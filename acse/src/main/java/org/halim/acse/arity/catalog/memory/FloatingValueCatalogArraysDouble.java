package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.FloatingValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * The chunked double catalog: one double a slot, 8 bytes an identity.
 * <p>
 * Growth, truncation and the chunk list itself live in {@link ChunkedCatalogBase} - everything
 * here is the four array hooks and the two accessors, which is all that actually depends on the
 * stored type.
 * */
public class FloatingValueCatalogArraysDouble extends ChunkedCatalogBase<double[]> implements FloatingValueCatalog {

/** The chunked double catalog: one double a slot, 8 bytes an identity. */
@Override
public double getEstimatedBytesPerIdentity() { return 8; }

public FloatingValueCatalogArraysDouble() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public FloatingValueCatalogArraysDouble(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected double[] allocate(int length) { return new double[length]; }
@Override
protected int lengthOf(double[] chunk) { return chunk.length; }
@Override
protected double[] copyOf(double[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(double[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (double) 0); }
/** Raw bits, so even a NaN's payload comes back as it went. */
@Override
protected void write(DataOutputStream dos, double[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeLong(Double.doubleToRawLongBits(chunk[i])); } }
@Override
protected void read(DataInputStream dis, double[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = Double.longBitsToDouble(dis.readLong()); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static FloatingValueCatalogArraysDouble loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FloatingValueCatalogArraysDouble catalog = new FloatingValueCatalogArraysDouble(readChunking(dis));
	catalog.readValues(dis);
	return catalog;
}


@Override
public double getData(long identity) {
	return data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)];
}

public boolean setData(long identity, double value) {
	boolean changed = false;
	if(identity > lastIndex) { ensureCapacity(identity); changed = true; }
	setDataRaw(identity, value);
	return changed;
}

/** Skips the growth check, so the caller carries the invariant that the slot already exists. */
public void setDataRaw(long identity, double value) {
	data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = value;
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setDoubleFor(long identity, double value) { setDataRaw(identity, value); }



/**
 * The range copied chunk by chunk rather than element by element. The chunk split is resolved once
 * per block instead of once per identity, and inside a block the copy is a run - so the cost of
 * chunking, which the per-element path pays on every read, is amortised over the whole run.
 * */
@Override
public void copyDoublesInto(long from, long to, double[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		double[] block = data.get(chunking.chunkOf(identity));
		int start = chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, block.length - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		System.arraycopy(block, start, dest, offset, run);
		identity += run;
		offset += run;
	}
}

}

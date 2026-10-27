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
 * The chunked float catalog: one float a slot, 4 bytes an identity.
 * <p>
 * Growth, truncation and the chunk list itself live in {@link ChunkedCatalogBase} - everything
 * here is the four array hooks and the two accessors, which is all that actually depends on the
 * stored type.
 * */
public class FloatingValueCatalogArraysFloat extends ChunkedCatalogBase<float[]> implements FloatingValueCatalog {

/** The chunked float catalog: one float a slot, 4 bytes an identity. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

public FloatingValueCatalogArraysFloat() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public FloatingValueCatalogArraysFloat(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected float[] allocate(int length) { return new float[length]; }
@Override
protected int lengthOf(float[] chunk) { return chunk.length; }
@Override
protected float[] copyOf(float[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(float[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (float) 0); }
/** Raw bits, so even a NaN's payload comes back as it went. */
@Override
protected void write(DataOutputStream dos, float[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeInt(Float.floatToRawIntBits(chunk[i])); } }
@Override
protected void read(DataInputStream dis, float[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = Float.intBitsToFloat(dis.readInt()); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static FloatingValueCatalogArraysFloat loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FloatingValueCatalogArraysFloat catalog = new FloatingValueCatalogArraysFloat(readChunking(dis));
	catalog.readValues(dis);
	return catalog;
}


@Override
public double getData(long identity) {
	return data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)];
}

/**
 * Writing narrows, and the narrowing rounds rather than throwing: unlike a truncated identity, a
 * rounded measurement is still the value it claims to be. A catalog of scores, weights or
 * coordinates is the case for this variant; a catalog of exact sums is not.
 * */
public boolean setData(long identity, double value) {
	boolean changed = false;
	if(identity > lastIndex) { ensureCapacity(identity); changed = true; }
	setDataRaw(identity, value);
	return changed;
}

/** Skips the growth check, so the caller carries the invariant that the slot already exists. */
public void setDataRaw(long identity, double value) {
	data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = (float) value;
}

/** The contract's write, rounding as {@link #setData} does, and leaving growth to the arity. */
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
		float[] block = data.get(chunking.chunkOf(identity));
		int start = chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, block.length - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		for(int i = 0; i < run; i++) { dest[offset + i] = block[start + i]; }
		identity += run;
		offset += run;
	}
}

}

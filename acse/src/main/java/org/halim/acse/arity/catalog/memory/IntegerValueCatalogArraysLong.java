package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.IntegerValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * The chunked long catalog: one long a slot, 8 bytes an identity.
 * <p>
 * Growth, truncation and the chunk list itself live in {@link ChunkedCatalogBase} - everything
 * here is the four array hooks and the two accessors, which is all that actually depends on the
 * stored type.
 * */
public class IntegerValueCatalogArraysLong extends ChunkedCatalogBase<long[]> implements IntegerValueCatalog {

/** The chunked long catalog: one long a slot, 8 bytes an identity. */
@Override
public double getEstimatedBytesPerIdentity() { return 8; }

public IntegerValueCatalogArraysLong() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public IntegerValueCatalogArraysLong(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected long[] allocate(int length) { return new long[length]; }
@Override
protected int lengthOf(long[] chunk) { return chunk.length; }
@Override
protected long[] copyOf(long[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(long[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (long) 0); }
@Override
protected void write(DataOutputStream dos, long[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeLong(chunk[i]); } }
@Override
protected void read(DataInputStream dis, long[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = dis.readLong(); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static IntegerValueCatalogArraysLong loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogArraysLong catalog = new IntegerValueCatalogArraysLong(readChunking(dis));
	catalog.readValues(dis);
	return catalog;
}


@Override
public long getData(long identity) {
	return data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)];
}

public boolean setData(long identity, long value) {
	boolean changed = false;
	if(identity > lastIndex) { ensureCapacity(identity); changed = true; }
	setDataRaw(identity, value);
	return changed;
}

/** Skips the growth check, so the caller carries the invariant that the slot already exists. */
public void setDataRaw(long identity, long value) {
	data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = value;
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setLongFor(long identity, long value) { setDataRaw(identity, value); }



/**
 * The range copied chunk by chunk rather than element by element. The chunk split is resolved once
 * per block instead of once per identity, and inside a block the copy is a run - so the cost of
 * chunking, which the per-element path pays on every read, is amortised over the whole run.
 * */
@Override
public void copyLongsInto(long from, long to, long[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		long[] block = data.get(chunking.chunkOf(identity));
		int start = chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, block.length - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		System.arraycopy(block, start, dest, offset, run);
		identity += run;
		offset += run;
	}
}

}

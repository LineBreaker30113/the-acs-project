package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.IntegerValueCatalog.IntSizedIntegerValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * The chunked int catalog: one int a slot, 4 bytes an identity.
 * <p>
 * Growth, truncation and the chunk list itself live in {@link ChunkedCatalogBase} - everything
 * here is the four array hooks and the two accessors, which is all that actually depends on the
 * stored type.
 * */
public class IntegerValueCatalogArraysInt extends ChunkedCatalogBase<int[]> implements IntSizedIntegerValueCatalog {

/** The chunked int catalog: one int a slot, 4 bytes an identity. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

public IntegerValueCatalogArraysInt() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public IntegerValueCatalogArraysInt(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected int[] allocate(int length) { return new int[length]; }
@Override
protected int lengthOf(int[] chunk) { return chunk.length; }
@Override
protected int[] copyOf(int[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(int[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (int) 0); }
@Override
protected void write(DataOutputStream dos, int[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeInt(chunk[i]); } }
@Override
protected void read(DataInputStream dis, int[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = dis.readInt(); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static IntegerValueCatalogArraysInt loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogArraysInt catalog = new IntegerValueCatalogArraysInt(readChunking(dis));
	catalog.readValues(dis);
	return catalog;
}


@Override
public int getIntData(long identity) {
	return data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)];
}

public boolean setData(long identity, int value) {
	boolean changed = false;
	if(identity > lastIndex) { ensureCapacity(identity); changed = true; }
	setDataRaw(identity, value);
	return changed;
}

/**
 * The same write from the wide side, so a caller holding long values does not have to narrow them
 * itself. Narrowing to a int wraps rather than saturating, and a wrapped value is
 * indistinguishable from a real one, so this refuses instead - which is the signal that the catalog
 * wanted was a wider one.
 * */
public boolean setData(long identity, long value) {
	return setData(identity, ACS_ArityComponentBase.exactInt(value));
}

/** Skips the growth check, so the caller carries the invariant that the slot already exists. */
public void setDataRaw(long identity, int value) {
	data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = value;
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setLongFor(long identity, long value) { setDataRaw(identity, ACS_ArityComponentBase.exactInt(value)); }



/**
 * The range copied chunk by chunk rather than element by element. The chunk split is resolved once
 * per block instead of once per identity, and inside a block the copy is a run - so the cost of
 * chunking, which the per-element path pays on every read, is amortised over the whole run.
 * */
@Override
public void copyIntsInto(long from, long to, int[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		int[] block = data.get(chunking.chunkOf(identity));
		int start = chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, block.length - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		System.arraycopy(block, start, dest, offset, run);
		identity += run;
		offset += run;
	}
}

}

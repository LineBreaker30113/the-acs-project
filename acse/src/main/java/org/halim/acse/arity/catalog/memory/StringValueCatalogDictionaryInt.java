package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/** The dictionary string catalog with four-byte codes: 4 bytes an identity, up to 2^31 - 1 distinct strings. */
public class StringValueCatalogDictionaryInt extends StringValueCatalogDictionaryBase<int[]> {

/** The codes only; the dictionary comes on top, once for each distinct string. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

public StringValueCatalogDictionaryInt() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public StringValueCatalogDictionaryInt(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected int[] allocate(int length) { return new int[length]; }
@Override
protected int lengthOf(int[] chunk) { return chunk.length; }
@Override
protected int[] copyOf(int[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(int[] chunk, int from, int to) { Arrays.fill(chunk, from, to, 0); }
@Override
protected void write(DataOutputStream dos, int[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeInt(chunk[i]); } }
@Override
protected void read(DataInputStream dis, int[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = dis.readInt(); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static StringValueCatalogDictionaryInt loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	StringValueCatalogDictionaryInt catalog = new StringValueCatalogDictionaryInt(readChunking(dis));
	catalog.readValues(dis);
	catalog.readDictionary(dis);
	return catalog;
}


@Override
protected int capacity() { return Integer.MAX_VALUE; }

@Override
public int getCode(long identity) { return data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)]; }

@Override
protected void store(long identity, int code) { data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = code; }

}

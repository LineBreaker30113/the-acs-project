package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/** The dictionary string catalog with two-byte codes: 2 bytes an identity, up to 65536 distinct strings. */
public class StringValueCatalogDictionaryShort extends StringValueCatalogDictionaryBase<short[]> {

/** The codes only; the dictionary comes on top, once for each distinct string. */
@Override
public double getEstimatedBytesPerIdentity() { return 2; }

public StringValueCatalogDictionaryShort() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public StringValueCatalogDictionaryShort(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected short[] allocate(int length) { return new short[length]; }
@Override
protected int lengthOf(short[] chunk) { return chunk.length; }
@Override
protected short[] copyOf(short[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(short[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (short) 0); }
@Override
protected void write(DataOutputStream dos, short[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeShort(chunk[i]); } }
@Override
protected void read(DataInputStream dis, short[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = dis.readShort(); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static StringValueCatalogDictionaryShort loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	StringValueCatalogDictionaryShort catalog = new StringValueCatalogDictionaryShort(readChunking(dis));
	catalog.readValues(dis);
	catalog.readDictionary(dis);
	return catalog;
}


@Override
protected int capacity() { return 1 << 16; }

@Override
public int getCode(long identity) { return Short.toUnsignedInt(data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)]); }

@Override
protected void store(long identity, int code) { data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = (short) code; }

}

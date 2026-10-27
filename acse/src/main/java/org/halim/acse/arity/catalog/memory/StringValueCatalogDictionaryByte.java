package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/** The dictionary string catalog with one-byte codes: 1 byte an identity, up to 256 distinct strings. */
public class StringValueCatalogDictionaryByte extends StringValueCatalogDictionaryBase<byte[]> {

/** The codes only; the dictionary comes on top, once for each distinct string. */
@Override
public double getEstimatedBytesPerIdentity() { return 1; }

public StringValueCatalogDictionaryByte() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public StringValueCatalogDictionaryByte(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected byte[] allocate(int length) { return new byte[length]; }
@Override
protected int lengthOf(byte[] chunk) { return chunk.length; }
@Override
protected byte[] copyOf(byte[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(byte[] chunk, int from, int to) { Arrays.fill(chunk, from, to, (byte) 0); }
@Override
protected void write(DataOutputStream dos, byte[] chunk, int length) throws IOException { dos.write(chunk, 0, length); }
@Override
protected void read(DataInputStream dis, byte[] chunk, int length) throws IOException { dis.readFully(chunk, 0, length); }

/** Rebuilds what {@link #saveTo} wrote. */
public static StringValueCatalogDictionaryByte loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	StringValueCatalogDictionaryByte catalog = new StringValueCatalogDictionaryByte(readChunking(dis));
	catalog.readValues(dis);
	catalog.readDictionary(dis);
	return catalog;
}


@Override
protected int capacity() { return 1 << 8; }

@Override
public int getCode(long identity) { return Byte.toUnsignedInt(data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)]); }

@Override
protected void store(long identity, int code) { data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = (byte) code; }

}

package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.LongChunkInt;
import org.halim.acs.arity.catalog.StringValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * The chunked string catalog, the plain one: a reference a slot, each string kept in memory as it is.
 * A slot never written holds null and reads as "", and "" is written as null, so the two are one value,
 * as a never-written number and a written zero are.
 * <p>
 * What it costs is the strings themselves on top of the references, an object each: a column of few
 * distinct values is cheaper as a dictionary, and long text belongs on disk. It is stored as each
 * string's UTF-16 units, through {@link ACS_PersistenceManager#writeString}, so any string comes back
 * exactly.
 * */
public class StringValueCatalogArrays extends ChunkedCatalogBase<String[]> implements StringValueCatalog {

/** The references only; the strings come on top. */
@Override
public double getEstimatedBytesPerIdentity() { return 8; }

public StringValueCatalogArrays() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(22));
}
public StringValueCatalogArrays(LongChunkInt chunking) {
	super(chunking);
}


@Override
protected String[] allocate(int length) { return new String[length]; }
@Override
protected int lengthOf(String[] chunk) { return chunk.length; }
@Override
protected String[] copyOf(String[] chunk, int length) { return Arrays.copyOf(chunk, length); }
@Override
protected void clear(String[] chunk, int from, int to) { Arrays.fill(chunk, from, to, null); }
@Override
protected void write(DataOutputStream dos, String[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { ACS_PersistenceManager.writeString(dos, chunk[i]); } }
@Override
protected void read(DataInputStream dis, String[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = ACS_PersistenceManager.readString(dis); } }

/** Rebuilds what {@link #saveTo} wrote. */
public static StringValueCatalogArrays loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	StringValueCatalogArrays catalog = new StringValueCatalogArrays(readChunking(dis));
	catalog.readValues(dis);
	return catalog;
}


@Override
public String getData(long identity) {
	String value = data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)];
	return value == null ? "" : value;
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setStringFor(long identity, String value) {
	data.get(chunking.chunkOf(identity))[chunking.offsetOf(identity)] = value.isEmpty() ? null : value;
}

}

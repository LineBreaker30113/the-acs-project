package org.halim.acse.arity.catalog.file;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.FloatingValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;


/**
 * The float catalog in a file: one float a slot, 4 bytes an identity, in the file and in memory only
 * where the system keeps its pages.
 * <p>
 * The file, its mappings and their growth live in {@link MappedRecordFile}, and the store's copy in
 * {@link FileCatalogBase}, a copy of the file byte for byte, so even a NaN's payload comes back as it went -
 * everything here is the accessors, which is all that depends on the stored type.
 * */
public class FloatingValueCatalogFileFloat extends FileCatalogBase implements FloatingValueCatalog {

/** One float a slot: 4 bytes an identity, of file. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

/** A new file, refused if it already holds data. */
public FloatingValueCatalogFileFloat(Path file) throws IOException {
	this(file, MappedRecordFile.DEFAULT_CHUNKING);
}
public FloatingValueCatalogFileFloat(Path file, LongChunkInt chunking) throws IOException {
	super(MappedRecordFile.create(file, chunking, MappedRecordFile.FLOAT));
}
FloatingValueCatalogFileFloat(MappedRecordFile storage) {
	super(storage);
}

/** Rebuilds what {@link #saveTo} wrote, over the file put back from the loaded folder's copy. */
public static FloatingValueCatalogFileFloat loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FloatingValueCatalogFileFloat catalog = new FloatingValueCatalogFileFloat(reopen(dis, MappedRecordFile.FLOAT));
	catalog.readValues(dis);
	return catalog;
}


@Override
public double getData(long identity) {
	return storage.chunkOf(identity).getFloat(storage.positionOf(identity));
}

/**
 * The contract's write, and it narrows: the narrowing rounds rather than throwing, as the in-memory float
 * catalog's does, since a rounded measurement is still the value it claims to be. Growth is the arity's.
 * */
@Override
public void setDoubleFor(long identity, double value) {
	storage.chunkOf(identity).putFloat(storage.positionOf(identity), (float) value);
}


/**
 * The range copied a mapping at a time: the chunk split is resolved once per mapping rather than once
 * per identity. A float widens on the way out, so inside a mapping the copy is a loop, not a run.
 * */
@Override
public void copyDoublesInto(long from, long to, double[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		MappedByteBuffer mapping = storage.chunkOf(identity);
		int start = storage.chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, storage.recordsIn(mapping) - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		for(int i = 0; i < run; i++) { dest[offset + i] = mapping.getFloat((start + i) * 4); }
		identity += run;
		offset += run;
	}
}

}

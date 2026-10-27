package org.halim.acse.arity.catalog.file;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.IntegerValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;


/**
 * The long catalog in a file: one long a slot, 8 bytes an identity, in the file and in memory only where
 * the system keeps its pages.
 * <p>
 * The file, its mappings and their growth live in {@link MappedRecordFile}, and the store's copy in
 * {@link FileCatalogBase} - everything here is the accessors, which is all that depends on the stored type.
 * */
public class IntegerValueCatalogFileLong extends FileCatalogBase implements IntegerValueCatalog {

/** One long a slot: 8 bytes an identity, of file. */
@Override
public double getEstimatedBytesPerIdentity() { return 8; }

/** A new file, refused if it already holds data. */
public IntegerValueCatalogFileLong(Path file) throws IOException {
	this(file, MappedRecordFile.DEFAULT_CHUNKING);
}
public IntegerValueCatalogFileLong(Path file, LongChunkInt chunking) throws IOException {
	super(MappedRecordFile.create(file, chunking, MappedRecordFile.LONG));
}
IntegerValueCatalogFileLong(MappedRecordFile storage) {
	super(storage);
}

/** Rebuilds what {@link #saveTo} wrote, over the file put back from the loaded folder's copy. */
public static IntegerValueCatalogFileLong loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogFileLong catalog = new IntegerValueCatalogFileLong(reopen(dis, MappedRecordFile.LONG));
	catalog.readValues(dis);
	return catalog;
}


@Override
public long getData(long identity) {
	return storage.chunkOf(identity).getLong(storage.positionOf(identity));
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setLongFor(long identity, long value) {
	storage.chunkOf(identity).putLong(storage.positionOf(identity), value);
}


/**
 * The range copied a mapping at a time: the chunk split is resolved once per mapping, and inside one the
 * copy is a run through a long view of it, so the per-element path's chunking is paid once over the run.
 * */
@Override
public void copyLongsInto(long from, long to, long[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		MappedByteBuffer mapping = storage.chunkOf(identity);
		int start = storage.chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, storage.recordsIn(mapping) - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		mapping.slice(start * 8, run * 8).order(mapping.order()).asLongBuffer().get(dest, offset, run);
		identity += run;
		offset += run;
	}
}

}

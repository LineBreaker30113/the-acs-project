package org.halim.acse.arity.catalog.file;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.IntegerValueCatalog.IntSizedIntegerValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;


/**
 * The int catalog in a file: one int a slot, 4 bytes an identity, in the file and in memory only where
 * the system keeps its pages.
 * <p>
 * The file, its mappings and their growth live in {@link MappedRecordFile}, and the store's copy in
 * {@link FileCatalogBase} - everything here is the accessors, which is all that depends on the stored type.
 * */
public class IntegerValueCatalogFileInt extends FileCatalogBase implements IntSizedIntegerValueCatalog {

/** One int a slot: 4 bytes an identity, of file. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

/** A new file, refused if it already holds data. */
public IntegerValueCatalogFileInt(Path file) throws IOException {
	this(file, MappedRecordFile.DEFAULT_CHUNKING);
}
public IntegerValueCatalogFileInt(Path file, LongChunkInt chunking) throws IOException {
	super(MappedRecordFile.create(file, chunking, MappedRecordFile.INT));
}
IntegerValueCatalogFileInt(MappedRecordFile storage) {
	super(storage);
}

/** Rebuilds what {@link #saveTo} wrote, over the file put back from the loaded folder's copy. */
public static IntegerValueCatalogFileInt loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IntegerValueCatalogFileInt catalog = new IntegerValueCatalogFileInt(reopen(dis, MappedRecordFile.INT));
	catalog.readValues(dis);
	return catalog;
}


@Override
public int getIntData(long identity) {
	return storage.chunkOf(identity).getInt(storage.positionOf(identity));
}

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setLongFor(long identity, long value) {
	storage.chunkOf(identity).putInt(storage.positionOf(identity), ACS_ArityComponentBase.exactInt(value));
}


/**
 * The range copied a mapping at a time: the chunk split is resolved once per mapping, and inside one the
 * copy is a run through an int view of it, so the per-element path's chunking is paid once over the run.
 * */
@Override
public void copyIntsInto(long from, long to, int[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		MappedByteBuffer mapping = storage.chunkOf(identity);
		int start = storage.chunking.offsetOf(identity);
		int run = (int) Math.min(to - identity, storage.recordsIn(mapping) - start);
		if(run <= 0) { throw new IndexOutOfBoundsException("identity " + identity + " is past this catalog"); }
		mapping.slice(start * 4, run * 4).order(mapping.order()).asIntBuffer().get(dest, offset, run);
		identity += run;
		offset += run;
	}
}

}

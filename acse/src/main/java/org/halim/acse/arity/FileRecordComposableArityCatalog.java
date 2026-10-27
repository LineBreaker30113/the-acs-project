package org.halim.acse.arity;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acs.arity.catalog.LongChunkInt;
import org.halim.acse.arity.catalog.file.FileRecordCatalog;
import org.halim.acse.arity.catalog.file.MappedRecordFile;
import org.halim.acs.arity.sets.IdentitiesSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A composable arity that comes with a file: it is made with a {@link FileRecordCatalog}, and that
 * record's fields are its first rows, field {@code i} at slot {@code i}, there from the start rather than
 * added through the journal. Every other row is added after them as on any composable arity, so the
 * record file is the arity's own storage without being its only one.
 * <p>
 * The record's rows are its {@link org.halim.acse.arity.catalog.file.FileRecordCatalogRow}
 * adapters, so they read, write, grow and journal as any row does, and they can be removed and added back
 * like any row; the record stays the arity's either way, at {@link #record}. What the class adds is the
 * binding: the arity holds the file it was made with, and its store and load carry the record whether or
 * not a row of it is still in a slot.
 * <p>
 * Nothing here is multi thread safe.
 * */
public class FileRecordComposableArityCatalog extends ACSE_ComposableArityCatalog {

/** The record file this arity was made with. */
public final FileRecordCatalog record;

/** A new record file of a field per type - {@link MappedRecordFile#LONG}, {@link MappedRecordFile#FLOAT}, ... - refused if it already holds data. */
public FileRecordComposableArityCatalog(IdentitiesSet occupied, ACS_Journal journal, Path file, byte... types) throws IOException {
	this(occupied, journal, file, MappedRecordFile.DEFAULT_CHUNKING, types);
}

public FileRecordComposableArityCatalog(IdentitiesSet occupied, ACS_Journal journal, Path file, LongChunkInt chunking, byte... types) throws IOException {
	this(occupied, journal, new FileRecordCatalog(file, chunking, types));
	for(int field = 0; field < record.rowCount(); field++) { putCatalogRow(field, record.row(field)); }
}

/** Over a record already made; its rows are not put in, which a load does from the stream. */
FileRecordComposableArityCatalog(IdentitiesSet occupied, ACS_Journal journal, FileRecordCatalog record) {
	super(occupied, journal);
	this.record = record;
}


/** The record's layout, which copies the file into the store's folder, and its reach, then what a composable arity writes. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	record.storage.writeLayout(dos);
	dos.writeLong(record.storage.lastIndex);
	return super.saveTo(dos);
}

/** Rebuilds what {@link #saveTo} wrote: the record from the loaded folder's copy, which its rows then share, then the composable arity's parts. */
public static FileRecordComposableArityCatalog loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FileRecordCatalog record = new FileRecordCatalog(MappedRecordFile.openLayout(dis));
	record.storage.reach(dis.readLong());
	long lastIndex = dis.readLong();
	FileRecordComposableArityCatalog arity = new FileRecordComposableArityCatalog(ACS_PersistenceManager.readPart(dis, IdentitiesSet.class, core, journal), journal, record);
	arity.lastIndex = lastIndex;
	for(int catalog = 0, count = dis.readInt(); catalog < count; catalog++) {
		arity.catalogRows.add(ACS_PersistenceManager.readPart(dis, ACS_ArityCatalogRowBase.class, core, journal));
	}
	return arity;
}


}

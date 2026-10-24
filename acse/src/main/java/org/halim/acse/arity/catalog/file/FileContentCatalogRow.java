package org.halim.acse.arity.catalog.file;

import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;


/**
 * A file per identity, its value the file's whole content: the bytes of the file another row names for the
 * identity. The naming is that row's - {@link org.halim.acse.arity.catalog.calculated.FilePathCatalogRow}
 * spreading identities over folders by their digits, {@link org.halim.acse.arity.catalog.calculated.HashedFilePathCatalogRow}
 * by a keyed permutation, or any row whose object or string is a path - so finding a file stays deterministic and
 * this row only reads what is there. A value can be any size, and only the one read is in memory.
 * <p>
 * A missing file reads as null, the zero of an object row. Nothing here writes a file during a request:
 * the contract writes only numbers, and a file changed outside the journal could not be undone, so the
 * files are the client's to write.
 * <p>
 * A store writes a full copy - the path row, the reach, then each file up to it or its absence - and a
 * load writes each file back and deletes the ones the copy says were absent, so the files are as the
 * stored generation saw them. That is the snapshot the copy buys, and the cost is every file read at every
 * store. Truncating deletes the files above the new reach, the way a stored row zeroes them.
 * */
public class FileContentCatalogRow implements ACS_ArityCatalogRowBase {

/** Names each identity's file. */
public final ACS_ArityCatalogRowBase paths;
/** The highest identity the arity has reached, so a store knows which files are the row's. */
public long lastIndex = -1;

public FileContentCatalogRow(ACS_ArityCatalogRowBase paths) {
	if(!namesPaths(paths)) { throw new IllegalArgumentException(paths.getClass().getSimpleName() + " names no paths"); }
	this.paths = paths;
}

/**
 * Built by name, as the core builds a row a request names, to sit at {@code row} of {@code arity}: its paths
 * are the nearest row before it that holds objects or strings, so which row names the files is found here,
 * not given by the request.
 * */
public FileContentCatalogRow(ACS_Core core, int arity, int row) { this(nearestPaths(core.getArity(arity), arity, row)); }

static ACS_ArityCatalogRowBase nearestPaths(ACS_ArityComponentBase target, int arity, int row) {
	for(int at = row - 1; at >= 0; at--) {
		ACS_ArityCatalogRowBase candidate = target.getCatalogRow(at);
		if(candidate != null && namesPaths(candidate)) { return candidate; }
	}
	throw new IllegalArgumentException("arity " + arity + " has no row of objects or strings before row " + row + " to name its files");
}

/** A row of objects, or of strings, which a string row answers as its objects too. */
static boolean namesPaths(ACS_ArityCatalogRowBase row) { return row.getPreferredType() == PREFERRED_OBJECT || row.getPreferredType() == PREFERRED_STRING; }


public Path pathOf(long identity) { return Path.of(paths.getObjectFor(identity).toString()); }

/** The file's bytes, or null for no file. */
@Override
public Object getObjectFor(long identity) { return bytesOf(pathOf(identity)); }

/** The file's bytes as UTF-8 text, or null for no file. */
public String getTextFor(long identity) {
	byte[] bytes = bytesOf(pathOf(identity));
	return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
}

static byte[] bytesOf(Path file) {
	try { return Files.readAllBytes(file); }
	catch(NoSuchFileException e) { return null; }
	catch(IOException e) { throw new UncheckedIOException(e); }
}


/** Nothing is held in memory; the files are on disk. */
@Override
public double getEstimatedBytesPerIdentity() { return 0; }

@Override
public int getPreferredType() { return PREFERRED_OBJECT; }

/** Records the reach; nothing grows, so never true. */
@Override
public boolean ensureCapacity(long identity) {
	lastIndex = Math.max(lastIndex, identity);
	return false;
}

/** Deletes the files of every identity above the new reach. */
@Override
public void truncate(long capacity) {
	try { for(long identity = Math.max(capacity + 1, 0); identity <= lastIndex; identity++) { Files.deleteIfExists(pathOf(identity)); } }
	catch(IOException e) { throw new UncheckedIOException(e); }
	lastIndex = Math.min(lastIndex, Math.max(capacity, -1));
}


/** The path row by class and data, the reach, then each file up to it as its length and bytes, or -1 for none. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	if(!ACS_PersistenceManager.writeClass(dos, paths)) {
		throw new IOException(paths.getClass().getName() + " declares no public static loadFrom, and a file content row cannot be stored without its paths");
	}
	if(!paths.saveTo(dos)) { return false; }
	dos.writeLong(lastIndex);
	for(long identity = 0; identity <= lastIndex; identity++) {
		byte[] bytes = bytesOf(pathOf(identity));
		if(bytes == null) {
			dos.writeInt(-1);
			continue;
		}
		dos.writeInt(bytes.length);
		dos.write(bytes);
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, writing each file back and deleting each one the copy has as absent. */
public static FileContentCatalogRow loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	ACS_ArityCatalogRowBase paths = ACS_PersistenceManager.readPart(dis, ACS_ArityCatalogRowBase.class, core, journal);
	if(paths == null) { throw new IOException("a file content row was stored without its paths"); }
	FileContentCatalogRow row = new FileContentCatalogRow(paths);
	row.lastIndex = dis.readLong();
	for(long identity = 0; identity <= row.lastIndex; identity++) {
		int length = dis.readInt();
		Path file = row.pathOf(identity);
		if(length == -1) {
			Files.deleteIfExists(file);
			continue;
		}
		if(length < 0) { throw new IOException("identity " + identity + " has a file of length " + length); }
		byte[] bytes = new byte[length];
		dis.readFully(bytes);
		if(file.getParent() != null) { Files.createDirectories(file.getParent()); }
		Files.write(file, bytes);
	}
	return row;
}


}

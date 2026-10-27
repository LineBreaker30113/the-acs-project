package org.halim.acse.arity.catalog.calculated;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


/**
 * A file path per identity, calculated from its eight hex digits {@code abcdefgh} as
 * {@code <path>ceg/abdfh<extension>}, so nothing is stored and nothing grows. Every other digit of the
 * last six names the folder, so consecutive identities spread over 4096 folders of at most 16^5 files
 * each. The path and the extension are fixed for the row's life: changing either moves every file,
 * which is replacing the row, not editing it.
 * */
public class FilePathCatalogRow implements ACS_ArityCatalogRowBase {

public final String path;
public final String extension;

public FilePathCatalogRow(String path, String extension) {
	this.path = path;
	this.extension = extension;
}

/** The path and the extension, which are all this row is. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	ACS_PersistenceManager.writeString(dos, path);
	ACS_PersistenceManager.writeString(dos, extension);
	return true;
}

public static FilePathCatalogRow loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	String path = ACS_PersistenceManager.readString(dis);
	return new FilePathCatalogRow(path, ACS_PersistenceManager.readString(dis));
}

@Override
public Object getObjectFor(long identity) {
	if(identity < 0 || identity > 0xFFFFFFFFL) { throw new ArithmeticException(identity + " does not fit 32 bits"); }
	String hex = String.format("%08x", identity);
	return path + hex.charAt(2) + hex.charAt(4) + hex.charAt(6) + '/'
		+ hex.charAt(0) + hex.charAt(1) + hex.charAt(3) + hex.charAt(5) + hex.charAt(7) + extension;
}

@Override
public double getEstimatedBytesPerIdentity() { return 0; }

@Override
public int getPreferredType() { return PREFERRED_OBJECT; }

@Override
public boolean ensureCapacity(long identity) { return false; }

@Override
public void truncate(long capacity) { }

}

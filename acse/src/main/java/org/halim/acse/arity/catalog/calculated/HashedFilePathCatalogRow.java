package org.halim.acse.arity.catalog.calculated;

import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


/**
 * A file path per identity, calculated as {@code <path>abc/defg<extension>} where {@code abcdefg} is the
 * identity through a keyed permutation of the 32-bit range, written as seven base-36 characters. The
 * folder is three characters, not two, because 2^32 / 36^6 is 1.97: the first character is only ever 0
 * or 1, so three give 2558 folders where two would give 72.
 * <p>
 * A permutation is a bijection, so two identities never share a name, and the same key always gives
 * the same name. Without the key, a name says nothing about its identity or its neighbours'. Seven
 * characters, because 36^6 is below 2^32 and 36^7 is above it; lowercase base 36, because a
 * case-insensitive file system would merge names that differ only in case.
 * <p>
 * The permutation is an eight-round Feistel network over the two 16-bit halves, whose round function
 * mixes the key, the round and the half. That hides the order from anyone without the key; it is not
 * encryption against someone collecting many identity-name pairs, which would take a format-preserving
 * cipher such as NIST FF1. The key belongs to the row as much as the path does: a different key names
 * every file differently.
 * */
public class HashedFilePathCatalogRow implements ACS_ArityCatalogRowBase {

static final int ROUNDS = 8;
static final int NAME_LENGTH = 7;

public final String path;
public final String extension;
final long key;

public HashedFilePathCatalogRow(String path, String extension, long key) {
	this.path = path;
	this.extension = extension;
	this.key = key;
}

/** The path, the extension and the key, which are all this row is. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	ACS_PersistenceManager.writeString(dos, path);
	ACS_PersistenceManager.writeString(dos, extension);
	dos.writeLong(key);
	return true;
}

public static HashedFilePathCatalogRow loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	String path = ACS_PersistenceManager.readString(dis);
	String extension = ACS_PersistenceManager.readString(dis);
	return new HashedFilePathCatalogRow(path, extension, dis.readLong());
}

@Override
public Object getObjectFor(long identity) {
	String name = nameOf(identity);
	return path + name.substring(0, 3) + '/' + name.substring(3) + extension;
}

public String nameOf(long identity) {
	if(identity < 0 || identity > 0xFFFFFFFFL) { throw new ArithmeticException(identity + " does not fit 32 bits"); }
	String name = Long.toString(Integer.toUnsignedLong(permute((int) identity)), 36);
	return "0".repeat(NAME_LENGTH - name.length()) + name;
}

int permute(int value) {
	int left = value >>> 16;
	int right = value & 0xFFFF;
	for(int round = 0; round < ROUNDS; round++) {
		int next = left ^ (mix(right, round) & 0xFFFF);
		left = right;
		right = next;
	}
	return (left << 16) | right;
}

/** SplitMix64's finalizer over the key, the round and the half. */
int mix(int half, int round) {
	long x = key + (round + 1) * 0x9E3779B97F4A7C15L + half;
	x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
	x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
	return (int) (x ^ (x >>> 31));
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

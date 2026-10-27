
package org.halim.acs.arity.sets;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;


/**
 * For now, not multi thread safe.
 * Doesn't know how the elements are represented.
 * <p>
 * Two families of operation live here. {@link #identityAt} and {@link #identityIteritor} are the <em>list</em>
 * view - positions, in ascending order - which is what a navigation UI pages through. The rest are
 * the <em>set</em> algebra, which is what closure over a formal context is built from: computing a
 * concept is nothing but repeated intersection, so an int set without these cannot participate.
 * <p>
 * Everything below {@link #contains} is a default written once against {@code contains} and
 * {@link #identityAt}, so a new representation only has to supply those two to gain the whole algebra.
 * A representation that can do better than the generic path - a bitmap intersecting word by word,
 * a complement form reasoning over its holes - should override.
 * */
public interface IdentitiesSet {

long identityAt(long index);
boolean register(long identity);
boolean unsign(long identity);
/** The lowest non-negative identity the set does not hold. */
default long lowestFree() {
	long identity = 0;
	while(contains(identity)) { identity++; }
	return identity;
}

/**
 * How many identities the set holds. Long, because a negative form stores a dense range in its
 * bounds - a set of 2^40 identities is three fields wide, and its size is not a number an int can
 * carry. The int-shaped views below are the ones that have a ceiling; this does not.
 * */
long size();

/** Membership. Expected to be logarithmic or better; the whole algebra below is built on it. */
boolean contains(long i);

LongListerIterator identityIteritor();


interface LongListerIterator {
	boolean hasNext();
	boolean hasPrevious();
	long next();
	long previous();
	void remove();
}


default boolean isEmpty() { return size() == 0; }

/**
 * How many elements the two sets share, without materialising them.
 * <p>
 * Walks the smaller side and probes the larger, which is the only ordering that stays cheap when
 * the two differ in size - and in a classification index they nearly always do, since a query
 * result is small and the class it is tested against is large.
 * */
default long intersectionCount(IdentitiesSet other) {
	IdentitiesSet small = size() <= other.size() ? this : other;
	IdentitiesSet large = small == this ? other : this;
	long shared = 0;
	for(long i = 0, n = small.size(); i < n; i++) { if(large.contains(small.identityAt(i))) { shared++; } }
	return shared;
}

/** The shared elements, ascending. Both sides enumerate ascending, so the result already is. */
default LongArrayList intersect(IdentitiesSet other) {
	IdentitiesSet small = size() <= other.size() ? this : other;
	IdentitiesSet large = small == this ? other : this;
	LongArrayList shared = new LongArrayList();
	for(long i = 0, n = small.size(); i < n; i++) {
		long value = small.identityAt(i);
		if(large.contains(value)) { shared.add(value); }
	}
	return shared;
}

/** What this set holds and {@code other} does not, ascending. */
default LongArrayList difference(IdentitiesSet other) {
	LongArrayList only = new LongArrayList();
	for(long i = 0, n = size(); i < n; i++) {
		long value = identityAt(i);
		if(!other.contains(value)) { only.add(value); }
	}
	return only;
}

/** Drops everything {@code other} does not hold. True when this set changed. */
default boolean retainAll(IdentitiesSet other) {
	// Collect first: removing during the walk would shift the positions underneath it.
	LongArrayList doomed = difference(other);
	for(int i = 0; i < doomed.size(); i++) { unsign(doomed.getLong(i)); }
	return !doomed.isEmpty();
}

/** Drops everything {@code other} holds. True when this set changed. */
default boolean removeAll(IdentitiesSet other) {
	boolean changed = false;
	// Drive from whichever side is smaller; removing by value needs no position bookkeeping.
	if(other.size() <= size()) {
		for(long i = 0, n = other.size(); i < n; i++) { changed |= unsign(other.identityAt(i)); }
		return changed;
	}
	LongArrayList doomed = intersect(other);
	for(int i = 0; i < doomed.size(); i++) { changed |= unsign(doomed.getLong(i)); }
	return changed;
}

/** Adds everything {@code other} holds. True when this set changed. */
default boolean addAll(IdentitiesSet other) {
	boolean changed = false;
	for(long i = 0, n = other.size(); i < n; i++) { changed |= register(other.identityAt(i)); }
	return changed;
}

/** Whether every element of {@code other} is in this set. */
default boolean containsAll(IdentitiesSet other) {
	if(other.size() > size()) { return false; }
	for(long i = 0, n = other.size(); i < n; i++) { if(!contains(other.identityAt(i))) { return false; } }
	return true;
}


/**
 * The same identities as a Roaring bitmap, which is the form the compressed algebra works on.
 * <p>
 * The generic path here materialises: it walks this set and adds every identity, so it costs one
 * pass and one bitmap. That is the price of crossing representations, and it is why a representation
 * overriding the algebra above tests for a shared representation first rather than converting.
 * <p>
 * A set that already <em>is</em> a bitmap returns its own storage rather than a copy - the
 * conversion is free precisely because there is nothing to convert. So the result is not guaranteed
 * detached, and writing to it may write through to the set. Treat it as read-only unless the
 * implementation says otherwise; take a copy when a private one is needed.
 * */
default Roaring64Bitmap getAsRoaringBitset() {
	Roaring64Bitmap bitmap = new Roaring64Bitmap();
	for(long i = 0, n = size(); i < n; i++) { bitmap.addLong(identityAt(i)); }
	return bitmap;
}


/**
 * Writes the identities so that the class's {@code public static loadFrom(ACS_Core, ACS_Journal,
 * DataInputStream)} rebuilds the same set. The default writes the count and then every identity, which
 * is the right size for a sparse set, and {@link #loadInto} reads it back; a representation with a
 * smaller form - a range and its holes, a compressed bitmap - overrides this and reads its own.
 * */
default boolean saveTo(DataOutputStream dos) throws IOException {
	long size = size();
	dos.writeLong(size);
	for(long i = 0; i < size; i++) { dos.writeLong(identityAt(i)); }
	return true;
}

/** Registers into an empty set what the default {@link #saveTo} wrote, and gives the set back: the static loadFrom of a set that keeps the default. */
static <S extends IdentitiesSet> S loadInto(S set, DataInputStream dis) throws IOException {
	for(long i = 0, size = dis.readLong(); i < size; i++) { set.register(dis.readLong()); }
	return set;
}


List<Integer> getIntList();
IntArrayList toList();


}

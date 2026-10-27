package org.halim.acse.arity.sets;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.sets.IdentitiesSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.roaringbitmap.longlong.LongConsumer;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An {@link IdentitiesSet} that is a compressed bitmap. Extends {@link Roaring64Bitmap} and
 * implements the interface directly, so everything Roaring already offers stays reachable and the
 * set view costs no second object - the interface's names were chosen to leave room for exactly
 * that, and {@code contains} and {@code isEmpty} are inherited from the bitmap unchanged.
 * <p>
 * Roaring's 64-bit form is the base, not the 32-bit {@code RoaringBitmap}, because the interface is
 * long-keyed. Sitting on the 32-bit bitmap would put a range check on every identity and give back
 * the reach the rest of the package was widened to gain.
 * <p>
 * Where it wins: the set algebra. The interface defaults walk the smaller side and probe the larger,
 * which is the right shape when nothing better is known - but when both sides are bitmaps,
 * {@code and}, {@code or} and {@code andNot} work on whole compressed containers and never
 * materialise the elements at all. Every algebra method below takes that path when the other side is
 * also one of these, and falls back to the interface default when it is not, so mixing
 * representations stays correct and only mixing costs anything.
 * <p>
 * Where it loses: {@link #identityAt} and the cursor. A bitmap has no positions, so an index is
 * resolved through {@code select}, a search rather than an array read - the listers answer that in
 * one step. A forward-only walk should take {@link #getLongIterator()} directly and skip the cursor;
 * the cursor exists because the interface asks for one that can go backwards, which Roaring's own
 * iterators cannot.
 * <p>
 * Identities are non-negative here as everywhere else in the package, and a negative one is refused
 * rather than stored - which also keeps this class out of the question of how the bitmap underneath
 * orders a value with its top bit set.
 * <p>
 * Not multi thread safe.
 * */
public class IdentitiesSetRoaring extends Roaring64Bitmap implements IdentitiesSet {


public IdentitiesSetRoaring() {
	super();
}

/** Prefilled with [firstIndex, lastIndex], both ends inclusive, to mirror the listers. */
public IdentitiesSetRoaring(long firstIndex, long lastIndex) {
	super();
	if(firstIndex > lastIndex) { return; }
	checkIdentity(firstIndex);
	checkIdentity(lastIndex);
	addRange(firstIndex, lastIndex + 1);
}
public IdentitiesSetRoaring(long lastIndex) {
	this(0, lastIndex);
}

public IdentitiesSetRoaring getCopy() {
	IdentitiesSetRoaring result = new IdentitiesSetRoaring();
	result.or(this);
	return result;
}


/** Roaring's portable form, the one its format specification fixes across languages, so the file is the bitmap. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	serializePortable(dos);
	return true;
}

public static IdentitiesSetRoaring loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	IdentitiesSetRoaring set = new IdentitiesSetRoaring();
	set.deserializePortable(dis);
	return set;
}


/** The engine's identities start at 0; a negative one is a bug rather than a far-away element. */
static void checkIdentity(long identity) {
	if(identity < 0) { throw new IllegalArgumentException(identity + " is not a valid identity"); }
}


@Override
public long identityAt(long index) {
	long size = getLongCardinality();
	if(index < 0 || index >= size) { throw new IndexOutOfBoundsException("index " + index + ", size " + size); }
	return select(index);
}

@Override
public boolean register(long identity) {
	checkIdentity(identity);
	// addLong reports nothing, and cardinality is not free on a bitmap either, so membership is
	// asked first - one probe to decide, one to write.
	if(contains(identity)) { return false; }
	addLong(identity);
	return true;
}

@Override
public boolean unsign(long identity) {
	// No range check: a negative value was never a member.
	if(!contains(identity)) { return false; }
	removeLong(identity);
	return true;
}

@Override
public long size() {
	return getLongCardinality();
}

@Override
public LongListerIterator identityIteritor() {
	return new Cursor();
}

/** Already a bitmap, so this is the set itself rather than a copy of it. */
@Override
public Roaring64Bitmap getAsRoaringBitset() {
	return this;
}


/**
 * Walks the iteration order by position, since a bitmap has none of its own and the interface asks
 * for a cursor that can also go backwards.
 * <p>
 * Every step costs a {@code select}. That is the price of bidirectionality here; a forward-only walk
 * should take {@link #getLongIterator()} instead, which streams containers and never searches.
 * */
private final class Cursor implements LongListerIterator {
	
	long cursor;
	long lastReturned = -1;
	
	@Override
	public boolean hasNext() { return cursor < getLongCardinality(); }
	
	@Override
	public boolean hasPrevious() { return cursor > 0; }
	
	@Override
	public long next() {
		if(!hasNext()) { throw new NoSuchElementException(); }
		lastReturned = cursor;
		return select(cursor++);
	}
	
	@Override
	public long previous() {
		if(!hasPrevious()) { throw new NoSuchElementException(); }
		lastReturned = --cursor;
		return select(lastReturned);
	}
	
	@Override
	public void remove() {
		if(lastReturned < 0) { throw new IllegalStateException("no element to remove"); }
		removeLong(select(lastReturned));
		// Everything past the removed element slid down one position. Coming from next() the cursor
		// sat after it and has to follow; coming from previous() it sat on it and already points
		// at the right successor.
		if(lastReturned < cursor) { cursor--; }
		lastReturned = -1;
	}
}


// --- The algebra, on whole containers when both sides are bitmaps. ---

@Override
public long intersectionCount(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) { return andCardinality(this, that); }
	return IdentitiesSet.super.intersectionCount(other);
}

@Override
public LongArrayList intersect(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) { return elementsOf(and(this, that)); }
	return IdentitiesSet.super.intersect(other);
}

@Override
public LongArrayList difference(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) { return elementsOf(andNot(this, that)); }
	return IdentitiesSet.super.difference(other);
}

@Override
public boolean retainAll(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) {
		long before = getLongCardinality();
		and(that);
		return getLongCardinality() != before;
	}
	return IdentitiesSet.super.retainAll(other);
}

@Override
public boolean removeAll(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) {
		long before = getLongCardinality();
		andNot(that);
		return getLongCardinality() != before;
	}
	return IdentitiesSet.super.removeAll(other);
}

@Override
public boolean addAll(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) {
		long before = getLongCardinality();
		or(that);
		return getLongCardinality() != before;
	}
	return IdentitiesSet.super.addAll(other);
}

@Override
public boolean containsAll(IdentitiesSet other) {
	if(other instanceof IdentitiesSetRoaring that) {
		return andCardinality(this, that) == that.getLongCardinality();
	}
	return IdentitiesSet.super.containsAll(other);
}


/** Ascending, which is the order Roaring already stores and iterates in. */
static LongArrayList elementsOf(Roaring64Bitmap bitmap) {
	LongArrayList elements = new LongArrayList(Math.toIntExact(bitmap.getLongCardinality()));
	bitmap.forEach((LongConsumer) elements::add);
	return elements;
}

/** Snapshots the set in ascending order, detached from the bitmap. Narrows, so it can throw. */
@Override
public List<Integer> getIntList() {
	List<Integer> elements = new ArrayList<>(Math.toIntExact(size()));
	forEach((LongConsumer) value -> elements.add(Math.toIntExact(value)));
	return Collections.unmodifiableList(elements);
}

/** Snapshots the set in ascending order, detached from the bitmap. Narrows, so it can throw. */
@Override
public IntArrayList toList() {
	IntArrayList elements = new IntArrayList(Math.toIntExact(size()));
	forEach((LongConsumer) value -> elements.add(Math.toIntExact(value)));
	return elements;
}

/** The lossless snapshot. The two int forms above narrow, and throw on an element too big. */
public LongArrayList toLongArrayList() {
	return elementsOf(this);
}


}

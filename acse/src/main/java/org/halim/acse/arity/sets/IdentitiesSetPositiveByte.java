package org.halim.acse.arity.sets;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.sets.IdentitiesSet;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A set of identities stored positively: {@code inclusionList} holds the identities themselves,
 * sorted ascending with no duplicates, each narrowed to 8 bits. The mirror of
 * {@link IdentitiesSetNegativeByte}, which lists what the set leaves out instead - same
 * {@link IdentitiesSet} contract over either, so the two are meant to be swapped as a set's density
 * changes.
 * <p>
 * Memory is proportional to the number of identities, not to the span they cover, so this is the
 * form to reach for when a set is sparse or simply small: identities scattered across the whole
 * range cost the same as identities packed together, and there is no gap to pay for. A dense set is
 * where it loses - a full range of n costs n slots here against one pair of bounds for the negative
 * form.
 * <p>
 * Identities are stored <em>unsigned</em>, as everywhere else in the package - they start at 0 and
 * never go negative, so the sign bit would cost a bit and half the range with it. This variant
 * covers [0, 255], which bounds the identity <em>values</em> rather than how many of them there are;
 * a set of forty identities all under 256 fits here, a set of two identities at a million does
 * not.
 * <p>
 * The small case is the one this variant is really for, and {@link #LINEAR_SCAN_LIMIT} is where that
 * shows. Below it membership is a straight walk rather than a binary search: a handful of bytes is
 * one or two cache lines, and a predictable walk over them beats a branchy search that jumps around
 * inside the same line. Above it the walk loses and the search takes over. The crossover is a
 * judgement rather than a measurement on your hardware - move it if profiling says so.
 * <p>
 * One invariant holds at all times, and everything below leans on it: {@code inclusionList} is
 * sorted ascending with no duplicates. An empty set is an empty list.
 * <p>
 * Not multi thread safe. The storage is the set: this class implements {@link IdentitiesSet} itself
 * rather than handing out a separate view, so there is one object per set and nothing to unwrap.
 * */
public class IdentitiesSetPositiveByte implements IdentitiesSet {


/** The largest identity this width can carry. Past it, promote to a wider variant. */
public static final long MAX_VALUE = 0xFFL;

/**
 * Up to this many identities, membership walks instead of searching. Small enough that the whole
 * list is a cache line or two, which is the condition that makes the walk win.
 * */
public static final int LINEAR_SCAN_LIMIT = 32;

ByteArrayList inclusionList;

/** The smallest identity, or 0 for an empty set - which then reads as the empty range [0, 0). */
public long getBegin() { return isEmpty() ? 0 : elementAt(0); }

/** Exclusive like the intended java behavior. */
public long getEnd() { return isEmpty() ? 0 : elementAt(count() - 1) + 1; }

public IdentitiesSetPositiveByte getCopy() {
	IdentitiesSetPositiveByte result = new IdentitiesSetPositiveByte();
	result.inclusionList.addAll(this.inclusionList);
	return result;
}

/** The empty set, which is where a small one naturally starts. */
public IdentitiesSetPositiveByte() {
	inclusionList = new ByteArrayList();
}

/** Prefilled with [firstIndex, lastIndex], both ends inclusive, to mirror the negative form. */
public IdentitiesSetPositiveByte(long firstIndex, long lastIndex) {
	long length = lastIndex - firstIndex + 1;
	inclusionList = new ByteArrayList((int) Math.max(length, 0));
	if(length <= 0) { return; }
	checkFits(firstIndex);
	checkFits(lastIndex);
	for(long value = firstIndex; value <= lastIndex; value++) { inclusionList.add(narrow(value)); }
}
public IdentitiesSetPositiveByte(long lastIndex) {
	this(0, lastIndex);
}

/** Rebuilds what the default {@link IdentitiesSet#saveTo} wrote. */
public static IdentitiesSetPositiveByte loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	return IdentitiesSet.loadInto(new IdentitiesSetPositiveByte(), dis);
}


@Override
public long identityAt(long index) {
	long size = count();
	if(index < 0 || index >= size) { throw new IndexOutOfBoundsException("index " + index + ", size " + size); }
	return elementAt((int) index);
}

@Override
public boolean register(long identity) {
	// Nothing here cares where the identity lands relative to the rest, which is the whole point of
	// the positive form: no range to widen, no gap to pay for. Only the ceiling matters.
	checkFits(identity);
	int slot = inclusionSlot(identity);
	if(slot >= 0) { return false; }
	inclusionList.add(-slot - 1, narrow(identity));
	return true;
}

@Override
public boolean unsign(long identity) {
	// No range check: an identity this width cannot hold was never a member.
	int slot = inclusionSlot(identity);
	if(slot < 0) { return false; }
	inclusionList.removeByte(slot);
	return true;
}

/** A member exactly when the sorted list holds it - a walk when small, a search when not. */
@Override
public boolean contains(long i) {
	return inclusionSlot(i) >= 0;
}

@Override
public long size() {
	return inclusionList.size();
}

@Override
public LongListerIterator identityIteritor() {
	return new Cursor();
}


/**
 * Walks the iteration order. The list is already in that order, so the cursor is an index into it
 * and nothing has to be translated beyond the widening.
 * */
private final class Cursor implements LongListerIterator {

	int cursor;
	int lastReturned = -1;

	@Override
	public boolean hasNext() { return cursor < count(); }

	@Override
	public boolean hasPrevious() { return cursor > 0; }

	@Override
	public long next() {
		if(!hasNext()) { throw new NoSuchElementException(); }
		lastReturned = cursor;
		return elementAt(cursor++);
	}

	@Override
	public long previous() {
		if(!hasPrevious()) { throw new NoSuchElementException(); }
		lastReturned = --cursor;
		return elementAt(lastReturned);
	}

	@Override
	public void remove() {
		if(lastReturned < 0) { throw new IllegalStateException("no element to remove"); }
		inclusionList.removeByte(lastReturned);
		// Everything past the removed identity slid down one index. Coming from next() the cursor
		// sat after it and has to follow; coming from previous() it sat on it and already points
		// at the right successor.
		if(lastReturned < cursor) { cursor--; }
		lastReturned = -1;
	}
}


/** The count as an int. A positive set stores one slot per identity, so this cannot overflow. */
int count() {
	return inclusionList.size();
}

/** The i-th identity widened, with i already known to be in range. The list is the order. */
long elementAt(int i) {
	return inclusionList.getByte(i) & 0xFFL;
}

/** Narrows for storage. Only ever reached behind {@link #checkFits}, so nothing is lost here. */
byte narrow(long value) {
	return (byte) value;
}

/** The ceiling, enforced. A wrapped identity would be indistinguishable from a real one. */
void checkFits(long value) {
	if(value < 0 || value > MAX_VALUE) {
		throw new IllegalArgumentException(value + " does not fit in 8 unsigned bits, max " + MAX_VALUE);
	}
}

/**
 * Position of {@code value} in {@code inclusionList}, or {@code -(insertion point) - 1} when it is
 * not a member - the same encoding {@code Arrays.binarySearch} uses, so an insertion point falls out
 * of a failed lookup for free and {@link #register} needs no second pass.
 * <p>
 * Both paths compare widened, never on the stored byte, so unsigned order and ascending iteration
 * order are the same order. The walk below the limit stops early on a value too large, which is what
 * lets it produce the same insertion point the search would.
 * */
int inclusionSlot(long value) {
	int size = inclusionList.size();
	if(size <= LINEAR_SCAN_LIMIT) {
		for(int i = 0; i < size; i++) {
			long element = elementAt(i);
			if(element == value) { return i; }
			if(element > value) { return -(i + 1); }
		}
		return -(size + 1);
	}
	int low = 0, high = size - 1;
	while(low <= high) {
		int mid = (low + high) >>> 1;
		long element = elementAt(mid);
		if(element < value) { low = mid + 1; }
		else if(element > value) { high = mid - 1; }
		else { return mid; }
	}
	return -(low + 1);
}


/** Snapshots the set in ascending order, detached from the storage. Narrows, so it can throw. */
@Override
public List<Integer> getIntList() {
	List<Integer> elements = new ArrayList<>(count());
	for(int i = 0, n = count(); i < n; i++) { elements.add(Math.toIntExact(elementAt(i))); }
	return Collections.unmodifiableList(elements);
}
/** Snapshots the set in ascending order, detached from the storage. Narrows, so it can throw. */
@Override
public IntArrayList toList() {
	IntArrayList elements = new IntArrayList(count());
	for(int i = 0, n = count(); i < n; i++) { elements.add(Math.toIntExact(elementAt(i))); }
	return elements;
}
/** The lossless snapshot. The two int forms above narrow, and throw on an identity too big. */
public LongArrayList toLongArrayList() {
	LongArrayList elements = new LongArrayList(count());
	for(int i = 0, n = count(); i < n; i++) { elements.add(elementAt(i)); }
	return elements;
}

@Override
public String toString() {
	return getClass().getSimpleName() + toLongArrayList();
}


}

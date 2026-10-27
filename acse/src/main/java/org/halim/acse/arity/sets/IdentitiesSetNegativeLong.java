package org.halim.acse.arity.sets;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.sets.IdentitiesSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A set of long identities stored negatively: the contiguous range [firstIndex, lastIndex], both
 * ends inclusive, minus the holes punched in it by {@code exclusionList}. Memory is proportional to
 * the number of holes, not to the number of elements, so this is the representation to reach for
 * when a set is dense - a full range costs one pair of bounds and nothing else.
 * <p>
 * The flip side is that sparsity is expensive. Adding an element far outside the range widens the
 * range to reach it and turns every integer it swallowed along the way into a hole, so one add can
 * cost the whole gap - and here that gap is measured in longs, so it can be a gap no machine will
 * hold. The bound is the class's own ceiling, not the caller's patience.
 * <p>
 * Nothing is narrowed here - a stored hole is the value - so this is the terminal variant the other
 * three promote into, and the reference they are checked against. Negatives are still rejected,
 * which keeps the range [0, Long.MAX_VALUE] and leaves ascending order unambiguous.
 * <p>
 * Three invariants hold at all times, and everything below leans on them:
 * <ul>
 *   <li>{@code exclusionList} is sorted ascending with no duplicates, and every hole lies strictly
 *       between the bounds;</li>
 *   <li>neither bound is itself a hole, so the first element is always {@code firstIndex} and the
 *       last is always {@code lastIndex}. An empty set is {@code firstIndex > lastIndex};</li>
 *   <li>every stored value fits this variant's width, which {@link #checkFits} is the only gate
 *       for - a wrapped identity is indistinguishable from a real one, so it throws rather than
 *       narrowing silently. That throw is the signal to promote to the next width.</li>
 * </ul>
 * Not multi thread safe. The storage is the set: this class implements {@link IdentitiesSet} itself
 * rather than handing out a separate view, so there is one object per set and nothing to unwrap.
 * */
public class IdentitiesSetNegativeLong implements IdentitiesSet {


/** The largest identity this width can carry. Past it, promote to the next variant. */
public static final long MAX_VALUE = Long.MAX_VALUE;

LongArrayList exclusionList;
long firstIndex, lastIndex;

/** The smallest element. For an empty set this meets getEnd(), leaving an empty range. */
long getBegin() { return firstIndex; }

/** Exclusive like the intended java behavior. */
long getEnd() { return lastIndex + 1; }

long getFreeSpace() {
	if(!exclusionList.isEmpty()) { return holeAt(exclusionList.size() - 1); }
	checkFits(lastIndex + 1);
	return ++lastIndex;
}

public IdentitiesSetNegativeLong getCopy() {
	IdentitiesSetNegativeLong result = new IdentitiesSetNegativeLong(this.firstIndex, this.lastIndex);
	result.exclusionList.addAll(this.exclusionList);
	return result;
}

public IdentitiesSetNegativeLong(long firstIndex, long lastIndex) {
	this.firstIndex = firstIndex;
	// count() reads the span straight off the bounds, so a backwards range would report a negative
	// size and isEmpty() would sail right past it. Pinning the floor at firstIndex - 1 leaves the
	// empty set exactly one encoding.
	this.lastIndex = Math.max(lastIndex, firstIndex - 1);
	// Only a non-empty range actually stores anything, so only that one has to fit.
	if(this.firstIndex <= this.lastIndex) { checkFits(this.firstIndex); checkFits(this.lastIndex); }
	exclusionList = new LongArrayList();
}
public IdentitiesSetNegativeLong(long lastIndex) {
	this(0, lastIndex);
}



@Override
public long identityAt(long index) {
	long size = spanCount();
	if(index < 0 || index >= size) { throw new IndexOutOfBoundsException("index " + index + ", size " + size); }
	return elementAt(index);
}

@Override
public boolean register(long identity) {
	return addValue(identity);
}
@Override
public boolean unsign(long identity) {
	return removeValue(identity);
}

/** Inside the bounds and not punched out - two comparisons and one binary search. */
@Override
public boolean contains(long i) {
	return i >= firstIndex && i <= lastIndex && exclusionSlot(i) < 0;
}

@Override
public long size() {
	return spanCount();
}

@Override
public LongListerIterator identityIteritor() {
	return new Cursor();
}


/** The bounds and the holes - the representation itself, so a dense set stays three fields wide on disk too. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeLong(firstIndex);
	dos.writeLong(lastIndex);
	dos.writeInt(exclusionList.size());
	for(int p = 0; p < exclusionList.size(); p++) { dos.writeLong(holeAt(p)); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, refusing a hole that would break the invariants above rather than holding it. */
public static IdentitiesSetNegativeLong loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	long first = dis.readLong(), last = dis.readLong();
	IdentitiesSetNegativeLong set = new IdentitiesSetNegativeLong(first, last);
	for(int p = 0, count = dis.readInt(); p < count; p++) {
		long hole = dis.readLong();
		long below = p == 0 ? first : set.holeAt(p - 1);
		if(hole <= below || hole >= last) { throw new IOException("hole " + hole + " is not strictly between " + below + " and " + last); }
		set.exclusionList.add(set.narrow(hole));
	}
	return set;
}


/**
 * Walks the iteration order, and is the one place that turns an element index back into a value.
 * */
private final class Cursor implements LongListerIterator {
	
	long cursor;
	long lastReturned = -1;
	
	@Override
	public boolean hasNext() { return cursor < spanCount(); }
	
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
		removeValue(elementAt(lastReturned));
		// Everything past the removed element slid down one index. Coming from next() the cursor
		// sat after it and has to follow; coming from previous() it sat on it and already points
		// at the right successor.
		if(lastReturned < cursor) { cursor--; }
		lastReturned = -1;
	}
}


@Override
public boolean isEmpty() {
	return spanCount() == 0;
}

/**
 * The element count. A negative set carries more of them than an int can name: the bounds are longs
 * and only the holes cost memory, so a set of 2^40 identities is three fields wide. This is the real
 * count, and {@link #count()} is the narrowed one, used only where an int-shaped container needs a
 * capacity - which is exactly where the ceiling belongs.
 * */
long spanCount() {
	return lastIndex - firstIndex + 1 - exclusionList.size();
}

/** The count as an int, for sizing an int-indexed container. Throws rather than truncating. */
int count() {
	return Math.toIntExact(spanCount());
}

/**
 * The i-th element, with i already known to be in range.
 * <p>
 * {@code holeAt(p) - firstIndex - p} is the number of elements sitting before that hole, and it
 * never decreases, so a binary search finds the first hole the i-th element does not have to jump
 * over. The count of holes below that point is exactly how far the element is pushed up from its
 * dense position.
 * */
long elementAt(long i) {
	int low = 0, high = exclusionList.size();
	while(low < high) {
		int mid = (low + high) >>> 1;
		if(holeAt(mid) - firstIndex - mid > i) { high = mid; }
		else { low = mid + 1; }
	}
	return firstIndex + i + low;
}

/** The p-th hole. Nothing to widen at this width; kept for shape. */
long holeAt(int p) {
	return exclusionList.getLong(p);
}

/** Identity, kept so the four variants stay line-for-line the same shape. */
long narrow(long value) {
	return (long) value;
}

/** The ceiling, enforced. A wrapped identity would be indistinguishable from a real one. */
void checkFits(long value) {
	if(value < 0 || value > MAX_VALUE) {
		throw new IllegalArgumentException(value + " is not a valid identity, max " + MAX_VALUE);
	}
}

/**
 * Position of {@code value} in {@code exclusionList}, or {@code -(insertion point) - 1} when it is
 * not a hole - the same encoding {@code Arrays.binarySearch} uses. Compares widened, so a value
 * this width cannot hold lands outside the list instead of wrapping into it.
 * */
int exclusionSlot(long value) {
	int low = 0, high = exclusionList.size() - 1;
	while(low <= high) {
		int mid = (low + high) >>> 1;
		long hole = holeAt(mid);
		if(hole < value) { low = mid + 1; }
		else if(hole > value) { high = mid - 1; }
		else { return mid; }
	}
	return -(low + 1);
}

boolean addValue(long value) {
	checkFits(value);
	if(isEmpty()) {
		firstIndex = lastIndex = value;
		exclusionList.clear();
		return true;
	}
	if(value < firstIndex) {
		// Widening the range swallows the gap wholesale, so every integer in it that is not the
		// new element has to be punched out again.
		punchGap(0, value + 1, firstIndex - 1);
		firstIndex = value;
		return true;
	}
	if(value > lastIndex) {
		punchGap(exclusionList.size(), lastIndex + 1, value - 1);
		lastIndex = value;
		return true;
	}
	// Inside the range, so the value is a member unless a hole says otherwise; filling that hole
	// cannot break either invariant, since the bounds are untouched.
	int slot = exclusionSlot(value);
	if(slot < 0) { return false; }
	exclusionList.removeLong(slot);
	return true;
}

/**
 * Inserts [from, to] as holes at {@code at}, which is where they belong in ascending order.
 * <p>
 * The gap is materialised, so a long-range jump asks for an array the length of the jump. That is
 * the negative form's cost stated honestly: it throws here rather than pretending the widening was
 * cheap.
 * */
private void punchGap(int at, long from, long to) {
	long length = to - from + 1;
	if(length <= 0) { return; }
	int span = Math.toIntExact(length);
	long[] gap = new long[span];
	for(int k = 0; k < span; k++) { gap[k] = narrow(from + k); }
	exclusionList.addElements(at, gap, 0, span);
}

boolean removeValue(long value) {
	if(isEmpty() || value < firstIndex || value > lastIndex) { return false; }
	// A bound is never a hole, so removing one shrinks the range instead - and then drags it past
	// any holes the shrink just exposed, to restore that invariant.
	if(value == firstIndex) {
		firstIndex++;
		int peel = 0;
		while(peel < exclusionList.size() && holeAt(peel) == firstIndex + peel) { peel++; }
		if(peel > 0) {
			exclusionList.removeElements(0, peel);
			firstIndex += peel;
		}
		if(isEmpty()) { exclusionList.clear(); }
		return true;
	}
	if(value == lastIndex) {
		lastIndex--;
		int peel = 0;
		while(peel < exclusionList.size() && holeAt(exclusionList.size() - 1 - peel) == lastIndex - peel) { peel++; }
		if(peel > 0) {
			exclusionList.removeElements(exclusionList.size() - peel, exclusionList.size());
			lastIndex -= peel;
		}
		if(isEmpty()) { exclusionList.clear(); }
		return true;
	}
	int slot = exclusionSlot(value);
	if(slot >= 0) { return false; }
	exclusionList.add(-slot - 1, narrow(value));
	return true;
}

/** Materialises the set in ascending order. Linear in the size of the set, not of the storage. */
@Override
public List<Integer> getIntList() {
	List<Integer> elements = new ArrayList<>(count());
	int hole = 0;
	for(long value = firstIndex; value <= lastIndex; value++) {
		if(hole < exclusionList.size() && holeAt(hole) == value) { hole++; continue; }
		elements.add(Math.toIntExact(value));
	}
	return Collections.unmodifiableList(elements);
}
/** Materialises the set in ascending order. Linear in the size of the set, not of the storage. */
@Override
public IntArrayList toList() {
	IntArrayList elements = new IntArrayList(count());
	int hole = 0;
	for(long value = firstIndex; value <= lastIndex; value++) {
		if(hole < exclusionList.size() && holeAt(hole) == value) { hole++; continue; }
		elements.add(Math.toIntExact(value));
	}
	return elements;
}
/** The lossless materialisation. The two int forms above narrow, and throw on an element too big. */
public LongArrayList toLongArrayList() {
	LongArrayList elements = new LongArrayList(count());
	int hole = 0;
	for(long value = firstIndex; value <= lastIndex; value++) {
		if(hole < exclusionList.size() && holeAt(hole) == value) { hole++; continue; }
		elements.add(value);
	}
	return elements;
}

@Override
public String toString() {
	return isEmpty()
			? getClass().getSimpleName() + "[]"
			: getClass().getSimpleName() + "[" + firstIndex + ".." + lastIndex + " minus " + exclusionList + "]";
}


}

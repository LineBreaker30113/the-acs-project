package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.arity.sets.IdentitiesSet;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The most formal unary structure: a graph whose elements are the identities of the arity at index 0.
 * Each kind is a graph on them under the restrictions its storage keeps, the most formal being any graph,
 * loops and parallel links included, and symmetric: a link joins its two elements both ways until
 * {@link #isDirected}. A storage takes the sets
 * it can keep and refuses the others when built. The bits sit from {@code 1 << 8} up, above a connector's,
 * so a binary structure keeps them as the rules on its primary arity in the same int.
 * <p>
 * A request is a link without direction; on a directed graph its first points to its last, the parent to
 * its child. A link here is only the relation: a link carrying data is an entity, an identity of an arity
 * of its own, which makes it a binary structure enforcing a unary one,
 * {@link ACS_BinaryStructure_U}. A repeat is no such data but a count,
 * so the same two elements may be linked more than once unless the storage forbids it;
 * however many links join two elements, the reads count the parent once.
 * <p>
 * A request that would break a restriction is refused. A root, an element every element listing no parent
 * hangs from, is kept rather than refused, and so is a transitive reduction: the links a new one implies
 * are dropped, which loses nothing. A restriction not forced can still be asked, by the analyses at the end,
 * which follow parents and children only.
 * */
public interface ACS_UnaryStructure extends ACS_StructureBase {


@Override
default int getArityCount() { return 1; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0); }

/** Whether a link goes one way, from the parent to the child; without it a link goes both ways, so an element's parents are its children. */
boolean isDirected();


/**
 * One more link from {@code first} to {@code last}, beside the ones there. False when, where
 * the storage keeps one link a pair, one already was, or, where it keeps a transitive reduction, a path implies it.
 * Refuses an element its arity does not hold, and a link a restriction forbids.
 * */
@ACS_Requestable(0)
boolean relateToUnary(int first, int last, String comment);

/** One link fewer from {@code first} to {@code last}, the last one parting them. False when there was none; refuses a removal a restriction forbids. */
@ACS_Requestable(1)
boolean unrelateFromUnary(int first, int last, String comment);

/** Whether a link goes from {@code first} to {@code last}, either way round on a directionless graph. */
@ACS_Requestable(2)
boolean isRelatedToUnary(int first, int last);

/**
 * A link from {@code parent} to {@code element} when none joins them. False when one was already there,
 * or, under a transitive reduction, another path implies it; refuses what {@link #relateToUnary} refuses.
 * */
@ACS_Requestable(3)
boolean addParent(int element, int parent, String comment);

/** Removes every link from {@code parent} to {@code element}. False when there was none; refuses a removal a restriction forbids. */
@ACS_Requestable(4)
boolean removeParent(int element, int parent, String comment);

/**
 * Moves {@code element} under {@code parent}: every parent it has is dropped and {@code parent} becomes
 * its one, while the links to its children stay. With a root, a parent of 0 moves it under the root, dropping them all. False when nothing changes. Refuses what {@link #addParent} and
 * {@link #removeParent} refuse, but never for a second parent, since the first is dropped.
 * */
@ACS_Requestable(5)
boolean setParent(int element, int parent, String comment);

/**
 * What {@code first} points to becomes {@code lasts}, a repeat counting once, as one change: refused for what
 * the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
@ACS_Requestable(6)
boolean setLasts(int first, int[] lasts, String comment);

/** What points to {@code last} becomes {@code firsts}, as {@link #setLasts} sets it. */
@ACS_Requestable(7)
boolean setFirsts(int last, int[] firsts, String comment);

/**
 * Every link of {@code source} goes to {@code target}, and {@code source} is left with none: its parents
 * become parents of {@code target} and its children children of it, a link already there counting once.
 * Two changes in one request, so refused where the merged lists break a restriction, or where
 * {@code source} may not be left alone. False when nothing changes.
 * */
@ACS_Requestable(8)
default boolean moveUnary(int source, int target, String comment) {
	boolean changed = mergeUnary(source, target, comment);
	changed |= setFirsts(source, new int[0], comment);
	return setLasts(source, new int[0], comment) || changed;
}

/** As {@link #moveUnary}, but {@code source} keeps its links. */
@ACS_Requestable(9)
default boolean mergeUnary(int source, int target, String comment) {
	boolean changed = setFirsts(target, union(firstsOf(target), firstsOf(source), target), comment);
	return setLasts(target, union(lastsOf(target), lastsOf(source), target), comment) || changed;
}


/** How many parents the element has; with a root, 1 for an element listing none, the root. */
int parentCount(int element);

/** The element's parents, {@link #parentCount} of them, in the order the implementation states. */
void copyParentsInto(int element, int[] dest, int offset);

/** How many children the element has; with a root, the root's are the elements listing no parent. */
int childCount(int element);

/** The element's children, {@link #childCount} of them, in the order the implementation states. */
void copyChildrenInto(int element, int[] dest, int offset);

/** What {@code first} points to: its children, as {@link #copyChildrenInto} gives them. */
@ACS_Requestable(10)
default int[] lastsOf(int first) {
	int[] lasts = new int[childCount(first)];
	copyChildrenInto(first, lasts, 0);
	return lasts;
}

/** What points to {@code last}: its parents, as {@link #copyParentsInto} gives them. */
@ACS_Requestable(11)
default int[] firstsOf(int last) {
	int[] firsts = new int[parentCount(last)];
	copyParentsInto(last, firsts, 0);
	return firsts;
}

/** Whether a path of links leads from {@code from} to {@code to}; true when they are the same element. */
@ACS_Requestable(12)
default boolean reaches(int from, int to) { return pathLength(from, to) >= 0; }


/**
 * Every element above {@code element}, each once and nearest first, the element itself never; written as
 * an int[] is on the wire, its length then the elements. A walk up the parents over the reads above, which
 * a storage overrides where its layout answers without one, as every analysis here is.
 * */
default void writeAncestors(int element, DataOutputStream dos) throws IOException { writeInts(dos, walk(element, true, null)); }

/** Every element below {@code element}, each once and nearest first, the element itself never; written as {@link #writeAncestors} writes. */
default void writeDescendants(int element, DataOutputStream dos) throws IOException { writeInts(dos, walk(element, false, null)); }

/**
 * The fewest links up from {@code element} to an element with no parent - with a root, to the root. -1 when no walk up reaches one, as on a cycle with nothing above it.
 * */
@ACS_Requestable(15)
default int depth(int element) {
	IntArrayList level = new IntArrayList();
	level.add(element);
	IntOpenHashSet seen = new IntOpenHashSet(level);
	for(int depth = 0; !level.isEmpty(); depth++) {
		IntArrayList above = new IntArrayList();
		for(int i = 0; i < level.size(); i++) {
			int[] parents = next(level.getInt(i), true, null);
			if(parents.length == 0) { return depth; }
			for(int parent : parents) { if(seen.add(parent)) { above.add(parent); } }
		}
		level = above;
	}
	return -1;
}

/**
 * The nearest elements above both {@code element} and {@code other}, ascending, each counting as above
 * itself, so one above the other is the answer alone: on a lattice, their join. Written as
 * {@link #writeAncestors} writes.
 * */
default void writeNearestCommonAncestors(int element, int other, DataOutputStream dos) throws IOException { writeInts(dos, nearestCommon(element, other, true)); }

/** The nearest elements below both, ascending, each counting as below itself: on a lattice, their meet. Written as {@link #writeAncestors} writes. */
default void writeNearestCommonDescendants(int element, int other, DataOutputStream dos) throws IOException { writeInts(dos, nearestCommon(element, other, false)); }

/**
 * The fewest links between {@code element} and {@code other}, either way round: 0 for the same element,
 * -1 when no path joins them, as when they lie in different parts. With a root, it joins every
 * part.
 * */
@ACS_Requestable(18)
default int distance(int element, int other) { return steps(element, other, true); }

/** The fewest links from {@code from} to {@code to} along their direction: 0 for the same element, -1 where {@link #reaches} is false. */
@ACS_Requestable(19)
default int pathLength(int from, int to) { return steps(from, to, false); }


/**
 * Every element of {@code elements}, each after its parents, written as {@link #writeAncestors} writes.
 * Like every analysis taking a set, it runs over the graph the set makes: only links between two of its
 * elements are followed, so the arity's occupied set gives the whole graph and a part of it, a filter's
 * result, that part. Refuses a set among which a cycle runs, and a directionless graph, which has no
 * order.
 * */
default void writeTopologicalOrder(IdentitiesSet elements, DataOutputStream dos) throws IOException { writeInts(dos, IntArrayList.wrap(topologicalOrderAmong(listed(elements)))); }

/** Whether a cycle runs among {@code elements}, a loop included; on a directionless graph, a cycle but a link and its reverse. */
default boolean hasCycle(IdentitiesSet elements) { return hasCycleAmong(listed(elements)); }

/** The elements with no parent among {@code elements}, ascending; written as {@link #writeAncestors} writes. */
default void writeRoots(IdentitiesSet elements, DataOutputStream dos) throws IOException { writeInts(dos, IntArrayList.wrap(rootsAmong(listed(elements)))); }

/** The elements with no child among {@code elements}, ascending; written as {@link #writeAncestors} writes. */
default void writeLeaves(IdentitiesSet elements, DataOutputStream dos) throws IOException { writeInts(dos, IntArrayList.wrap(leavesAmong(listed(elements)))); }

/**
 * The parts {@code elements} fall into, each the elements a path joins, direction ignored. Written as their
 * count, an int, then each part as {@link #writeAncestors} writes, ascending, the parts in the order of
 * their smallest elements.
 * */
default void writeParts(IdentitiesSet elements, DataOutputStream dos) throws IOException {
	int[] listed = listed(elements);
	List<IntArrayList> parts = parts(listed, new IntOpenHashSet(listed));
	dos.writeInt(parts.size());
	for(IntArrayList part : parts) { writeInts(dos, part); }
}

/** The average {@link #distance} among {@code elements}, over the pairs a path joins; -1 when none is joined. */
default double connectivity(IdentitiesSet elements) { return connectivityAmong(listed(elements)); }

/** Whether every element of {@code elements} reaches every other along the arrows. */
default boolean reachesAll(IdentitiesSet elements) { return reachesAllAmong(listed(elements)); }

/** Whether {@code elements} make one part, direction ignored. */
default boolean connectsAll(IdentitiesSet elements) { return connectsAllAmong(listed(elements)); }


/** What {@link #writeAncestors} writes, as an int[]: the analyses as requests, which take and give no stream. */
@ACS_Requestable(13)
default int[] ancestorsOf(int element) { return walk(element, true, null).toIntArray(); }

/** What {@link #writeDescendants} writes, as an int[]. */
@ACS_Requestable(14)
default int[] descendantsOf(int element) { return walk(element, false, null).toIntArray(); }

/** What {@link #writeNearestCommonAncestors} writes, as an int[]. */
@ACS_Requestable(16)
default int[] nearestCommonAncestorsOf(int element, int other) { return nearestCommon(element, other, true).toIntArray(); }

/** What {@link #writeNearestCommonDescendants} writes, as an int[]. */
@ACS_Requestable(17)
default int[] nearestCommonDescendantsOf(int element, int other) { return nearestCommon(element, other, false).toIntArray(); }

/** {@link #writeTopologicalOrder} over the graph {@code elements} make, given as ints, since a request takes no set; refused as it refuses. */
@ACS_Requestable(20)
default int[] topologicalOrderAmong(int[] elements) {
	if(!isDirected()) { throw new UnsupportedOperationException("a directionless graph has no order"); }
	IntArrayList order = order(distinct(elements));
	if(order.size() < distinct(elements).length) { throw new IllegalStateException("a cycle runs among the elements; " + order.size() + " of " + distinct(elements).length + " ordered"); }
	return order.toIntArray();
}

/** {@link #hasCycle} among {@code elements}. */
@ACS_Requestable(21)
default boolean hasCycleAmong(int[] elements) {
	int[] listed = distinct(elements);
	if(isDirected()) { return order(listed).size() < listed.length; }
	IntOpenHashSet within = new IntOpenHashSet(listed);
	long links = 0;
	for(int element : listed) {
		for(int other : next(element, false, within)) {
			if(other == element) { return true; }
			if(element < other) { links++; }
		}
	}
	return links > listed.length - parts(listed, within).size();
}

/** What {@link #writeRoots} writes among {@code elements}, as an int[]. */
@ACS_Requestable(22)
default int[] rootsAmong(int[] elements) { return ends(distinct(elements), true).toIntArray(); }

/** What {@link #writeLeaves} writes among {@code elements}, as an int[]. */
@ACS_Requestable(23)
default int[] leavesAmong(int[] elements) { return ends(distinct(elements), false).toIntArray(); }

/** What {@link #writeParts} writes among {@code elements}, as one int[] in its layout: the part count, then each part's size and elements. */
@ACS_Requestable(24)
default int[] partsAmong(int[] elements) {
	int[] listed = distinct(elements);
	List<IntArrayList> parts = parts(listed, new IntOpenHashSet(listed));
	IntArrayList layout = new IntArrayList();
	layout.add(parts.size());
	for(IntArrayList part : parts) {
		layout.add(part.size());
		layout.addAll(part);
	}
	return layout.toIntArray();
}

/** {@link #connectivity} among {@code elements}. */
@ACS_Requestable(25)
default double connectivityAmong(int[] elements) {
	int[] listed = distinct(elements);
	IntOpenHashSet within = new IntOpenHashSet(listed);
	long sum = 0, pairs = 0;
	for(int element : listed) {
		IntArrayList level = new IntArrayList();
		level.add(element);
		IntOpenHashSet seen = new IntOpenHashSet(level);
		for(int steps = 1; !level.isEmpty(); steps++) {
			IntArrayList beyond = new IntArrayList();
			for(int i = 0; i < level.size(); i++) {
				for(int at : around(level.getInt(i), within)) { if(seen.add(at)) { beyond.add(at); } }
			}
			sum += (long) steps * beyond.size();
			pairs += beyond.size();
			level = beyond;
		}
	}
	return pairs == 0 ? -1 : (double) sum / pairs;
}

/** {@link #reachesAll} among {@code elements}. */
@ACS_Requestable(26)
default boolean reachesAllAmong(int[] elements) {
	int[] listed = distinct(elements);
	if(listed.length <= 1) { return true; }
	IntOpenHashSet within = new IntOpenHashSet(listed);
	return walk(listed[0], false, within).size() == listed.length - 1 && walk(listed[0], true, within).size() == listed.length - 1;
}

/** {@link #connectsAll} among {@code elements}. */
@ACS_Requestable(27)
default boolean connectsAllAmong(int[] elements) {
	int[] listed = distinct(elements);
	return parts(listed, new IntOpenHashSet(listed)).size() <= 1;
}


/** A breadth-first walk from {@code element} along the parents, or the children, each element once and the start never; {@code within} null for the whole graph. */
private IntArrayList walk(int element, boolean up, IntOpenHashSet within) {
	IntArrayList found = new IntArrayList();
	IntOpenHashSet seen = new IntOpenHashSet();
	seen.add(element);
	for(int i = -1; i < found.size(); i++) {
		for(int at : next(i < 0 ? element : found.getInt(i), up, within)) { if(seen.add(at)) { found.add(at); } }
	}
	return found;
}

/** The common ones, less every one that lies beyond another common one. */
private IntArrayList nearestCommon(int element, int other, boolean up) {
	IntOpenHashSet common = new IntOpenHashSet(walk(element, up, null));
	common.add(element);
	IntOpenHashSet others = new IntOpenHashSet(walk(other, up, null));
	others.add(other);
	common.retainAll(others);
	IntOpenHashSet beyond = new IntOpenHashSet();
	for(IntIterator it = common.iterator(); it.hasNext(); ) { beyond.addAll(walk(it.nextInt(), up, null)); }
	common.removeAll(beyond);
	int[] nearest = common.toIntArray();
	Arrays.sort(nearest);
	return IntArrayList.wrap(nearest);
}

/** A breadth-first walk from {@code from} along the children, or {@code eitherWay} the parents too, counting the levels to {@code to}. */
private int steps(int from, int to, boolean eitherWay) {
	if(from == to) { return 0; }
	IntArrayList level = new IntArrayList();
	level.add(from);
	IntOpenHashSet seen = new IntOpenHashSet(level);
	for(int steps = 1; !level.isEmpty(); steps++) {
		IntArrayList beyond = new IntArrayList();
		for(int i = 0; i < level.size(); i++) {
			for(int at : eitherWay ? around(level.getInt(i), null) : next(level.getInt(i), false, null)) {
				if(at == to) { return steps; }
				if(seen.add(at)) { beyond.add(at); }
			}
		}
		level = beyond;
	}
	return -1;
}

/** Kahn's order: the elements with no parent among them first, then each once its last parent is placed; shorter than the list when a cycle runs in it. */
private IntArrayList order(int[] listed) {
	IntOpenHashSet within = new IntOpenHashSet(listed);
	IntArrayList order = new IntArrayList();
	Int2IntOpenHashMap waiting = new Int2IntOpenHashMap();
	for(int element : listed) {
		int parents = next(element, true, within).length;
		if(parents == 0) { order.add(element); }
		else { waiting.put(element, parents); }
	}
	for(int i = 0; i < order.size(); i++) {
		for(int child : next(order.getInt(i), false, within)) { if(waiting.addTo(child, -1) == 1) { order.add(child); } }
	}
	return order;
}

private IntArrayList ends(int[] listed, boolean up) {
	IntOpenHashSet within = new IntOpenHashSet(listed);
	IntArrayList ends = new IntArrayList();
	for(int element : listed) { if(next(element, up, within).length == 0) { ends.add(element); } }
	return ends;
}

private List<IntArrayList> parts(int[] listed, IntOpenHashSet within) {
	List<IntArrayList> parts = new ArrayList<>();
	IntOpenHashSet seen = new IntOpenHashSet();
	for(int element : listed) {
		if(!seen.add(element)) { continue; }
		IntArrayList part = new IntArrayList();
		part.add(element);
		for(int i = 0; i < part.size(); i++) {
			for(int at : around(part.getInt(i), within)) { if(seen.add(at)) { part.add(at); } }
		}
		part.sort(null);
		parts.add(part);
	}
	return parts;
}

/** The set's elements, ascending, as the ints a structure holds; an identity past an int is refused rather than narrowed. */
private static int[] listed(IdentitiesSet elements) {
	int[] listed = new int[Math.toIntExact(elements.size())];
	IdentitiesSet.LongListerIterator it = elements.identityIteritor();
	for(int i = 0; i < listed.length; i++) { listed[i] = Math.toIntExact(it.next()); }
	return listed;
}

/** The given elements, each once, ascending, as a set's would be. */
private static int[] distinct(int[] elements) {
	int[] distinct = new IntOpenHashSet(elements).toIntArray();
	Arrays.sort(distinct);
	return distinct;
}

/** {@code held} and {@code added}, each once, {@code self} left out, so a merge makes no loop of its own. */
private static int[] union(int[] held, int[] added, int self) {
	IntOpenHashSet union = new IntOpenHashSet(held);
	for(int at : added) { if(at != self) { union.add(at); } }
	return union.toIntArray();
}

private int[] around(int element, IntOpenHashSet within) {
	int[] parents = next(element, true, within), children = next(element, false, within);
	int[] both = Arrays.copyOf(parents, parents.length + children.length);
	System.arraycopy(children, 0, both, parents.length, children.length);
	return both;
}

/** The element's parents, or children, keeping those {@code within} holds; null keeps them all. */
private int[] next(int element, boolean up, IntOpenHashSet within) {
	int[] into = new int[up ? parentCount(element) : childCount(element)];
	if(up) { copyParentsInto(element, into, 0); }
	else { copyChildrenInto(element, into, 0); }
	if(within == null) { return into; }
	int kept = 0;
	for(int at : into) { if(within.contains(at)) { into[kept++] = at; } }
	return Arrays.copyOf(into, kept);
}

private static void writeInts(DataOutputStream dos, IntArrayList ints) throws IOException {
	dos.writeInt(ints.size());
	for(int i = 0; i < ints.size(); i++) { dos.writeInt(ints.getInt(i)); }
}


}

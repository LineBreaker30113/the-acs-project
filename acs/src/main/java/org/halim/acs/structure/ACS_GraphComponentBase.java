package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.arity.sets.IdentitiesSet;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A graph whose elements are the identities of the arity at index 0. Each kind is a graph on them under
 * restrictions: the bits of one int, given when the structure is built and fixed from then on, 0 being any
 * graph, loops included, and symmetric in every kind, since clusters are symmetric by nature: an edge joins
 * its two elements both ways until {@link #DIRECTED}. A structure is built from its
 * restrictions, and a storage takes the sets it can keep and refuses the others when built. The bits sit
 * from {@code 1 << 8} up, above a connector's, so a kind that is both keeps them in one int.
 * <p>
 * An edge goes from a parent to its child. A unary structure holds the edges alone; a node-edge one makes
 * each edge an identity of the arity at index 1, so it carries data, and adds restrictions of its own.
 * However many edges join two elements, the reads count the parent once.
 * <p>
 * A request that would break a restriction is refused. {@link #ROOTED} is kept rather than refused, since
 * it catches what lists no parent, and {@link #NO_IMPLIED_EDGES} is kept as each kind says. A restriction
 * not forced can still be asked, by the analyses at the end, which follow parents and children only and so
 * hold for every kind.
 * */
public interface ACS_GraphComponentBase extends ACS_StructureComponentBase {

/** No edge from an element to itself. */
int NO_LOOPS = 1 << 8;

/** No path returns to where it started, so no loops either; on a directionless graph, no cycle but an edge and its reverse, which makes it a forest. */
int ACYCLIC = 1 << 9;

/** At most one parent an element, its one "from"; with {@link #ACYCLIC}, a tree or a forest. */
int AT_MOST_ONE_FROM = 1 << 10;

/** At most one child an element, its one "to"; with {@link #AT_MOST_ONE_FROM}, a sequence. */
int AT_MOST_ONE_TO = 1 << 11;

/**
 * Element 0 is the root: it takes no parent, an element listing none hangs from it, and so the root
 * reaches every element. The root is a fallback rather than an edge, so it counts against no
 * restriction and is never added or removed as a parent; an element losing its last parent falls back
 * to it.
 * */
int ROOTED = 1 << 12;

/**
 * No edge another path already implies - the transitive reduction, unique on an acyclic graph. An edge
 * implied by the ones there is not added; what becomes of the ones a new edge implies is the kind's to say.
 * */
int NO_IMPLIED_EDGES = 1 << 13;

/**
 * Every element reaches every other along the arrows. An element counts from its first edge, since a
 * new identity has none, and forget, which no structure refuses, can still break it. Asked by
 * {@link #reachesAll}.
 * */
int FULLY_REACHABLE = 1 << 14;

/** Every two elements are joined by a path, direction ignored: no islands. Counted as {@link #FULLY_REACHABLE} counts; asked by {@link #connectsAll}. */
int ALL_CONNECTED = 1 << 15;

/**
 * An edge goes one way, from the parent to the child. Without it an edge goes both ways, so an element's
 * parents are its children; each kind says how its edges take a direction.
 * */
int DIRECTED = 1 << 17;


/** The restrictions it was built with, an OR of the constants above. */
int getRestrictions();

default boolean allowsLoops() { return (getRestrictions() & (NO_LOOPS | ACYCLIC)) == 0; }
default boolean isDirected() { return (getRestrictions() & DIRECTED) != 0; }
default boolean isAcyclic() { return (getRestrictions() & ACYCLIC) != 0; }
default boolean hasAtMostOneFrom() { return (getRestrictions() & AT_MOST_ONE_FROM) != 0; }
default boolean hasAtMostOneTo() { return (getRestrictions() & AT_MOST_ONE_TO) != 0; }
default boolean isRooted() { return (getRestrictions() & ROOTED) != 0; }
default boolean hasNoImpliedEdges() { return (getRestrictions() & NO_IMPLIED_EDGES) != 0; }
default boolean isFullyReachable() { return (getRestrictions() & FULLY_REACHABLE) != 0; }
default boolean isAllConnected() { return (getRestrictions() & ALL_CONNECTED) != 0; }


/**
 * An edge from {@code parent} to {@code element}. False when it was already there, or, with
 * {@link #NO_IMPLIED_EDGES}, when another path implies it. Refuses an element its arity does not hold,
 * and an edge a restriction forbids.
 * */
boolean addParent(int element, int parent, String comment);

/** False when there was no such edge; refuses a removal a restriction forbids. */
boolean removeParent(int element, int parent, String comment);

/**
 * Moves {@code element} under {@code parent}: every parent it has is dropped and {@code parent} becomes
 * its one, while the edges to its children stay. With {@link #ROOTED}, a parent of 0 moves it under the
 * root, dropping them all. False when nothing changes. Refuses what {@link #addParent} and
 * {@link #removeParent} refuse, but never for a second parent, since the first is dropped.
 * */
boolean setParent(int element, int parent, String comment);

/** How many parents the element has; with {@link #ROOTED}, 1 for an element listing none, the root. */
int parentCount(int element);

/** The element's parents, {@link #parentCount} of them, in the order the implementation states. */
void copyParentsInto(int element, int[] dest, int offset);

/** How many children the element has; with {@link #ROOTED}, the root's are the elements listing no parent. */
int childCount(int element);

/** The element's children, {@link #childCount} of them, in the order the implementation states. */
void copyChildrenInto(int element, int[] dest, int offset);

/** Whether a path of edges leads from {@code from} to {@code to}; true when they are the same element. */
boolean reaches(int from, int to);


/**
 * One more edge from {@code first} to {@code last}: a request is a link without direction, and on a directed
 * graph its first points to its last. False when the kind keeps one edge a pair and it is there; refuses
 * what {@link #addParent} refuses.
 * */
boolean connectTo(int first, int last, String comment);

/** One edge fewer from {@code first} to {@code last}; false when there is none. */
boolean disconnectFrom(int first, int last, String comment);

/** Whether an edge goes from {@code first} to {@code last}, either way round on a directionless graph. */
boolean isConnectedTo(int first, int last);

/** What {@code first} points to: its children, as {@link #copyChildrenInto} gives them. */
default int[] lastsOf(int first) {
	int[] lasts = new int[childCount(first)];
	copyChildrenInto(first, lasts, 0);
	return lasts;
}

/** What points to {@code last}: its parents, as {@link #copyParentsInto} gives them. */
default int[] firstsOf(int last) {
	int[] firsts = new int[parentCount(last)];
	copyParentsInto(last, firsts, 0);
	return firsts;
}

default int lastCount(int first) { return childCount(first); }

default int firstCount(int last) { return parentCount(last); }

/**
 * What {@code first} points to becomes {@code lasts}, a repeat counting once, as one change: refused for what
 * the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
boolean setLasts(int first, int[] lasts, String comment);

/** What points to {@code last} becomes {@code firsts}, as {@link #setLasts} sets it. */
boolean setFirsts(int last, int[] firsts, String comment);


/**
 * Every element above {@code element}, each once and nearest first, the element itself never; written as
 * an int[] is on the wire, its length then the elements. A walk up the parents over the reads above, which
 * a storage overrides where its layout answers without one, as every analysis here is.
 * */
default void writeAncestors(int element, DataOutputStream dos) throws IOException { writeInts(dos, walk(element, true, null)); }

/** Every element below {@code element}, each once and nearest first, the element itself never; written as {@link #writeAncestors} writes. */
default void writeDescendants(int element, DataOutputStream dos) throws IOException { writeInts(dos, walk(element, false, null)); }

/**
 * The fewest edges up from {@code element} to an element with no parent - with {@link #ROOTED}, to the
 * root. -1 when no walk up reaches one, as on a cycle with nothing above it.
 * */
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
 * The fewest edges between {@code element} and {@code other}, either way round: 0 for the same element,
 * -1 when no path joins them, as when they lie in different parts. With {@link #ROOTED}, the root joins
 * every part.
 * */
default int distance(int element, int other) { return steps(element, other, true); }

/** The fewest edges from {@code from} to {@code to} along their direction: 0 for the same element, -1 where {@link #reaches} is false. */
default int pathLength(int from, int to) { return steps(from, to, false); }


/**
 * Every element of {@code elements}, each after its parents, written as {@link #writeAncestors} writes.
 * Like every analysis taking a set, it runs over the graph the set makes: only edges between two of its
 * elements are followed, so the arity's occupied set gives the whole graph and a part of it, a filter's
 * result, that part. Refuses a set among which a cycle runs, and a directionless graph, which has no
 * order.
 * */
default void writeTopologicalOrder(IdentitiesSet elements, DataOutputStream dos) throws IOException {
	if(!isDirected()) { throw new UnsupportedOperationException("a directionless graph has no order"); }
	IntArrayList order = order(elements);
	if(order.size() < elements.size()) { throw new IllegalStateException("a cycle runs among the elements; " + order.size() + " of " + elements.size() + " ordered"); }
	writeInts(dos, order);
}

/** Whether a cycle runs among {@code elements}, a loop included; on a directionless graph, a cycle but an edge and its reverse. */
default boolean hasCycle(IdentitiesSet elements) {
	if(isDirected()) { return order(elements).size() < elements.size(); }
	long edges = 0;
	for(int element : listed(elements)) {
		for(int other : next(element, false, elements)) {
			if(other == element) { return true; }
			if(element < other) { edges++; }
		}
	}
	return edges > elements.size() - parts(elements).size();
}

/** The elements with no parent among {@code elements}, ascending; written as {@link #writeAncestors} writes. */
default void writeRoots(IdentitiesSet elements, DataOutputStream dos) throws IOException { writeInts(dos, ends(elements, true)); }

/** The elements with no child among {@code elements}, ascending; written as {@link #writeAncestors} writes. */
default void writeLeaves(IdentitiesSet elements, DataOutputStream dos) throws IOException { writeInts(dos, ends(elements, false)); }

/**
 * The parts {@code elements} fall into, each the elements a path joins, direction ignored. Written as their
 * count, an int, then each part as {@link #writeAncestors} writes, ascending, the parts in the order of
 * their smallest elements.
 * */
default void writeParts(IdentitiesSet elements, DataOutputStream dos) throws IOException {
	List<IntArrayList> parts = parts(elements);
	dos.writeInt(parts.size());
	for(IntArrayList part : parts) { writeInts(dos, part); }
}

/** The average {@link #distance} among {@code elements}, over the pairs a path joins; -1 when none is joined. */
default double connectivity(IdentitiesSet elements) {
	long sum = 0, pairs = 0;
	for(int element : listed(elements)) {
		IntArrayList level = new IntArrayList();
		level.add(element);
		IntOpenHashSet seen = new IntOpenHashSet(level);
		for(int steps = 1; !level.isEmpty(); steps++) {
			IntArrayList beyond = new IntArrayList();
			for(int i = 0; i < level.size(); i++) {
				for(int at : around(level.getInt(i), elements)) { if(seen.add(at)) { beyond.add(at); } }
			}
			sum += (long) steps * beyond.size();
			pairs += beyond.size();
			level = beyond;
		}
	}
	return pairs == 0 ? -1 : (double) sum / pairs;
}

/** Whether every element of {@code elements} reaches every other along the arrows: {@link #FULLY_REACHABLE}, asked. */
default boolean reachesAll(IdentitiesSet elements) {
	if(elements.size() <= 1) { return true; }
	int first = listed(elements)[0];
	return walk(first, false, elements).size() == elements.size() - 1 && walk(first, true, elements).size() == elements.size() - 1;
}

/** Whether {@code elements} make one part: {@link #ALL_CONNECTED}, asked. */
default boolean connectsAll(IdentitiesSet elements) { return parts(elements).size() <= 1; }


/** What {@link #writeAncestors} writes, as an int[]: the analyses as requests, which take and give no stream. */
default int[] ancestorsOf(int element) { return walk(element, true, null).toIntArray(); }

/** What {@link #writeDescendants} writes, as an int[]. */
default int[] descendantsOf(int element) { return walk(element, false, null).toIntArray(); }

/** What {@link #writeNearestCommonAncestors} writes, as an int[]. */
default int[] nearestCommonAncestorsOf(int element, int other) { return nearestCommon(element, other, true).toIntArray(); }

/** What {@link #writeNearestCommonDescendants} writes, as an int[]. */
default int[] nearestCommonDescendantsOf(int element, int other) { return nearestCommon(element, other, false).toIntArray(); }

/** {@link #writeTopologicalOrder} over the graph {@code elements} make, given as ints, since a request takes no set; refused as it refuses. */
int[] topologicalOrderAmong(int[] elements);

/** {@link #hasCycle} among {@code elements}. */
boolean hasCycleAmong(int[] elements);

/** What {@link #writeRoots} writes among {@code elements}, as an int[]. */
int[] rootsAmong(int[] elements);

/** What {@link #writeLeaves} writes among {@code elements}, as an int[]. */
int[] leavesAmong(int[] elements);

/** What {@link #writeParts} writes among {@code elements}, as one int[] in its layout: the part count, then each part's size and elements. */
int[] partsAmong(int[] elements);

/** {@link #connectivity} among {@code elements}. */
double connectivityAmong(int[] elements);

/** {@link #reachesAll} among {@code elements}. */
boolean reachesAllAmong(int[] elements);

/** {@link #connectsAll} among {@code elements}. */
boolean connectsAllAmong(int[] elements);


/** A breadth-first walk from {@code element} along the parents, or the children, each element once and the start never; {@code within} null for the whole graph. */
private IntArrayList walk(int element, boolean up, IdentitiesSet within) {
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

/** Kahn's order: the elements with no parent among them first, then each once its last parent is placed; shorter than the set when a cycle runs in it. */
private IntArrayList order(IdentitiesSet elements) {
	IntArrayList order = new IntArrayList();
	Int2IntOpenHashMap waiting = new Int2IntOpenHashMap();
	for(int element : listed(elements)) {
		int parents = next(element, true, elements).length;
		if(parents == 0) { order.add(element); }
		else { waiting.put(element, parents); }
	}
	for(int i = 0; i < order.size(); i++) {
		for(int child : next(order.getInt(i), false, elements)) { if(waiting.addTo(child, -1) == 1) { order.add(child); } }
	}
	return order;
}

private IntArrayList ends(IdentitiesSet elements, boolean up) {
	IntArrayList ends = new IntArrayList();
	for(int element : listed(elements)) { if(next(element, up, elements).length == 0) { ends.add(element); } }
	return ends;
}

private List<IntArrayList> parts(IdentitiesSet elements) {
	List<IntArrayList> parts = new ArrayList<>();
	IntOpenHashSet seen = new IntOpenHashSet();
	for(int element : listed(elements)) {
		if(!seen.add(element)) { continue; }
		IntArrayList part = new IntArrayList();
		part.add(element);
		for(int i = 0; i < part.size(); i++) {
			for(int at : around(part.getInt(i), elements)) { if(seen.add(at)) { part.add(at); } }
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

private int[] around(int element, IdentitiesSet within) {
	int[] parents = next(element, true, within), children = next(element, false, within);
	int[] both = Arrays.copyOf(parents, parents.length + children.length);
	System.arraycopy(children, 0, both, parents.length, children.length);
	return both;
}

/** The element's parents, or children, keeping those {@code within} holds; null keeps them all. */
private int[] next(int element, boolean up, IdentitiesSet within) {
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

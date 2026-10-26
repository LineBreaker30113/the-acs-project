package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Requestable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Formal unary structure: a graph on identities of arity 0. */
public interface ACS_UnaryStructure extends ACS_StructureBase {

	byte STEP_RELATE_TO = 1;
	byte STEP_UNRELATE_TO = -1;

	@Override
	default int getArityCount() { return 1; }

	@Override
	default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0); }

	default boolean isDirected() { return hasRestriction(UnaryRestrictions.DIRECTED); }
	default boolean isAcyclic() { return hasRestriction(UnaryRestrictions.ACYCLIC); }

	@ACS_Requestable(5)
	boolean relateToUnary(int predecessor, int successor, String comment);

	@ACS_Requestable(6)
	boolean unrelateFromUnary(int predecessor, int successor, String comment);

	@ACS_Requestable(7)
	boolean isRelatedToUnary(int predecessor, int successor);

	@ACS_Requestable(8)
	boolean setPredecessor(int element, int predecessor, String comment);

	@ACS_Requestable(9)
	boolean setPredecessors(int element, int[] predecessors, String comment);

	@ACS_Requestable(10)
	boolean setSuccessors(int element, int[] successors, String comment);

	@ACS_Requestable(11)
	default boolean moveUnary(int source, int target, String comment) {
		boolean changed = mergeUnary(source, target, comment);
		changed |= setPredecessors(source, new int[0], comment);
		return setSuccessors(source, new int[0], comment) || changed;
	}

	@ACS_Requestable(12)
	default boolean mergeUnary(int source, int target, String comment) {
		boolean changed = setPredecessors(target, union(predecessorsOf(target), predecessorsOf(source), target), comment);
		return setSuccessors(target, union(successorsOf(target), successorsOf(source), target), comment) || changed;
	}

	int predecessorCount(int element);

	void copyPredecessorsInto(int element, int[] dest, int offset);

	int successorCount(int element);

	void copySuccessorsInto(int element, int[] dest, int offset);

	@ACS_Requestable(13)
	default int[] predecessorsOf(int element) {
		int[] predecessors = new int[predecessorCount(element)];
		copyPredecessorsInto(element, predecessors, 0);
		return predecessors;
	}

	@ACS_Requestable(14)
	default int[] successorsOf(int element) {
		int[] successors = new int[successorCount(element)];
		copySuccessorsInto(element, successors, 0);
		return successors;
	}

	@ACS_Requestable(15)
	default boolean reaches(int from, int to) { return pathLength(from, to) >= 0; }

	@ACS_Requestable(16)
	default int[] ancestorsOf(int element) { return walk(element, true, null).toIntArray(); }

	@ACS_Requestable(17)
	default int[] descendantsOf(int element) { return walk(element, false, null).toIntArray(); }

	@ACS_Requestable(18)
	default int depth(int element) {
		IntArrayList level = new IntArrayList();
		level.add(element);
		IntOpenHashSet seen = new IntOpenHashSet(level);
		for(int depth = 0; !level.isEmpty(); depth++) {
			IntArrayList above = new IntArrayList();
			for(int i = 0; i < level.size(); i++) {
				int[] predecessors = next(level.getInt(i), true, null);
				if(predecessors.length == 0) { return depth; }
				for(int predecessor : predecessors) { if(seen.add(predecessor)) { above.add(predecessor); } }
			}
			level = above;
		}
		return -1;
	}

	@ACS_Requestable(19)
	default int[] nearestCommonAncestorsOf(int element, int other) { return nearestCommon(element, other, true).toIntArray(); }

	@ACS_Requestable(20)
	default int[] nearestCommonDescendantsOf(int element, int other) { return nearestCommon(element, other, false).toIntArray(); }

	@ACS_Requestable(21)
	default int distance(int element, int other) { return steps(element, other, true); }

	@ACS_Requestable(22)
	default int pathLength(int from, int to) { return steps(from, to, false); }

	@ACS_Requestable(23)
	default int[] topologicalOrderAmong(int[] elements) {
		if(!isDirected()) { throw new UnsupportedOperationException("a directionless graph has no order"); }
		IntArrayList order = order(distinct(elements));
		if(order.size() < distinct(elements).length) { throw new IllegalStateException("a cycle runs among the elements; " + order.size() + " of " + distinct(elements).length + " ordered"); }
		return order.toIntArray();
	}

	@ACS_Requestable(24)
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

	@ACS_Requestable(25)
	default int[] rootsAmong(int[] elements) { return ends(distinct(elements), true).toIntArray(); }

	@ACS_Requestable(26)
	default int[] leavesAmong(int[] elements) { return ends(distinct(elements), false).toIntArray(); }

	@ACS_Requestable(27)
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

	@ACS_Requestable(28)
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

	@ACS_Requestable(29)
	default boolean reachesAllAmong(int[] elements) {
		int[] listed = distinct(elements);
		if(listed.length <= 1) { return true; }
		IntOpenHashSet within = new IntOpenHashSet(listed);
		return walk(listed[0], false, within).size() == listed.length - 1 && walk(listed[0], true, within).size() == listed.length - 1;
	}

	@ACS_Requestable(30)
	default boolean connectsAllAmong(int[] elements) {
		int[] listed = distinct(elements);
		return parts(listed, new IntOpenHashSet(listed)).size() <= 1;
	}


/** A breadth-first walk from {@code element} along the predecessors, or the successors, each element once and the start never; {@code within} null for the whole graph. */
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

/** A breadth-first walk from {@code from} along the successors, or {@code eitherWay} the predecessors too, counting the levels to {@code to}. */
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

/** Kahn's order: the elements with no predecessor among them first, then each once its last predecessor is placed; shorter than the list when a cycle runs in it. */
private IntArrayList order(int[] listed) {
	IntOpenHashSet within = new IntOpenHashSet(listed);
	IntArrayList order = new IntArrayList();
	Int2IntOpenHashMap waiting = new Int2IntOpenHashMap();
	for(int element : listed) {
		int predecessors = next(element, true, within).length;
		if(predecessors == 0) { order.add(element); }
		else { waiting.put(element, predecessors); }
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
	int[] predecessors = next(element, true, within), successors = next(element, false, within);
	int[] both = Arrays.copyOf(predecessors, predecessors.length + successors.length);
	System.arraycopy(successors, 0, both, predecessors.length, successors.length);
	return both;
}

/** The element's predecessors, or successors, keeping those {@code within} holds; null keeps them all. */
private int[] next(int element, boolean up, IntOpenHashSet within) {
	int[] into = new int[up ? predecessorCount(element) : successorCount(element)];
	if(up) { copyPredecessorsInto(element, into, 0); }
	else { copySuccessorsInto(element, into, 0); }
	if(within == null) { return into; }
	int kept = 0;
	for(int at : into) { if(within.contains(at)) { into[kept++] = at; } }
	return Arrays.copyOf(into, kept);
}

}

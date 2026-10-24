package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.ACS_Requestable;
import org.roaringbitmap.RoaringBitmap;

import java.io.DataInputStream;
import java.io.IOException;


/**
 * The node side as the node arity connected to itself in one Roaring bitmap: every unordered pair of nodes
 * has an index, and the index is in the bitmap exactly when the pair has an edge. Beside it, one int list
 * holds the edges in their pairs' index order, so an edge's place in the list is how many connected pairs
 * sit below its pair in the bitmap: the bitmap's rank of the index finds the edge. The edge itself is the
 * edge arity's own identity, captured like any other, so edges stay dense.
 * <p>
 * The index of nodes {@code a <= b} is {@code b(b+1)/2 + a}: the pairs listed by their larger node, each
 * larger node's pairs in a run. No stride is fixed, so the node arity grows without moving an index. The
 * index is an int, so the larger node stays below 65535; an edge past that is refused as a value that does
 * not fit. One index a pair holds one edge, so there are no parallel edges here, and on a directed graph an
 * edge from {@code a} to {@code b} and one from {@code b} to {@code a} cannot both exist.
 * <p>
 * Finding the edge of two nodes is an index, a probe and a rank. A node's edges with smaller nodes are one
 * run; its edges with larger nodes lie one per later run, so finding those probes every node up to the
 * highest connected one. Making or removing an edge shifts the list past its place, a copy of the edges
 * above it; moving the edge arity's own identities instead would move their data with them, which is
 * slower. Not multi thread safe.
 * */
public class SingleRoaringNodeEdgeComponent extends NodeEdgeGraphBase {

/** Past this, a larger node's run ends past an int. */
static final int NODE_LIMIT = 65535;

/** An index per pair with an edge. */
final RoaringBitmap pairs = new RoaringBitmap();
/** The edges in their pairs' index order: an edge's place is how many connected pairs sit below its pair. */
final IntArrayList edgesInOrder = new IntArrayList();


public SingleRoaringNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean directed, boolean acyclic, boolean allowsLoops) {
	super(core, journal, nodeArity, edgeArity, directed, acyclic, allowsLoops, false);
}

SingleRoaringNodeEdgeComponent(ACS_Core core, ACS_Journal journal, Rules rules) {
	super(core, journal, rules);
	if(rules.allowsParallelEdges) { throw new IllegalArgumentException("one index a pair holds one edge"); }
}


/** The start of the larger node's run. */
static long runOf(int larger) { return (long) larger * (larger + 1) / 2; }

/** The unordered pair's index, or -1 for a negative node or one past the limit. */
static long indexOf(int node, int otherNode) {
	int smaller = Math.min(node, otherNode), larger = Math.max(node, otherNode);
	if(smaller < 0 || larger >= NODE_LIMIT) { return -1; }
	return runOf(larger) + smaller;
}

/** The larger node of an index: the last run starting at or before it. */
static int largerOf(int index) {
	int larger = (int) ((Math.sqrt(8.0 * index + 1) - 1) / 2);
	while(runOf(larger) > index) { larger--; }
	while(runOf(larger + 1) <= index) { larger++; }
	return larger;
}

/** The edge of a connected pair's index: the list entry at the count of pairs below it. */
int edgeAt(int index) { return edgesInOrder.getInt(pairs.rank(index) - 1); }


/** One pair holds one edge, whichever way round, and its index must be an int. */
@Override
void checkIndexable(int first, int second) {
	long index = indexOf(first, second);
	if(index < 0) { throw new ArithmeticException("nodes " + first + " and " + second + " have no int index; nodes stay below " + NODE_LIMIT); }
	if(pairs.contains((int) index)) { throw new IllegalArgumentException("nodes " + first + " and " + second + " already share an edge; one pair holds one"); }
}

/** The pair's place is the count below it, taken before the index is added. */
@Override
void index(int edge, int first, int second) {
	int index = (int) indexOf(first, second);
	int place = pairs.rank(index);
	pairs.add(index);
	edgesInOrder.add(place, edge);
}

@Override
void unindex(int edge, int first, int second) {
	int index = (int) indexOf(first, second);
	edgesInOrder.removeInt(pairs.rank(index) - 1);
	pairs.remove(index);
}

/** All indexes first, then each edge into its place, so a load never shifts the list. */
@Override
void rebuildIndex() throws IOException {
	int edges = 0;
	for(int at = 0; at < ends.size(); at += 2) {
		if(ends.getInt(at) < 0) { continue; }
		long index = indexOf(ends.getInt(at), ends.getInt(at + 1));
		if(index < 0) { throw new IOException("edge " + at / 2 + " has nodes past " + NODE_LIMIT); }
		pairs.add((int) index);
		edges++;
	}
	if(pairs.getCardinality() != edges) { throw new IOException("two edges share a pair of nodes, which one index cannot hold"); }
	edgesInOrder.size(edges);
	for(int at = 0; at < ends.size(); at += 2) {
		if(ends.getInt(at) >= 0) { edgesInOrder.set(pairs.rank((int) indexOf(ends.getInt(at), ends.getInt(at + 1))) - 1, at / 2); }
	}
}

/** An index, a probe and a rank, then the direction checked against the edge's ends. */
@ACS_Requestable(15)
@Override
public int edgeOf(int node, int otherNode) {
	long index = indexOf(node, otherNode);
	if(index < 0 || !pairs.contains((int) index)) { return -1; }
	int edge = edgeAt((int) index);
	return directed && firstNodeOf(edge) != node ? -1 : edge;
}

/** The node's own run - its pairs with smaller nodes and itself - then one probe in each later run. */
@Override
IntArrayList edgesOf(int node) {
	IntArrayList held = new IntArrayList();
	if(node < 0 || node >= NODE_LIMIT || pairs.isEmpty()) { return held; }
	pairs.forEachInRange((int) runOf(node), node + 1, index -> held.add(edgeAt(index)));
	for(int larger = node + 1, last = largerOf(pairs.last()); larger <= last; larger++) {
		int index = (int) (runOf(larger) + node);
		if(pairs.contains(index)) { held.add(edgeAt(index)); }
	}
	return held;
}


/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal. */
public static SingleRoaringNodeEdgeComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	SingleRoaringNodeEdgeComponent graph = new SingleRoaringNodeEdgeComponent(core, journal, Rules.read(dis));
	graph.readEdges(dis);
	return graph;
}


}

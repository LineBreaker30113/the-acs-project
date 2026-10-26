package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.halim.acs.structure.UnaryRestrictions.*;
import static org.halim.acs.structure.BinaryRestrictions.*;


/**
 * The graph as one int list, two ints an edge: the nodes of edge {@code e} sit at {@code 2e} and
 * {@code 2e + 1}, the smaller first, so the edge's identity is where its pair is. Edges are the edge
 * arity's own identities, captured as its lowest free one when a connection is made, so they stay dense
 * and the list stays as long as the edges are many; a removed edge leaves {@code -1, -1} in its place, and
 * the next capture takes the place back.
 * <p>
 * The list is the whole of it, so the cost is the search: finding the edge of two nodes, a node's edges, or
 * whether two nodes are connected walks every edge. Edges are stored by their own place, never searched
 * for when made, so parallel edges cost nothing extra when allowed. Not multi thread safe.
 * */
public class PairListNodeEdgeComponent extends NodeEdgeBase {

/** The edge (edge, smaller node, larger node); the negative triple removes it. */
static final byte LINK = 1;

final ACS_Core core;
final ACS_Journal journal;
final int nodeArity, edgeArity;
final boolean allowsLoops, allowsParallelLinks;
final int[] params = new int[3];

/** Two ints an edge, {@code -1, -1} where there is none. */
final IntArrayList pairs = new IntArrayList();


public PairListNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean allowsLoops, boolean allowsParallelLinks) {
	if(nodeArity == edgeArity) { throw new IllegalArgumentException("nodes and edges in one arity " + nodeArity); }
	this.core = core;
	this.journal = journal;
	this.nodeArity = nodeArity;
	this.edgeArity = edgeArity;
	this.allowsLoops = allowsLoops;
	this.allowsParallelLinks = allowsParallelLinks;
}

/** Built by name through the core's addStructure: nodeArity, edgeArity, allowsLoops, allowsParallelLinks read from the request, in that order; nothing is written back. */
public PairListNodeEdgeComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readBoolean(), parameters.readBoolean());
}


@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> nodeArity;
		case 1 -> edgeArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of a graph over two");
	};
}

public boolean allowsLoops() { return allowsLoops; }

public boolean allowsParallelLinks() { return allowsParallelLinks; }


/** A walk of every edge. */
@Override
public int edgeOf(int node, int otherNode) {
	int smaller = Math.min(node, otherNode), larger = Math.max(node, otherNode);
	for(int at = 0; at < pairs.size(); at += 2) {
		if(pairs.getInt(at) == smaller && pairs.getInt(at + 1) == larger && smaller >= 0) { return at / 2; }
	}
	return -1;
}

@Override
public int firstNodeOf(int edge) { return edge >= 0 && 2 * edge < pairs.size() ? pairs.getInt(2 * edge) : -1; }

@Override
public int secondNodeOf(int edge) { return edge >= 0 && 2 * edge < pairs.size() ? pairs.getInt(2 * edge + 1) : -1; }

/** A walk of every edge. */
@Override
public int edgeCount(int node) { return edgeListOf(node).size(); }

/** A walk of every edge, so ascending. */
@Override
public void copyEdgesInto(int node, int[] dest, int offset) {
	IntArrayList held = edgeListOf(node);
	held.getElements(0, dest, offset, held.size());
}

IntArrayList edgeListOf(int node) {
	IntArrayList held = new IntArrayList();
	if(node < 0) { return held; }
	for(int at = 0; at < pairs.size(); at += 2) {
		if(pairs.getInt(at) == node || pairs.getInt(at + 1) == node) { held.add(at / 2); }
	}
	return held;
}


/** A new edge is the edge arity's lowest free identity, captured through the core, nested in this request so undo removes it with the connection. */
@Override
public int ensureEdge(int node, int otherNode, String comment) {
	requireLive(node);
	requireLive(otherNode);
	if(node == otherNode && !allowsLoops) { throw new IllegalArgumentException("no loops: " + node + " to itself"); }
	if(!allowsParallelLinks) {
		int held = edgeOf(node, otherNode);
		if(held >= 0) { return held; }
	}
	int request = journal.beginRecordingActivity(this, "ensureEdge", params, comment);
	int edge;
	try {
		edge = Math.toIntExact(core.captureIdentity(edgeArity, null));
		link(edge, Math.min(node, otherNode), Math.max(node, otherNode));
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return edge;
}

/** Every edge between the two, removed through the core, which forgets each here. */
@Override
public boolean breakEdges(int node, int otherNode, String comment) {
	int smaller = Math.min(node, otherNode), larger = Math.max(node, otherNode);
	IntArrayList edges = new IntArrayList();
	for(int at = 0; at < pairs.size(); at += 2) {
		if(smaller >= 0 && pairs.getInt(at) == smaller && pairs.getInt(at + 1) == larger) { edges.add(at / 2); }
	}
	if(edges.isEmpty()) { return false; }
	int request = journal.beginRecordingActivity(this, "breakEdges", params, comment);
	try { for(int i = 0; i < edges.size(); i++) { core.delegateRemoval(edgeArity, edges.getInt(i), null); } }
	catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

/** An edge leaving its arity clears its place; a node leaving its arity removes its edges through the core. */
@Override
public boolean forget(int arity, int identity) {
	if(arity == edgeArity) {
		if(firstNodeOf(identity) < 0) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", params, null);
		unlink(identity);
		complete(request);
		return true;
	}
	if(arity != nodeArity) { return false; }
	IntArrayList edges = edgeListOf(identity);
	if(edges.isEmpty()) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	for(int i = 0; i < edges.size(); i++) { core.delegateRemoval(edgeArity, edges.getInt(i), null); }
	complete(request);
	return true;
}


/** Puts the pair in the edge's place and records it. */
void link(int edge, int smaller, int larger) {
	linkUnchecked(edge, smaller, larger);
	params[0] = edge;
	params[1] = smaller;
	params[2] = larger;
	journal.recordActivityStep(LINK, 3);
}

void unlink(int edge) {
	params[0] = edge;
	params[1] = pairs.getInt(2 * edge);
	params[2] = pairs.getInt(2 * edge + 1);
	linkUnchecked(edge, -1, -1);
	journal.recordActivityStep((byte) -LINK, 3);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case LINK -> linkUnchecked(params[index], params[index + 1], params[index + 2]);
		case -LINK -> linkUnchecked(params[index], -1, -1);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

/** Grows the list to the edge's place, the places between left empty. */
void linkUnchecked(int edge, int smaller, int larger) {
	while(pairs.size() < 2 * edge + 2) { pairs.add(-1); }
	pairs.set(2 * edge, smaller);
	pairs.set(2 * edge + 1, larger);
}


/** The two arity slots and the two rules, then each edge as (edge, smaller, larger), so the file carries the graph and not the list's empty places. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(nodeArity);
	dos.writeInt(edgeArity);
	dos.writeBoolean(allowsLoops);
	dos.writeBoolean(allowsParallelLinks);
	int count = 0;
	for(int at = 0; at < pairs.size(); at += 2) { if(pairs.getInt(at) >= 0) { count++; } }
	dos.writeInt(count);
	for(int at = 0; at < pairs.size(); at += 2) {
		if(pairs.getInt(at) < 0) { continue; }
		dos.writeInt(at / 2);
		dos.writeInt(pairs.getInt(at));
		dos.writeInt(pairs.getInt(at + 1));
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses an edge written twice, a pair out of order, or one the rules forbid. */
public static PairListNodeEdgeComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int nodeArity = dis.readInt();
	int edgeArity = dis.readInt();
	boolean allowsLoops = dis.readBoolean();
	PairListNodeEdgeComponent graph = new PairListNodeEdgeComponent(core, journal, nodeArity, edgeArity, allowsLoops, dis.readBoolean());
	// The pairs met so far, so refusing a parallel edge is a lookup rather than a walk of the list.
	LongOpenHashSet met = new LongOpenHashSet();
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int edge = dis.readInt();
		int smaller = dis.readInt();
		int larger = dis.readInt();
		if(edge < 0 || smaller < 0 || smaller > larger) { throw new IOException("edge " + edge + " (" + smaller + ", " + larger + ") is not a stored edge"); }
		if(graph.firstNodeOf(edge) >= 0) { throw new IOException("edge " + edge + " is written twice"); }
		if(smaller == larger && !allowsLoops) { throw new IOException("loop on " + smaller + " in a graph without loops"); }
		if(!graph.allowsParallelLinks && !met.add((long) smaller << 32 | larger)) { throw new IOException("parallel edge " + edge + " in a graph without them"); }
		graph.linkUnchecked(edge, smaller, larger);
	}
	return graph;
}


void requireLive(int node) {
	if(node < 0 || !core.getArity(nodeArity).occupied().contains(node)) {
		throw new IllegalArgumentException(node + " is not an occupied node of arity " + nodeArity);
	}
}

/** Aborts nested requests left open by the failure too, up to and including {@code request}. */
void abort(int request) {
	while(journal.abortRecordingActivity() != request) { }
}

void complete(int request) {
	int completed = journal.completeRecordingActivity();
	if(completed != request) { throw new IllegalStateException("began request " + request + ", completed " + completed); }
}


/** Directionless, with the two rules as the graph's bits; no other restriction is kept here. */
public int getRestrictions() { return (allowsLoops ? 0 : NO_LOOPS) | (allowsParallelLinks ? 0 : NO_PARALLEL_LINKS); }

/** The edge arity is caused but not strictly: an edge it captures through the core is the client's arity's too. */
@Override
public boolean isCaused(int index) { return false; }


/** Whether the node is one of the edge's two. */
@Override
public boolean isRelatedToBinary(int node, int edge) { return node >= 0 && (firstNodeOf(edge) == node || secondNodeOf(edge) == node); }

/** False when the node is one of the edge's; refused otherwise, since an edge has exactly two nodes, both given when {@link #ensureEdge} makes it. */
@Override
public boolean relateToBinary(int node, int edge, String comment) {
	if(isRelatedToBinary(node, edge)) { return false; }
	throw new IllegalArgumentException("connecting node " + node + " to edge " + edge + " leaves the edge with other than two nodes; ensureEdge makes an edge with its two");
}

/** False when the node is not one of the edge's; refused otherwise, since the edge would keep one node: {@link #breakEdges} removes it whole. */
@Override
public boolean unrelateFromBinary(int node, int edge, String comment) {
	if(!isRelatedToBinary(node, edge)) { return false; }
	throw new IllegalArgumentException("breaking node " + node + " off edge " + edge + " leaves the edge with other than two nodes; breakEdges removes it");
}

/** False when the edge is already the node's one; refused otherwise, since the move leaves an edge with other than two nodes. */
@Override
public boolean setComplement(int node, int edge, String comment) {
	if(isRelatedToBinary(node, edge) && edgeCount(node) == 1) { return false; }
	throw new IllegalArgumentException("making edge " + edge + " node " + node + "'s one leaves an edge with other than two nodes");
}

/** False when the edge is a loop on the node, its one already; refused otherwise, since an edge keeping one node has other than two. */
@Override
public boolean setPrimary(int edge, int node, String comment) {
	if(node >= 0 && firstNodeOf(edge) == node && secondNodeOf(edge) == node) { return false; }
	throw new IllegalArgumentException("making node " + node + " edge " + edge + "'s one leaves the edge with other than two nodes");
}

@Override
public int complementCount(int node) { return edgeCount(node); }

/** Two, one for a loop, none when the edge is no connection here. */
@Override
public int primaryCount(int edge) {
	int first = firstNodeOf(edge);
	return first < 0 ? 0 : first == secondNodeOf(edge) ? 1 : 2;
}

/** Ascending, as {@link #copyEdgesInto} gives them. */
@Override
public void copyComplementsInto(int node, int[] dest, int offset) { copyEdgesInto(node, dest, offset); }

/** The first node, then the second unless the edge is a loop. */
@Override
public void copyPrimariesInto(int edge, int[] dest, int offset) {
	int count = primaryCount(edge);
	if(count > 0) { dest[offset] = firstNodeOf(edge); }
	if(count > 1) { dest[offset + 1] = secondNodeOf(edge); }
}



/** Every edge from the parent, as {@link #breakEdges} removes them. */
/**
 * The edges from every other predecessor removed, then one from {@code predecessor} made unless one is there, as one
 * request: the new edge is checked after the removals, so what breaks only midway is no refusal.
 * */
@Override
public boolean setPredecessor(int element, int predecessor, String comment) {
	requireLive(element);
	requireLive(predecessor);
	int[] predecessors = predecessorsOf(element);
	boolean held = Arrays.binarySearch(predecessors, predecessor) >= 0;
	if(held && predecessors.length == 1) { return false; }
	int request = journal.beginRecordingActivity(this, "setPredecessor", params, comment);
	try {
		for(int other : predecessors) { if(other != predecessor) { breakEdges(other, element, null); } }
		if(!held) { ensureEdge(predecessor, element, null); }
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

@Override
public int predecessorCount(int element) { return firstCount(element); }

@Override
public int successorCount(int element) { return lastCount(element); }

/** Ascending, each once. */
@Override
public void copyPredecessorsInto(int element, int[] dest, int offset) {
	int[] predecessors = predecessorsOf(element);
	System.arraycopy(predecessors, 0, dest, offset, predecessors.length);
}

/** Ascending, each once. */
@Override
public void copySuccessorsInto(int element, int[] dest, int offset) {
	int[] successors = successorsOf(element);
	System.arraycopy(successors, 0, dest, offset, successors.length);
}


public boolean reaches(int from, int to) {
	if(from == to) { return true; }
	IntArrayList stack = new IntArrayList();
	IntOpenHashSet seen = new IntOpenHashSet();
	stack.add(from);
	seen.add(from);
	while(!stack.isEmpty()) {
		for(int other : successorsOf(stack.popInt())) {
			if(other == to) { return true; }
			if(seen.add(other)) { stack.add(other); }
		}
	}
	return false;
}

public boolean relateToUnary(int first, int last, String comment) {
	if(!allowsParallelLinks && edgeOf(first, last) >= 0) { return false; }
	ensureEdge(first, last, comment);
	return true;
}

public boolean unrelateFromUnary(int first, int last, String comment) {
	int edge = edgeOf(first, last);
	if(edge < 0) { return false; }
	int request = journal.beginRecordingActivity(this, "unrelateFromUnary", params, comment);
	try { core.delegateRemoval(edgeArity, edge, null); }
	catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

public boolean isRelatedToUnary(int first, int last) { return edgeOf(first, last) >= 0; }

public int[] successorsOf(int first) { return across(first, false); }

public int[] predecessorsOf(int last) { return across(last, true); }

public int lastCount(int first) { return successorsOf(first).length; }

public int firstCount(int last) { return predecessorsOf(last).length; }

/** The other node of each of the node's edges, the graph being directionless; a walk of every edge. */
int[] across(int node, boolean up) {
	IntArrayList held = edgeListOf(node);
	IntOpenHashSet found = new IntOpenHashSet();
	for(int i = 0; i < held.size(); i++) {
		int edge = held.getInt(i), first = firstNodeOf(edge), second = secondNodeOf(edge);
		found.add(first == node ? second : first);
	}
	int[] nodes = found.toIntArray();
	Arrays.sort(nodes);
	return nodes;
}




/** The element's clusters, in the order {@link #copyComplementsInto} gives them, for the outside: no request fills the caller's array. */
@Override
public int[] getComplementsOf(int element) {
	int[] clusters = new int[complementCount(element)];
	copyComplementsInto(element, clusters, 0);
	return clusters;
}

/** The cluster's elements, in the order {@link #copyPrimariesInto} gives them. */
@Override
public int[] getPrimariesOf(int cluster) {
	int[] elements = new int[primaryCount(cluster)];
	copyPrimariesInto(cluster, elements, 0);
	return elements;
}



@Override
public boolean setSuccessors(int first, int[] lasts, String comment) { return setAcross(first, lasts, false, comment); }

/** What has an edge to {@code last} becomes {@code firsts}, as {@link #setSuccessors} sets it. */
@Override
public boolean setPredecessors(int last, int[] firsts, String comment) { return setAcross(last, firsts, true, comment); }

boolean setAcross(int node, int[] wanted, boolean up, String comment) {
	requireLive(node);
	IntOpenHashSet kept = new IntOpenHashSet(wanted);
	int[] held = across(node, up);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, up ? "setPredecessors" : "setSuccessors", params, comment);
	try {
		for(int other : held) {
			if(!kept.contains(other)) { breakEdges(up ? other : node, up ? node : other, null); }
		}
		for(int other : wanted) {
			if(had.add(other)) { ensureEdge(up ? other : node, up ? node : other, null); }
		}
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

/** False when {@code clusters} are the node's edges already; refused otherwise, as {@link #setComplement} is. */
@Override
public boolean setComplementsOf(int element, int[] clusters, String comment) {
	if(new IntOpenHashSet(clusters).equals(new IntOpenHashSet(getComplementsOf(element)))) { return false; }
	throw new IllegalArgumentException("giving node " + element + " other edges leaves an edge with other than two nodes");
}

/** False when {@code elements} are the edge's nodes already; refused otherwise, as {@link #setPrimary} is. */
@Override
public boolean setPrimariesOf(int cluster, int[] elements, String comment) {
	if(new IntOpenHashSet(elements).equals(new IntOpenHashSet(getPrimariesOf(cluster)))) { return false; }
	throw new IllegalArgumentException("giving edge " + cluster + " other nodes leaves it with other than two");
}



/**
 * The requests, by the codes {@link NodeEdgeBase} marks: each reads its inputs, calls the public method,
 * which keeps its checks, and writes the result. A read is recorded as a query: its method's name and
 * inputs, then its answer, or its refusal; a list is answered by its length.
 * */

@Override
public ACS_Journal getJournal() { return journal; }

}

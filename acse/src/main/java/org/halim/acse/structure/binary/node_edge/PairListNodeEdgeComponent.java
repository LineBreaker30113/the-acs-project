package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.arity.sets.IdentitiesSet;
import org.halim.acs.structure.binary.node_edge.ACS_NodeEdgeComponentBase;
import org.halim.acse.arity.sets.IdentitiesSetRoaring;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;


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
public class PairListNodeEdgeComponent implements ACS_NodeEdgeComponentBase {

/** The edge (edge, smaller node, larger node); the negative triple removes it. */
static final byte LINK = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = requestNames(PairListNodeEdgeComponent.class);

final ACS_Core core;
final ACS_Journal journal;
final int nodeArity, edgeArity;
final boolean allowsLoops, allowsParallelEdges;
final int[] params = new int[3];

/** Two ints an edge, {@code -1, -1} where there is none. */
final IntArrayList pairs = new IntArrayList();


public PairListNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean allowsLoops, boolean allowsParallelEdges) {
	if(nodeArity == edgeArity) { throw new IllegalArgumentException("nodes and edges in one arity " + nodeArity); }
	this.core = core;
	this.journal = journal;
	this.nodeArity = nodeArity;
	this.edgeArity = edgeArity;
	this.allowsLoops = allowsLoops;
	this.allowsParallelEdges = allowsParallelEdges;
}

/** Built by name through the core's addStructure: nodeArity, edgeArity, allowsLoops, allowsParallelEdges read from the request, in that order; nothing is written back. */
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

@Override
public boolean allowsLoops() { return allowsLoops; }

@Override
public boolean allowsParallelEdges() { return allowsParallelEdges; }


/** A walk of every edge. */
@ACS_Requestable(15)
@Override
public int edgeOf(int node, int otherNode) {
	int smaller = Math.min(node, otherNode), larger = Math.max(node, otherNode);
	for(int at = 0; at < pairs.size(); at += 2) {
		if(pairs.getInt(at) == smaller && pairs.getInt(at + 1) == larger && smaller >= 0) { return at / 2; }
	}
	return -1;
}

@ACS_Requestable(16)
@Override
public int firstNodeOf(int edge) { return edge >= 0 && 2 * edge < pairs.size() ? pairs.getInt(2 * edge) : -1; }

@ACS_Requestable(17)
@Override
public int secondNodeOf(int edge) { return edge >= 0 && 2 * edge < pairs.size() ? pairs.getInt(2 * edge + 1) : -1; }

/** A walk of every edge. */
@ACS_Requestable(18)
@Override
public int edgeCount(int node) { return edgesOf(node).size(); }

/** A walk of every edge, so ascending. */
@Override
public void copyEdgesInto(int node, int[] dest, int offset) {
	IntArrayList held = edgesOf(node);
	held.getElements(0, dest, offset, held.size());
}

IntArrayList edgesOf(int node) {
	IntArrayList held = new IntArrayList();
	if(node < 0) { return held; }
	for(int at = 0; at < pairs.size(); at += 2) {
		if(pairs.getInt(at) == node || pairs.getInt(at + 1) == node) { held.add(at / 2); }
	}
	return held;
}


/** A new edge is the edge arity's lowest free identity, captured through the core, nested in this request so undo removes it with the connection. */
@ACS_Requestable(13)
@Override
public int ensureEdge(int node, int otherNode, String comment) {
	requireLive(node);
	requireLive(otherNode);
	if(node == otherNode && !allowsLoops) { throw new IllegalArgumentException("no loops: " + node + " to itself"); }
	if(!allowsParallelEdges) {
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
@ACS_Requestable(14)
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
	IntArrayList edges = edgesOf(identity);
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
	dos.writeBoolean(allowsParallelEdges);
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
		if(!graph.allowsParallelEdges && !met.add((long) smaller << 32 | larger)) { throw new IOException("parallel edge " + edge + " in a graph without them"); }
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
@Override
public int getRestrictions() { return (allowsLoops ? 0 : NO_LOOPS) | (allowsParallelEdges ? 0 : NO_PARALLEL_EDGES); }


/** Whether the node is one of the edge's two. */
@ACS_Requestable(0)
@Override
public boolean isConnected(int node, int edge) { return node >= 0 && (firstNodeOf(edge) == node || secondNodeOf(edge) == node); }

/** False when the node is one of the edge's; refused otherwise, since an edge has exactly two nodes, both given when {@link #ensureEdge} makes it. */
@ACS_Requestable(1)
@Override
public boolean ensureConnection(int node, int edge, String comment) {
	if(isConnected(node, edge)) { return false; }
	throw new IllegalArgumentException("connecting node " + node + " to edge " + edge + " leaves the edge with other than two nodes; ensureEdge makes an edge with its two");
}

/** False when the node is not one of the edge's; refused otherwise, since the edge would keep one node: {@link #breakEdges} removes it whole. */
@ACS_Requestable(2)
@Override
public boolean breakConnection(int node, int edge, String comment) {
	if(!isConnected(node, edge)) { return false; }
	throw new IllegalArgumentException("breaking node " + node + " off edge " + edge + " leaves the edge with other than two nodes; breakEdges removes it");
}

/** False when the edge is already the node's one; refused otherwise, since the move leaves an edge with other than two nodes. */
@ACS_Requestable(3)
@Override
public boolean setComplement(int node, int edge, String comment) {
	if(isConnected(node, edge) && edgeCount(node) == 1) { return false; }
	throw new IllegalArgumentException("making edge " + edge + " node " + node + "'s one leaves an edge with other than two nodes");
}

/** False when the edge is a loop on the node, its one already; refused otherwise, since an edge keeping one node has other than two. */
@ACS_Requestable(4)
@Override
public boolean setPrimary(int edge, int node, String comment) {
	if(node >= 0 && firstNodeOf(edge) == node && secondNodeOf(edge) == node) { return false; }
	throw new IllegalArgumentException("making node " + node + " edge " + edge + "'s one leaves the edge with other than two nodes");
}

@ACS_Requestable(5)
@Override
public int complementCount(int node) { return edgeCount(node); }

/** Two, one for a loop, none when the edge is no connection here. */
@ACS_Requestable(6)
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


/** An edge from the parent, as {@link #ensureEdge} makes it, when none joins the two; false when one does. */
@ACS_Requestable(7)
@Override
public boolean addParent(int element, int parent, String comment) {
	requireLive(element);
	requireLive(parent);
	if(edgeOf(parent, element) >= 0) { return false; }
	ensureEdge(parent, element, comment);
	return true;
}

/** Every edge from the parent, as {@link #breakEdges} removes them. */
@ACS_Requestable(8)
@Override
public boolean removeParent(int element, int parent, String comment) { return breakEdges(parent, element, comment); }

/**
 * The edges from every other parent removed, then one from {@code parent} made unless one is there, as one
 * request: the new edge is checked after the removals, so what breaks only midway is no refusal.
 * */
@ACS_Requestable(9)
@Override
public boolean setParent(int element, int parent, String comment) {
	requireLive(element);
	requireLive(parent);
	int[] parents = firstsOf(element);
	boolean held = Arrays.binarySearch(parents, parent) >= 0;
	if(held && parents.length == 1) { return false; }
	int request = journal.beginRecordingActivity(this, "setParent", params, comment);
	try {
		for(int other : parents) { if(other != parent) { breakEdges(other, element, null); } }
		if(!held) { ensureEdge(parent, element, null); }
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

@ACS_Requestable(10)
@Override
public int parentCount(int element) { return firstCount(element); }

@ACS_Requestable(11)
@Override
public int childCount(int element) { return lastCount(element); }

/** Ascending, each once. */
@Override
public void copyParentsInto(int element, int[] dest, int offset) {
	int[] parents = firstsOf(element);
	System.arraycopy(parents, 0, dest, offset, parents.length);
}

/** Ascending, each once. */
@Override
public void copyChildrenInto(int element, int[] dest, int offset) {
	int[] children = lastsOf(element);
	System.arraycopy(children, 0, dest, offset, children.length);
}


/** A walk with a seen set along every edge, the graph being directionless. */
@ACS_Requestable(12)
@Override
public boolean reaches(int from, int to) {
	if(from == to) { return true; }
	IntArrayList stack = new IntArrayList();
	IntOpenHashSet seen = new IntOpenHashSet();
	stack.add(from);
	seen.add(from);
	while(!stack.isEmpty()) {
		for(int other : lastsOf(stack.popInt())) {
			if(other == to) { return true; }
			if(seen.add(other)) { stack.add(other); }
		}
	}
	return false;
}


/** One more edge from {@code first} to {@code last}, as {@link #ensureEdge} makes it; false when, without parallel edges, one is there already. */
@ACS_Requestable(19)
@Override
public boolean connectTo(int first, int last, String comment) {
	if(!allowsParallelEdges && edgeOf(first, last) >= 0) { return false; }
	ensureEdge(first, last, comment);
	return true;
}

/** One edge fewer from {@code first} to {@code last}, the lowest, removed through the core; false when there is none. */
@ACS_Requestable(20)
@Override
public boolean disconnectFrom(int first, int last, String comment) {
	int edge = edgeOf(first, last);
	if(edge < 0) { return false; }
	int request = journal.beginRecordingActivity(this, "disconnectFrom", params, comment);
	try { core.delegateRemoval(edgeArity, edge, null); }
	catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

@ACS_Requestable(21)
@Override
public boolean isConnectedTo(int first, int last) { return edgeOf(first, last) >= 0; }

/** The nodes {@code first} has an edge to, each once and ascending; on a directionless graph, every node it shares an edge with. */
@ACS_Requestable(22)
@Override
public int[] lastsOf(int first) { return across(first, false); }

/** The nodes with an edge to {@code last}, each once and ascending; on a directionless graph, as {@link #lastsOf}. */
@ACS_Requestable(23)
@Override
public int[] firstsOf(int last) { return across(last, true); }

@ACS_Requestable(24)
@Override
public int lastCount(int first) { return lastsOf(first).length; }

@ACS_Requestable(25)
@Override
public int firstCount(int last) { return firstsOf(last).length; }

/** The other node of each of the node's edges, the graph being directionless; a walk of every edge. */
int[] across(int node, boolean up) {
	IntArrayList held = edgesOf(node);
	IntOpenHashSet found = new IntOpenHashSet();
	for(int i = 0; i < held.size(); i++) {
		int edge = held.getInt(i), first = firstNodeOf(edge), second = secondNodeOf(edge);
		found.add(first == node ? second : first);
	}
	int[] nodes = found.toIntArray();
	Arrays.sort(nodes);
	return nodes;
}

/** {@link #ensureConnection}, as elements-clusters names it: the element is a node, the cluster an edge. */
@ACS_Requestable(26)
@Override
public boolean cluster(int element, int cluster, String comment) { return ensureConnection(element, cluster, comment); }

/** {@link #breakConnection}, as elements-clusters names it. */
@ACS_Requestable(27)
@Override
public boolean uncluster(int element, int cluster, String comment) { return breakConnection(element, cluster, comment); }

/** {@link #isConnected}, as elements-clusters names it. */
@ACS_Requestable(28)
@Override
public boolean isClustered(int element, int cluster) { return isConnected(element, cluster); }

/** The element's clusters, in the order {@link #copyComplementsInto} gives them, for the outside: no request fills the caller's array. */
@ACS_Requestable(29)
@Override
public int[] clustersOf(int element) {
	int[] clusters = new int[complementCount(element)];
	copyComplementsInto(element, clusters, 0);
	return clusters;
}

/** The cluster's elements, in the order {@link #copyPrimariesInto} gives them. */
@ACS_Requestable(30)
@Override
public int[] elementsOf(int cluster) {
	int[] elements = new int[primaryCount(cluster)];
	copyPrimariesInto(cluster, elements, 0);
	return elements;
}

@ACS_Requestable(31)
@Override
public int clusterCount(int element) { return complementCount(element); }

@ACS_Requestable(32)
@Override
public int elementCount(int cluster) { return primaryCount(cluster); }


/**
 * What {@code first} has an edge to becomes {@code lasts}, a repeat counting once: the edges to every other
 * node removed through the core, then one made to each new one, as one request, so each new edge is
 * checked after the removals and nothing is refused for what breaks only midway. False when nothing changes.
 * */
@ACS_Requestable(33)
@Override
public boolean setLasts(int first, int[] lasts, String comment) { return setAcross(first, lasts, false, comment); }

/** What has an edge to {@code last} becomes {@code firsts}, as {@link #setLasts} sets it. */
@ACS_Requestable(34)
@Override
public boolean setFirsts(int last, int[] firsts, String comment) { return setAcross(last, firsts, true, comment); }

boolean setAcross(int node, int[] wanted, boolean up, String comment) {
	requireLive(node);
	IntOpenHashSet kept = new IntOpenHashSet(wanted);
	int[] held = across(node, up);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, up ? "setFirsts" : "setLasts", params, comment);
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
@ACS_Requestable(35)
@Override
public boolean setClusters(int element, int[] clusters, String comment) {
	if(new IntOpenHashSet(clusters).equals(new IntOpenHashSet(clustersOf(element)))) { return false; }
	throw new IllegalArgumentException("giving node " + element + " other edges leaves an edge with other than two nodes");
}

/** False when {@code elements} are the edge's nodes already; refused otherwise, as {@link #setPrimary} is. */
@ACS_Requestable(36)
@Override
public boolean setElements(int cluster, int[] elements, String comment) {
	if(new IntOpenHashSet(elements).equals(new IntOpenHashSet(elementsOf(cluster)))) { return false; }
	throw new IllegalArgumentException("giving edge " + cluster + " other nodes leaves it with other than two");
}


/** {@link #depth}, marked here so it is a request. */
@ACS_Requestable(37)
@Override
public int depth(int element) { return ACS_NodeEdgeComponentBase.super.depth(element); }

/** {@link #distance}, marked here so it is a request. */
@ACS_Requestable(38)
@Override
public int distance(int element, int other) { return ACS_NodeEdgeComponentBase.super.distance(element, other); }

/** {@link #pathLength}, marked here so it is a request. */
@ACS_Requestable(39)
@Override
public int pathLength(int from, int to) { return ACS_NodeEdgeComponentBase.super.pathLength(from, to); }

/** {@link #writeAncestors} as an int[], for the outside: no request takes a stream. */
@ACS_Requestable(40)
@Override
public int[] ancestorsOf(int element) { return listed(dos -> writeAncestors(element, dos)); }

/** {@link #writeDescendants} as an int[]. */
@ACS_Requestable(41)
@Override
public int[] descendantsOf(int element) { return listed(dos -> writeDescendants(element, dos)); }

/** {@link #writeNearestCommonAncestors} as an int[]. */
@ACS_Requestable(42)
@Override
public int[] nearestCommonAncestorsOf(int element, int other) { return listed(dos -> writeNearestCommonAncestors(element, other, dos)); }

/** {@link #writeNearestCommonDescendants} as an int[]. */
@ACS_Requestable(43)
@Override
public int[] nearestCommonDescendantsOf(int element, int other) { return listed(dos -> writeNearestCommonDescendants(element, other, dos)); }

/** {@link #writeTopologicalOrder} over the graph {@code elements} make, given as ints since no request takes a set. */
@ACS_Requestable(44)
@Override
public int[] topologicalOrderAmong(int[] elements) { return listed(dos -> writeTopologicalOrder(setOf(elements), dos)); }

/** {@link #hasCycle} among {@code elements}. */
@ACS_Requestable(45)
@Override
public boolean hasCycleAmong(int[] elements) { return hasCycle(setOf(elements)); }

/** {@link #writeRoots} among {@code elements}, as an int[]. */
@ACS_Requestable(46)
@Override
public int[] rootsAmong(int[] elements) { return listed(dos -> writeRoots(setOf(elements), dos)); }

/** {@link #writeLeaves} among {@code elements}, as an int[]. */
@ACS_Requestable(47)
@Override
public int[] leavesAmong(int[] elements) { return listed(dos -> writeLeaves(setOf(elements), dos)); }

/** {@link #writeParts} among {@code elements}, as one int[] in its layout: the part count, then each part's size and elements. */
@ACS_Requestable(48)
@Override
public int[] partsAmong(int[] elements) { return written(dos -> writeParts(setOf(elements), dos)); }

/** {@link #connectivity} among {@code elements}. */
@ACS_Requestable(49)
@Override
public double connectivityAmong(int[] elements) { return connectivity(setOf(elements)); }

/** {@link #reachesAll} among {@code elements}. */
@ACS_Requestable(50)
@Override
public boolean reachesAllAmong(int[] elements) { return reachesAll(setOf(elements)); }

/** {@link #connectsAll} among {@code elements}. */
@ACS_Requestable(51)
@Override
public boolean connectsAllAmong(int[] elements) { return connectsAll(setOf(elements)); }

/** What an analysis writes to a stream. */
interface Writing { void to(DataOutputStream dos) throws IOException; }

/** Every int the analysis writes, in order. */
static int[] written(Writing writing) {
	ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	try { writing.to(new DataOutputStream(bytes)); }
	catch(IOException e) { throw new UncheckedIOException(e); }
	IntBuffer ints = ByteBuffer.wrap(bytes.toByteArray()).asIntBuffer();
	int[] all = new int[ints.remaining()];
	ints.get(all);
	return all;
}

/** The elements of the one int[] the analysis writes, its length left out. */
static int[] listed(Writing writing) {
	int[] all = written(writing);
	return Arrays.copyOfRange(all, 1, all.length);
}

/** The ints as a set, which the analyses take; an identity is registered once however often given. */
static IdentitiesSet setOf(int[] elements) {
	IdentitiesSetRoaring set = new IdentitiesSetRoaring();
	for(int element : elements) { set.register(element); }
	return set;
}

/**
 * The requests, by code in the order of the marks: each reads its inputs, calls the public method, which
 * keeps its checks, and writes the result. A read is recorded as a query: its method's name and inputs,
 * then its answer, or its refusal; a list is answered by its length.
 * */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	String query = null;
	try {
		switch(code) {
			case 0, 12, 15, 21, 28 -> {
				int identity = parameters.readInt(), other = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ", " + other + ")";
				if(code == 15) {
					int value = edgeOf(identity, other);
					recordQuery(query, Integer.toString(value));
					returns.writeInt(value);
				} else {
					boolean value = switch(code) {
						case 0 -> isConnected(identity, other);
						case 12 -> reaches(identity, other);
						case 21 -> isConnectedTo(identity, other);
						default -> isClustered(identity, other);
					};
					recordQuery(query, Boolean.toString(value));
					returns.writeBoolean(value);
				}
			}
			case 1 -> returns.writeBoolean(ensureConnection(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 2 -> returns.writeBoolean(breakConnection(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 3 -> returns.writeBoolean(setComplement(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 4 -> returns.writeBoolean(setPrimary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 7 -> returns.writeBoolean(addParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 8 -> returns.writeBoolean(removeParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 9 -> returns.writeBoolean(setParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 13 -> returns.writeInt(ensureEdge(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 14 -> returns.writeBoolean(breakEdges(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 19 -> returns.writeBoolean(connectTo(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 20 -> returns.writeBoolean(disconnectFrom(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 26 -> returns.writeBoolean(cluster(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 27 -> returns.writeBoolean(uncluster(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 5, 6, 10, 11, 16, 17, 18, 24, 25, 31, 32 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int value = switch(code) {
					case 5 -> complementCount(identity);
					case 6 -> primaryCount(identity);
					case 10 -> parentCount(identity);
					case 11 -> childCount(identity);
					case 16 -> firstNodeOf(identity);
					case 17 -> secondNodeOf(identity);
					case 18 -> edgeCount(identity);
					case 24 -> lastCount(identity);
					case 25 -> firstCount(identity);
					case 31 -> clusterCount(identity);
					default -> elementCount(identity);
				};
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 22, 23, 29, 30 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int[] value = switch(code) {
					case 22 -> lastsOf(identity);
					case 23 -> firstsOf(identity);
					case 29 -> clustersOf(identity);
					default -> elementsOf(identity);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 33 -> returns.writeBoolean(setLasts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 34 -> returns.writeBoolean(setFirsts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 35 -> returns.writeBoolean(setClusters(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 36 -> returns.writeBoolean(setElements(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 37, 38, 39 -> {
				int identity = parameters.readInt(), other = code == 37 ? 0 : parameters.readInt();
				query = REQUESTS[code] + "(" + identity + (code == 37 ? "" : ", " + other) + ")";
				int value = switch(code) {
					case 37 -> depth(identity);
					case 38 -> distance(identity, other);
					default -> pathLength(identity, other);
				};
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 40, 41, 42, 43 -> {
				int identity = parameters.readInt(), other = code < 42 ? 0 : parameters.readInt();
				query = REQUESTS[code] + "(" + identity + (code < 42 ? "" : ", " + other) + ")";
				int[] value = switch(code) {
					case 40 -> ancestorsOf(identity);
					case 41 -> descendantsOf(identity);
					case 42 -> nearestCommonAncestorsOf(identity, other);
					default -> nearestCommonDescendantsOf(identity, other);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 44, 46, 47, 48 -> {
				int[] elements = readInts(parameters);
				query = REQUESTS[code] + "(int[" + elements.length + "])";
				int[] value = switch(code) {
					case 44 -> topologicalOrderAmong(elements);
					case 46 -> rootsAmong(elements);
					case 47 -> leavesAmong(elements);
					default -> partsAmong(elements);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 45, 50, 51 -> {
				int[] elements = readInts(parameters);
				query = REQUESTS[code] + "(int[" + elements.length + "])";
				boolean value = switch(code) {
					case 45 -> hasCycleAmong(elements);
					case 50 -> reachesAllAmong(elements);
					default -> connectsAllAmong(elements);
				};
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 49 -> {
				int[] elements = readInts(parameters);
				query = REQUESTS[code] + "(int[" + elements.length + "])";
				double value = connectivityAmong(elements);
				recordQuery(query, Double.toString(value));
				returns.writeDouble(value);
			}
			default -> throw new IllegalArgumentException(getClass().getName() + " has no request " + code);
		}
	} catch(RuntimeException e) {
		if(query != null) { recordQuery(query, "refused: " + reason(e)); }
		ACS_Actor.writeString(returns, reason(e));
		return false;
	}
	return true;
}

void recordQuery(String request, String response) {
	journal.recordQueryRequest(request);
	journal.recordQueryResponse(response);
}

/** What a refused request writes back: the error's message, or its class when it has none. */
static String reason(RuntimeException e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getName(); }

/** The names of the class's requests by their codes, from the marks, so no name is a literal. */
static String[] requestNames(Class<?> actor) {
	ArrayList<String> names = new ArrayList<>();
	for(Method method : actor.getMethods()) {
		ACS_Requestable mark = method.getAnnotation(ACS_Requestable.class);
		if(mark == null || method.isBridge()) { continue; }
		while(names.size() <= mark.value()) { names.add(null); }
		names.set(mark.value(), method.getName());
	}
	return names.toArray(new String[0]);
}

/** An int[] on the wire: its length, then its elements. */
static void writeInts(DataOutputStream dos, int[] ints) throws IOException {
	dos.writeInt(ints.length);
	for(int value : ints) { dos.writeInt(value); }
}

/** An int[] as {@link #writeInts} writes it, its elements taken as they arrive rather than the length allocated first, so a garbled length ends in an EOFException. */
static int[] readInts(DataInputStream dis) throws IOException {
	int length = dis.readInt();
	if(length < 0) { throw new IOException("int[] length " + length); }
	IntArrayList ints = new IntArrayList();
	for(int i = 0; i < length; i++) { ints.add(dis.readInt()); }
	return ints.toIntArray();
}


}

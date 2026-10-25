package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.arity.sets.IdentitiesSet;
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

import static org.halim.acse.structure.UnaryRestrictions.*;
import static org.halim.acse.structure.BinaryRestrictions.*;


/**
 * What every stored form of the node-edge graph shares, so that each rule is written once and holds on
 * every storage: the rules themselves, checked when an edge is made; the edge → nodes side, two ints an
 * edge, which every form keeps because it costs eight bytes an edge and turns every question about an edge
 * into a lookup; and the core's part - capturing an edge, removing one, forgetting a node - with the
 * journal steps. A subclass is a storage: it keeps only the node → edges side, through the hooks below,
 * the way a chunked catalog keeps only its array hooks over {@code ChunkedCatalogBase}.
 * <p>
 * An edge is recorded as one step, {@link #LINK}, holding the edge and its two nodes, and the hooks are
 * called below every check: the request that recorded the step already passed them, so undo and redo
 * replay it as it is. The acyclic rule walks the graph before an edge is made - along edges going out on a
 * directed graph, along every edge on a directionless one - so it costs what the storage's node side costs
 * to walk. Not multi thread safe.
 * */
public abstract class NodeEdgeGraphBase extends NodeEdgeBase {

/** The edge (edge, first node, second node); the negative triple removes it. */
static final byte LINK = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = ACS_Actor.requestNames(NodeEdgeGraphBase.class);

final ACS_Core core;
final ACS_Journal journal;
final int nodeArity, edgeArity;
final boolean directed, acyclic, allowsLoops, allowsParallelLinks;
final int[] params = new int[3];

/** Two ints an edge, its first node then its second; {@code -1, -1} where there is none. */
final IntArrayList ends = new IntArrayList();


protected NodeEdgeGraphBase(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean directed, boolean acyclic, boolean allowsLoops, boolean allowsParallelLinks) {
	if(nodeArity == edgeArity) { throw new IllegalArgumentException("nodes and edges in one arity " + nodeArity); }
	if(acyclic && allowsLoops) { throw new IllegalArgumentException("an acyclic graph has no loops: a loop is a cycle"); }
	this.core = core;
	this.journal = journal;
	this.nodeArity = nodeArity;
	this.edgeArity = edgeArity;
	this.directed = directed;
	this.acyclic = acyclic;
	this.allowsLoops = allowsLoops;
	this.allowsParallelLinks = allowsParallelLinks;
}

NodeEdgeGraphBase(ACS_Core core, ACS_Journal journal, Rules rules) {
	this(core, journal, rules.nodeArity, rules.edgeArity, rules.directed, rules.acyclic, rules.allowsLoops, rules.allowsParallelLinks);
}


/** Puts the edge on its nodes in the storage; its ends are already in place. */
abstract void index(int edge, int first, int second);

/** Takes the edge off its nodes in the storage; its ends are still in place. */
abstract void unindex(int edge, int first, int second);

/** The node's edges, in any order, as a list of the caller's own. */
abstract IntArrayList edgeListOf(int node);

/** Refuses, before anything is recorded, an edge this storage cannot hold. */
void checkIndexable(int first, int second) { }

/** Builds the node side from the ends after a load; the default puts each edge on its nodes. */
void rebuildIndex() throws IOException {
	for(int at = 0; at < ends.size(); at += 2) { if(ends.getInt(at) >= 0) { index(at / 2, ends.getInt(at), ends.getInt(at + 1)); } }
}

/** The storage's own settings, written after the rules, which its loadFrom reads back. */
void writeSettings(DataOutputStream dos) throws IOException { }


@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> nodeArity;
		case 1 -> edgeArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of a graph over two");
	};
}

@Override
public boolean isDirected() { return directed; }

public boolean isAcyclic() { return acyclic; }

public boolean allowsLoops() { return allowsLoops; }

public boolean allowsParallelLinks() { return allowsParallelLinks; }


@Override
public int firstNodeOf(int edge) { return edge >= 0 && 2L * edge < ends.size() ? ends.getInt(2 * edge) : -1; }

@Override
public int secondNodeOf(int edge) { return edge >= 0 && 2L * edge < ends.size() ? ends.getInt(2 * edge + 1) : -1; }

/** The two nodes in the order an edge keeps them: as given on a directed graph, the smaller first on a directionless one. */
int firstOf(int node, int otherNode) { return directed ? node : Math.min(node, otherNode); }
int secondOf(int node, int otherNode) { return directed ? otherNode : Math.max(node, otherNode); }

/** A search of the first node's own edges. */
@Override
public int edgeOf(int node, int otherNode) {
	if(node < 0 || otherNode < 0) { return -1; }
	int first = firstOf(node, otherNode), second = secondOf(node, otherNode);
	IntArrayList held = edgeListOf(first);
	int found = -1;
	for(int i = 0; i < held.size(); i++) {
		int edge = held.getInt(i);
		if(firstNodeOf(edge) == first && secondNodeOf(edge) == second && (found < 0 || edge < found)) { found = edge; }
	}
	return found;
}

@Override
public int edgeCount(int node) { return edgeListOf(node).size(); }

@Override
public void copyEdgesInto(int node, int[] dest, int offset) {
	IntArrayList held = edgeListOf(node);
	IntArrays.quickSort(held.elements(), 0, held.size());
	held.getElements(0, dest, offset, held.size());
}

/**
 * Whether a path leads from {@code start} to {@code target}: along edges going out on a directed graph,
 * along every edge on a directionless one. A walk with a seen set, since a graph can hold many paths
 * between two nodes and a walk that forgets where it has been takes them all.
 * */
public boolean reaches(int start, int target) {
	if(start == target) { return true; }
	IntArrayList stack = new IntArrayList();
	IntOpenHashSet seen = new IntOpenHashSet();
	stack.add(start);
	seen.add(start);
	while(!stack.isEmpty()) {
		int node = stack.popInt();
		IntArrayList held = edgeListOf(node);
		for(int i = 0; i < held.size(); i++) {
			int edge = held.getInt(i), first = firstNodeOf(edge);
			if(directed && first != node) { continue; }
			int other = first == node ? secondNodeOf(edge) : first;
			if(other == target) { return true; }
			if(seen.add(other)) { stack.add(other); }
		}
	}
	return false;
}


/** Every rule is checked before anything is recorded; the edge is the edge arity's lowest free identity, captured nested in this request. */
@Override
public int ensureEdge(int node, int otherNode, String comment) {
	requireLive(node);
	requireLive(otherNode);
	if(node == otherNode && !allowsLoops) { throw new IllegalArgumentException("no loops: " + node + " to itself"); }
	int first = firstOf(node, otherNode), second = secondOf(node, otherNode);
	if(!allowsParallelLinks) {
		int held = edgeOf(first, second);
		if(held >= 0) { return held; }
	}
	if(acyclic && reaches(second, first)) { throw new IllegalArgumentException("an edge from " + first + " to " + second + " closes a cycle"); }
	checkIndexable(first, second);
	int request = journal.beginRecordingActivity(this, "ensureEdge", params, comment);
	int edge;
	try {
		edge = Math.toIntExact(core.captureIdentity(edgeArity, null));
		link(edge, first, second);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return edge;
}

/** Every such edge, removed through the core, which forgets each here. */
@Override
public boolean breakEdges(int node, int otherNode, String comment) {
	if(node < 0 || otherNode < 0) { return false; }
	int first = firstOf(node, otherNode), second = secondOf(node, otherNode);
	IntArrayList held = edgeListOf(first), edges = new IntArrayList();
	for(int i = 0; i < held.size(); i++) {
		int edge = held.getInt(i);
		if(firstNodeOf(edge) == first && secondNodeOf(edge) == second) { edges.add(edge); }
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

/** An edge leaving its arity is taken off its nodes; a node leaving its arity removes its edges through the core. */
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


/** Puts the edge in place and records it. */
void link(int edge, int first, int second) {
	linkUnchecked(edge, first, second);
	params[0] = edge;
	params[1] = first;
	params[2] = second;
	journal.recordActivityStep(LINK, 3);
}

void unlink(int edge) {
	params[0] = edge;
	params[1] = firstNodeOf(edge);
	params[2] = secondNodeOf(edge);
	unlinkUnchecked(edge);
	journal.recordActivityStep((byte) -LINK, 3);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case LINK -> linkUnchecked(params[index], params[index + 1], params[index + 2]);
		case -LINK -> unlinkUnchecked(params[index]);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

/** The ends first, then the storage, which may read them. The places up to the edge's are grown empty. */
void linkUnchecked(int edge, int first, int second) {
	while(ends.size() < 2 * edge + 2) { ends.add(-1); }
	ends.set(2 * edge, first);
	ends.set(2 * edge + 1, second);
	index(edge, first, second);
}

void unlinkUnchecked(int edge) {
	unindex(edge, firstNodeOf(edge), secondNodeOf(edge));
	ends.set(2 * edge, -1);
	ends.set(2 * edge + 1, -1);
}


/** The arity slots and the rules, the storage's settings, then each edge as (edge, first, second), so the file carries the graph and not a storage's layout. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	new Rules(nodeArity, edgeArity, directed, acyclic, allowsLoops, allowsParallelLinks).write(dos);
	writeSettings(dos);
	int count = 0;
	for(int at = 0; at < ends.size(); at += 2) { if(ends.getInt(at) >= 0) { count++; } }
	dos.writeInt(count);
	for(int at = 0; at < ends.size(); at += 2) {
		if(ends.getInt(at) < 0) { continue; }
		dos.writeInt(at / 2);
		dos.writeInt(ends.getInt(at));
		dos.writeInt(ends.getInt(at + 1));
	}
	return true;
}

/**
 * The edges {@link #saveTo} wrote, after the rules and the settings a subclass's loadFrom read to build
 * itself, then the node side built once from all of them. Refuses an edge written twice, a pair out of
 * order on a directionless graph, and one the loop or parallel rule forbids.
 * */
void readEdges(DataInputStream dis) throws IOException {
	LongOpenHashSet met = new LongOpenHashSet();
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int edge = dis.readInt(), first = dis.readInt(), second = dis.readInt();
		if(edge < 0 || first < 0 || second < 0 || (!directed && first > second)) { throw new IOException("edge " + edge + " (" + first + ", " + second + ") is not a stored edge"); }
		if(firstNodeOf(edge) >= 0) { throw new IOException("edge " + edge + " is written twice"); }
		if(first == second && !allowsLoops) { throw new IOException("loop on " + first + " in a graph without loops"); }
		if(!allowsParallelLinks && !met.add((long) first << 32 | second)) { throw new IOException("parallel edge " + edge + " in a graph without them"); }
		while(ends.size() < 2 * edge + 2) { ends.add(-1); }
		ends.set(2 * edge, first);
		ends.set(2 * edge + 1, second);
	}
	rebuildIndex();
}

/** The arity slots and the four rules, as a store writes them and a loadFrom reads them back before its own settings. */
static final class Rules {
	final int nodeArity, edgeArity;
	final boolean directed, acyclic, allowsLoops, allowsParallelLinks;

	Rules(int nodeArity, int edgeArity, boolean directed, boolean acyclic, boolean allowsLoops, boolean allowsParallelLinks) {
		this.nodeArity = nodeArity;
		this.edgeArity = edgeArity;
		this.directed = directed;
		this.acyclic = acyclic;
		this.allowsLoops = allowsLoops;
		this.allowsParallelLinks = allowsParallelLinks;
	}

	void write(DataOutputStream dos) throws IOException {
		dos.writeInt(nodeArity);
		dos.writeInt(edgeArity);
		dos.writeBoolean(directed);
		dos.writeBoolean(acyclic);
		dos.writeBoolean(allowsLoops);
		dos.writeBoolean(allowsParallelLinks);
	}

	static Rules read(DataInputStream dis) throws IOException {
		int nodeArity = dis.readInt(), edgeArity = dis.readInt();
		boolean directed = dis.readBoolean(), acyclic = dis.readBoolean(), allowsLoops = dis.readBoolean();
		return new Rules(nodeArity, edgeArity, directed, acyclic, allowsLoops, dis.readBoolean());
	}
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


/** The four rules as the graph's bits; no other restriction is kept here. */
public int getRestrictions() { return (directed ? DIRECTED : 0) | (acyclic ? ACYCLIC : 0) | (allowsLoops ? 0 : NO_LOOPS) | (allowsParallelLinks ? 0 : NO_PARALLEL_LINKS); }

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


/** An edge from the parent, as {@link #ensureEdge} makes it, when none joins the two; false when one does. */
@Override
public boolean addParent(int element, int parent, String comment) {
	requireLive(element);
	requireLive(parent);
	if(edgeOf(parent, element) >= 0) { return false; }
	ensureEdge(parent, element, comment);
	return true;
}

/** Every edge from the parent, as {@link #breakEdges} removes them. */
@Override
public boolean removeParent(int element, int parent, String comment) { return breakEdges(parent, element, comment); }

/**
 * The edges from every other parent removed, then one from {@code parent} made unless one is there, as one
 * request: the new edge is checked after the removals, so what breaks only midway is no refusal.
 * */
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

@Override
public int parentCount(int element) { return firstCount(element); }

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


/** One more edge from {@code first} to {@code last}, as {@link #ensureEdge} makes it; false when, without parallel edges, one is there already. */
@Override
public boolean relateToUnary(int first, int last, String comment) {
	if(!allowsParallelLinks && edgeOf(first, last) >= 0) { return false; }
	ensureEdge(first, last, comment);
	return true;
}

/** One edge fewer from {@code first} to {@code last}, the lowest, removed through the core; false when there is none. */
@Override
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

@Override
public boolean isRelatedToUnary(int first, int last) { return edgeOf(first, last) >= 0; }

/** The nodes {@code first} has an edge to, each once and ascending; on a directionless graph, every node it shares an edge with. */
@Override
public int[] lastsOf(int first) { return across(first, false); }

/** The nodes with an edge to {@code last}, each once and ascending; on a directionless graph, as {@link #lastsOf}. */
@Override
public int[] firstsOf(int last) { return across(last, true); }

public int lastCount(int first) { return lastsOf(first).length; }

public int firstCount(int last) { return firstsOf(last).length; }

/** The other node of each of the node's edges, those ending at it when {@code up} on a directed graph, those starting at it otherwise. */
int[] across(int node, boolean up) {
	IntArrayList held = edgeListOf(node);
	IntOpenHashSet found = new IntOpenHashSet();
	for(int i = 0; i < held.size(); i++) {
		int edge = held.getInt(i), first = firstNodeOf(edge), second = secondNodeOf(edge);
		if(!directed) { found.add(first == node ? second : first); }
		else if(up ? second == node : first == node) { found.add(up ? first : second); }
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



/**
 * What {@code first} has an edge to becomes {@code lasts}, a repeat counting once: the edges to every other
 * node removed through the core, then one made to each new one, as one request, so each new edge is
 * checked after the removals and nothing is refused for what breaks only midway. False when nothing changes.
 * */
@Override
public boolean setLasts(int first, int[] lasts, String comment) { return setAcross(first, lasts, false, comment); }

/** What has an edge to {@code last} becomes {@code firsts}, as {@link #setLasts} sets it. */
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


/** {@link #depth}, marked here so it is a request. */
@Override
public int depth(int element) { return super.depth(element); }

/** {@link #distance}, marked here so it is a request. */
@Override
public int distance(int element, int other) { return super.distance(element, other); }

/** {@link #pathLength}, marked here so it is a request. */
@Override
public int pathLength(int from, int to) { return super.pathLength(from, to); }

/** {@link #writeAncestors} as an int[], for the outside: no request takes a stream. */
@Override
public int[] ancestorsOf(int element) { return listed(dos -> writeAncestors(element, dos)); }

/** {@link #writeDescendants} as an int[]. */
@Override
public int[] descendantsOf(int element) { return listed(dos -> writeDescendants(element, dos)); }

/** {@link #writeNearestCommonAncestors} as an int[]. */
@Override
public int[] nearestCommonAncestorsOf(int element, int other) { return listed(dos -> writeNearestCommonAncestors(element, other, dos)); }

/** {@link #writeNearestCommonDescendants} as an int[]. */
@Override
public int[] nearestCommonDescendantsOf(int element, int other) { return listed(dos -> writeNearestCommonDescendants(element, other, dos)); }

/** {@link #writeTopologicalOrder} over the graph {@code elements} make, given as ints since no request takes a set. */
@Override
public int[] topologicalOrderAmong(int[] elements) { return listed(dos -> writeTopologicalOrder(setOf(elements), dos)); }

/** {@link #hasCycle} among {@code elements}. */
@Override
public boolean hasCycleAmong(int[] elements) { return hasCycle(setOf(elements)); }

/** {@link #writeRoots} among {@code elements}, as an int[]. */
@Override
public int[] rootsAmong(int[] elements) { return listed(dos -> writeRoots(setOf(elements), dos)); }

/** {@link #writeLeaves} among {@code elements}, as an int[]. */
@Override
public int[] leavesAmong(int[] elements) { return listed(dos -> writeLeaves(setOf(elements), dos)); }

/** {@link #writeParts} among {@code elements}, as one int[] in its layout: the part count, then each part's size and elements. */
@Override
public int[] partsAmong(int[] elements) { return written(dos -> writeParts(setOf(elements), dos)); }

/** {@link #connectivity} among {@code elements}. */
@Override
public double connectivityAmong(int[] elements) { return connectivity(setOf(elements)); }

/** {@link #reachesAll} among {@code elements}. */
@Override
public boolean reachesAllAmong(int[] elements) { return reachesAll(setOf(elements)); }

/** {@link #connectsAll} among {@code elements}. */
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
 * The requests, by the codes {@link NodeEdgeBase} marks: each reads its inputs, calls the public method,
 * which keeps its checks, and writes the result. A read is recorded as a query: its method's name and
 * inputs, then its answer, or its refusal; a list is answered by its length.
 * */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	String query = null;
	try {
		switch(code) {
			case 0 -> returns.writeBoolean(relateToBinary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 1 -> returns.writeBoolean(unrelateFromBinary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 3 -> returns.writeBoolean(setComplement(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 4 -> returns.writeBoolean(setPrimary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 5 -> returns.writeBoolean(setComplementsOf(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 6 -> returns.writeBoolean(setPrimariesOf(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 7 -> returns.writeBoolean(moveBinary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 8 -> returns.writeBoolean(mergeBinary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 13 -> returns.writeBoolean(relateToUnary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 14 -> returns.writeBoolean(unrelateFromUnary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 16 -> returns.writeBoolean(addParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 17 -> returns.writeBoolean(removeParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 18 -> returns.writeBoolean(setParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 19 -> returns.writeBoolean(setLasts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 20 -> returns.writeBoolean(setFirsts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 21 -> returns.writeBoolean(moveUnary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 22 -> returns.writeBoolean(mergeUnary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 41 -> returns.writeInt(ensureEdge(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 42 -> returns.writeBoolean(breakEdges(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 2, 15, 25 -> {
				int identity = parameters.readInt(), other = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ", " + other + ")";
				boolean value = switch(code) {
					case 2 -> isRelatedToBinary(identity, other);
					case 15 -> isRelatedToUnary(identity, other);
					default -> reaches(identity, other);
				};
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 9, 10, 28, 44, 45 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int value = switch(code) {
					case 9 -> complementCount(identity);
					case 10 -> primaryCount(identity);
					case 28 -> depth(identity);
					case 44 -> firstNodeOf(identity);
					default -> secondNodeOf(identity);
				};
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 31, 32, 43 -> {
				int identity = parameters.readInt(), other = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ", " + other + ")";
				int value = switch(code) {
					case 31 -> distance(identity, other);
					case 32 -> pathLength(identity, other);
					default -> edgeOf(identity, other);
				};
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 11, 12, 23, 24, 26, 27, 46, 47, 48 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int[] value = switch(code) {
					case 11 -> getComplementsOf(identity);
					case 12 -> getPrimariesOf(identity);
					case 23 -> lastsOf(identity);
					case 24 -> firstsOf(identity);
					case 26 -> ancestorsOf(identity);
					case 27 -> descendantsOf(identity);
					case 46 -> edgesOf(identity);
					case 47 -> outEdgesOf(identity);
					default -> inEdgesOf(identity);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 29, 30 -> {
				int identity = parameters.readInt(), other = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ", " + other + ")";
				int[] value = code == 29 ? nearestCommonAncestorsOf(identity, other) : nearestCommonDescendantsOf(identity, other);
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 33, 35, 36, 37 -> {
				int[] elements = readInts(parameters);
				query = REQUESTS[code] + "(int[" + elements.length + "])";
				int[] value = switch(code) {
					case 33 -> topologicalOrderAmong(elements);
					case 35 -> rootsAmong(elements);
					case 36 -> leavesAmong(elements);
					default -> partsAmong(elements);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 34, 39, 40 -> {
				int[] elements = readInts(parameters);
				query = REQUESTS[code] + "(int[" + elements.length + "])";
				boolean value = switch(code) {
					case 34 -> hasCycleAmong(elements);
					case 39 -> reachesAllAmong(elements);
					default -> connectsAllAmong(elements);
				};
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 38 -> {
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

package org.halim.acse.structure.binary.node_edge;

import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.structure.ACS_BinaryStructure_U;
import org.halim.acse.structure.BinaryRestrictions;
import org.halim.acse.structure.UnaryRestrictions;

import static org.halim.acse.structure.UnaryRestrictions.*;
import static org.halim.acse.structure.BinaryRestrictions.*;

/**
 * The node-cluster whose every cluster, an edge, joins exactly two nodes, the same node twice for a
 * loop: the arity at index 0 holds the nodes and the one at index 1 the edges, so a node and an edge each
 * carry data of their own, and an edge is an identity like any other. With {@link #DIRECTED}, the first goes
 * to the second. The connector's requests take a node and an edge, so one that would leave an edge with
 * other than two nodes is refused. {@link #addParent} captures an edge only when none joins the two, and
 * {@link #removeParent} removes every edge from the parent, as {@link #breakEdges} does.
 * <p>
 * What kind of graph the nodes make is the node-cluster's graph rules, so 0 is any symmetric graph,
 * with loops and parallel edges. How the graph is stored is the implementation's, and says nothing about
 * which restrictions it takes. {@link #NO_IMPLIED_LINKS} refuses rather than drops: an edge another path
 * implies is not added, and one that would imply an edge already there is refused, since dropping an edge
 * would forget its data.
 * <p>
 * An edge's two nodes are its first and its second: where it starts and where it ends on a directed
 * graph, the smaller and the larger on a directionless one. Edges are made and removed with their
 * connections. Connecting two nodes captures the edge on the edge arity through the core, as a request
 * nested in the connection's, so undoing the connection removes the edge with it; the edge arity is so
 * caused, but not strictly. An edge leaving its arity - through the core, or by its connection breaking -
 * is forgotten here, and a node leaving its arity removes every edge on it through
 * {@link ACS_Core#delegateRemoval}, which is the recursive step. An edge identity captured any other way is
 * no connection. The two arities must be different slots: an edge that was also a node would be forgotten
 * as both.
 * <p>
 * No contract names this kind, since exactly two nodes a cluster is no enforced structure; its requests are
 * marked here, after the ones of {@link ACS_BinaryStructure_U}, for every node-edge storage to share.
 * */
public abstract class NodeEdgeBase implements ACS_BinaryStructure_U {

public int getNodeArity() { return getArityIdentity(0); }
public int getEdgeArity() { return getArityIdentity(1); }

/** Whether its edges go from their first node to their second, read from its restrictions. */
@Override
public boolean isDirected() { return (getRestrictions() & UnaryRestrictions.DIRECTED) != 0; }

/** The restrictions it was built with, an OR of the bits of {@link UnaryRestrictions} and {@link BinaryRestrictions}. */
public abstract int getRestrictions();


/**
 * As {@link ACS_UnaryStructure#setParent}; with {@link #AT_MOST_ONE_INCOMING}, the edge from its one parent
 * is kept and re-pointed, so its identity and its data stay and only its first node changes.
 * */
@Override
public abstract boolean setParent(int element, int parent, String comment);


/**
 * An edge from {@code node} to {@code otherNode}, or between them on a directionless graph, given back: a
 * new one captured on the edge arity, or, with {@link #NO_PARALLEL_LINKS}, the one already there. Refuses
 * a node its arity does not hold and an edge a restriction forbids.
 * */
@ACS_Requestable(41)
public abstract int ensureEdge(int node, int otherNode, String comment);

/** Removes every edge from {@code node} to {@code otherNode}, or between them on a directionless graph, through the core. False when there was none. */
@ACS_Requestable(42)
public abstract boolean breakEdges(int node, int otherNode, String comment);

/** An edge from {@code node} to {@code otherNode}, or between them on a directionless graph, or -1 for none; with parallel edges, the lowest. */
@ACS_Requestable(43)
public abstract int edgeOf(int node, int otherNode);

public boolean hasEdge(int node, int otherNode) { return edgeOf(node, otherNode) >= 0; }

/** Where the edge starts, or its smaller node on a directionless graph; -1 when the edge is no connection here. */
@ACS_Requestable(44)
public abstract int firstNodeOf(int edge);

/** Where the edge ends, or its larger node on a directionless graph; -1 when the edge is no connection here. */
@ACS_Requestable(45)
public abstract int secondNodeOf(int edge);

/** How many edges the node is on, either way round; a loop counts once. */
public abstract int edgeCount(int node);

/** The node's edges, {@link #edgeCount} of them, ascending. */
public abstract void copyEdgesInto(int node, int[] dest, int offset);

/** The node's edges, as {@link #copyEdgesInto} gives them. */
@ACS_Requestable(46)
public int[] edgesOf(int node) {
	int[] edges = new int[edgeCount(node)];
	copyEdgesInto(node, edges, 0);
	return edges;
}

/** The edges starting at the node; on a directionless graph, all of its edges. */
public int outEdgeCount(int node) {
	if(!isDirected()) { return edgeCount(node); }
	int count = 0;
	for(int edge : edgesOf(node)) { if(firstNodeOf(edge) == node) { count++; } }
	return count;
}

/** The edges starting at the node, {@link #outEdgeCount} of them, ascending. */
public void copyOutEdgesInto(int node, int[] dest, int offset) {
	for(int edge : edgesOf(node)) { if(!isDirected() || firstNodeOf(edge) == node) { dest[offset++] = edge; } }
}

/** The edges starting at the node, as {@link #copyOutEdgesInto} gives them. */
@ACS_Requestable(47)
public int[] outEdgesOf(int node) {
	int[] edges = new int[outEdgeCount(node)];
	copyOutEdgesInto(node, edges, 0);
	return edges;
}

/** The edges ending at the node; on a directionless graph, all of its edges. */
public int inEdgeCount(int node) {
	if(!isDirected()) { return edgeCount(node); }
	int count = 0;
	for(int edge : edgesOf(node)) { if(secondNodeOf(edge) == node) { count++; } }
	return count;
}

/** The edges ending at the node, {@link #inEdgeCount} of them, ascending. */
public void copyInEdgesInto(int node, int[] dest, int offset) {
	for(int edge : edgesOf(node)) { if(!isDirected() || secondNodeOf(edge) == node) { dest[offset++] = edge; } }
}

/** The edges ending at the node, as {@link #copyInEdgesInto} gives them. */
@ACS_Requestable(48)
public int[] inEdgesOf(int node) {
	int[] edges = new int[inEdgeCount(node)];
	copyInEdgesInto(node, edges, 0);
	return edges;
}


}

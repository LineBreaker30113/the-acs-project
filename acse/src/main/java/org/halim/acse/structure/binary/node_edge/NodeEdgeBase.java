package org.halim.acse.structure.binary.node_edge;

import org.halim.acs.ACS_Requestable;
import org.halim.acs.structure.ACS_BinaryStructure;
import org.halim.acs.structure.ACS_UnaryStructure;
import org.halim.acs.structure.UnaryRestrictions;

import java.io.DataOutputStream;
import java.io.IOException;

/** Base class for node-edge binary connector structures with shared unary graph view. */
public abstract class NodeEdgeBase implements ACS_BinaryStructure {

	public int getNodeArity() { return getArityIdentity(0); }
	public int getEdgeArity() { return getArityIdentity(1); }

	public boolean isDirected() { return (getRestrictions() & UnaryRestrictions.DIRECTED) != 0; }

	public abstract int getRestrictions();

	public abstract boolean setPredecessor(int element, int predecessor, String comment);
	public abstract boolean setPredecessors(int element, int[] predecessors, String comment);
	public abstract boolean setSuccessors(int element, int[] successors, String comment);
	public abstract int predecessorCount(int element);
	public abstract void copyPredecessorsInto(int element, int[] dest, int offset);
	public abstract int successorCount(int element);
	public abstract void copySuccessorsInto(int element, int[] dest, int offset);

	@ACS_Requestable(18)
	public abstract int ensureEdge(int node, int otherNode, String comment);

	@ACS_Requestable(19)
	public abstract boolean breakEdges(int node, int otherNode, String comment);

	@ACS_Requestable(20)
	public abstract int edgeOf(int node, int otherNode);

	public boolean hasEdge(int node, int otherNode) { return edgeOf(node, otherNode) >= 0; }

	@ACS_Requestable(21)
	public abstract int firstNodeOf(int edge);

	@ACS_Requestable(22)
	public abstract int secondNodeOf(int edge);

	public abstract int edgeCount(int node);

	public abstract void copyEdgesInto(int node, int[] dest, int offset);

	@ACS_Requestable(23)
	public int[] edgesOf(int node) {
		int[] edges = new int[edgeCount(node)];
		copyEdgesInto(node, edges, 0);
		return edges;
	}

	public int outEdgeCount(int node) {
		if(!isDirected()) { return edgeCount(node); }
		int count = 0;
		for(int edge : edgesOf(node)) { if(firstNodeOf(edge) == node) { count++; } }
		return count;
	}

	public void copyOutEdgesInto(int node, int[] dest, int offset) {
		for(int edge : edgesOf(node)) { if(!isDirected() || firstNodeOf(edge) == node) { dest[offset++] = edge; } }
	}

	@ACS_Requestable(24)
	public int[] outEdgesOf(int node) {
		int[] edges = new int[outEdgeCount(node)];
		copyOutEdgesInto(node, edges, 0);
		return edges;
	}

	public int inEdgeCount(int node) {
		if(!isDirected()) { return edgeCount(node); }
		int count = 0;
		for(int edge : edgesOf(node)) { if(secondNodeOf(edge) == node) { count++; } }
		return count;
	}

	public void copyInEdgesInto(int node, int[] dest, int offset) {
		for(int edge : edgesOf(node)) { if(!isDirected() || secondNodeOf(edge) == node) { dest[offset++] = edge; } }
	}

	@ACS_Requestable(25)
	public int[] inEdgesOf(int node) {
		int[] edges = new int[inEdgeCount(node)];
		copyInEdgesInto(node, edges, 0);
		return edges;
	}

	protected final ACS_UnaryStructure unaryView = new UnaryGraphView();

	public ACS_UnaryStructure asUnaryGraph() { return unaryView; }

	public class UnaryGraphView implements ACS_UnaryStructure {
		@Override public int getArityCount() { return 1; }
		@Override public int getArityIdentity(int index) { return getNodeArity(); }
		@Override public boolean isRelatedArity(int identity) { return identity == getNodeArity(); }
		@Override public boolean isCaused(int index) { return NodeEdgeBase.this.isCaused(0); }
		@Override public int getRestrictions() { return NodeEdgeBase.this.getRestrictions(); }

		@Override public boolean relateToUnary(int predecessor, int successor, String comment) {
			return NodeEdgeBase.this.ensureEdge(predecessor, successor, comment) >= 0;
		}

		@Override public boolean unrelateFromUnary(int predecessor, int successor, String comment) {
			return NodeEdgeBase.this.breakEdges(predecessor, successor, comment);
		}

		@Override public boolean isRelatedToUnary(int predecessor, int successor) {
			return NodeEdgeBase.this.hasEdge(predecessor, successor);
		}

		@Override public boolean setPredecessor(int element, int predecessor, String comment) {
			return NodeEdgeBase.this.setPredecessor(element, predecessor, comment);
		}

		@Override public boolean setPredecessors(int element, int[] predecessors, String comment) {
			return NodeEdgeBase.this.setPredecessors(element, predecessors, comment);
		}

		@Override public boolean setSuccessors(int element, int[] successors, String comment) {
			return NodeEdgeBase.this.setSuccessors(element, successors, comment);
		}

		@Override public int predecessorCount(int element) { return NodeEdgeBase.this.predecessorCount(element); }
		@Override public void copyPredecessorsInto(int element, int[] dest, int offset) { NodeEdgeBase.this.copyPredecessorsInto(element, dest, offset); }
		@Override public int successorCount(int element) { return NodeEdgeBase.this.successorCount(element); }
		@Override public void copySuccessorsInto(int element, int[] dest, int offset) { NodeEdgeBase.this.copySuccessorsInto(element, dest, offset); }

		@Override public boolean saveTo(DataOutputStream dos) throws IOException { return NodeEdgeBase.this.saveTo(dos); }
		@Override public boolean forget(int arity, int identity) { return NodeEdgeBase.this.forget(arity, identity); }
		@Override public void doTraceableStep(byte stepPair, int[] params, int index) { NodeEdgeBase.this.doTraceableStep(stepPair, params, index); }
		@Override public org.halim.acs.ACS_Journal getJournal() { return NodeEdgeBase.this.getJournal(); }
	}
}

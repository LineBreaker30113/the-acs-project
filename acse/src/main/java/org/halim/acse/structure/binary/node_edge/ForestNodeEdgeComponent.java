package org.halim.acse.structure.binary.node_edge;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_Requestable;
import org.halim.acs.structure.ACS_StructureBase;
import org.halim.acs.structure.UnaryRestrictions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * A forest whose nodes are one arity and whose edges are another, in a layout simpler than a general
 * graph's: each node points to its own edge, and the edge is an int list - the node itself, its parent,
 * then the edges of its children. Every int here is an imv, the memory index the identity sets map an
 * identity to, which is also where the identity's data sits, so the node in an edge's list is where the
 * node's data is found, and a child's edge is where the connection's data is found.
 * <p>
 * A node's edge is the edge arity's own identity, captured through the core the first time the node takes
 * part, and it stays the node's: losing its parent sets the parent to 0 rather than removing the edge, so
 * the connection's data outlives the move. That is the difference from the node-edge contract, whose edges
 * come and go with their connections, and why this type has its methods of its own.
 * <p>
 * A parent of 0 is no parent. When the node arity has a root, node 0 is it: every node without a parent
 * hangs from it, and it takes no parent itself. When it has none, {@link #skipFirstNode} is set and node 0
 * is no node here at all. The children of 0 - the root's, or the tops of the trees - are not listed
 * anywhere, so finding them walks every edge; every other node's children are its list. A parent is one
 * read, a node's children one copy, and moving a node removes its edge from its old parent's list, a search
 * of its siblings. A parent below the node is refused, so the forest stays acyclic by construction.
 * <p>
 * Each request records one step, after the edges it captures: the journal replays a request's steps in
 * the order recorded, so a step that needed another's entry could not be undone, and one step needs none.
 * Not multi thread safe.
 * */
public class ForestNodeEdgeComponent implements ACS_StructureBase {

/** [edge, node, old parent, new parent, made, parent's edge]: moves the node, first making its entry ({@code made & 1}) and its parent's ({@code made & 2}). */
static final byte SET = 1;
/** [edge, node, parent, child count, child edges...]: takes the edge out, its children left without a parent. */
static final byte DETACH = 2;

final ACS_Core core;
final ACS_Journal journal;
final int nodeArity, edgeArity;
/** Node 0 is no node here, since the node arity has no root. */
public final boolean skipFirstNode;
final int[] params = new int[6];
final IntArrayList detachParams = new IntArrayList();

/** Per node, its edge; -1 until it takes part. */
final IntArrayList edgeOfNode = new IntArrayList();
/** Per edge, {@code [node, parent, child edges...]}; null where there is none. */
final ArrayList<IntArrayList> entries = new ArrayList<>();


public ForestNodeEdgeComponent(ACS_Core core, ACS_Journal journal, int nodeArity, int edgeArity, boolean skipFirstNode) {
	if(nodeArity == edgeArity) { throw new IllegalArgumentException("nodes and edges in one arity " + nodeArity); }
	this.core = core;
	this.journal = journal;
	this.nodeArity = nodeArity;
	this.edgeArity = edgeArity;
	this.skipFirstNode = skipFirstNode;
}

/** Built by name through the core's addStructure: nodeArity, edgeArity, skipFirstNode read from the request, in that order; nothing is written back. */
public ForestNodeEdgeComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readBoolean());
}


@Override
public int getArityCount() { return 2; }

/** It makes no arity, so it strictly causes none. */
@Override
public boolean isCaused(int index) { return false; }

@Override
public int getRestrictions() {
	return UnaryRestrictions.DIRECTED
		| UnaryRestrictions.ACYCLIC
		| UnaryRestrictions.AT_MOST_ONE_INCOMING
		| UnaryRestrictions.NO_LOOPS
		| UnaryRestrictions.NO_PARALLEL_LINKS;
}

@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> nodeArity;
		case 1 -> edgeArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of a forest over two");
	};
}

@Override
public boolean isRelatedArity(int identity) { return identity == nodeArity || identity == edgeArity; }


/** The node's edge, or -1 before it takes part. */
@ACS_Requestable(5)
public int edgeOf(int node) { return node >= 0 && node < edgeOfNode.size() ? edgeOfNode.getInt(node) : -1; }

/** The node an edge belongs to, or -1 for no edge here. */
@ACS_Requestable(6)
public int nodeOf(int edge) {
	IntArrayList entry = entryOf(edge);
	return entry == null ? -1 : entry.getInt(0);
}

/** The node's parent: 0 for none, and for a node that has not taken part. */
@ACS_Requestable(7)
public int parentOf(int node) {
	IntArrayList entry = entryOf(edgeOf(node));
	return entry == null ? 0 : entry.getInt(1);
}

@ACS_Requestable(8)
public int childCount(int node) {
	if(node == 0) { return topEdges().size(); }
	IntArrayList entry = entryOf(edgeOf(node));
	return entry == null ? 0 : entry.size() - 2;
}

/** The edges of the node's children; for 0, of every node without a parent, which walks every edge. */
public void copyChildEdgesInto(int node, int[] dest, int offset) {
	if(node == 0) {
		IntArrayList top = topEdges();
		top.getElements(0, dest, offset, top.size());
		return;
	}
	IntArrayList entry = entryOf(edgeOf(node));
	if(entry != null) { entry.getElements(2, dest, offset, entry.size() - 2); }
}

/** The node's children themselves, in the order of their edges. */
public void copyChildrenInto(int node, int[] dest, int offset) {
	int[] edges = new int[childCount(node)];
	copyChildEdgesInto(node, edges, 0);
	for(int edge : edges) { dest[offset++] = nodeOf(edge); }
}

/** Whether {@code ancestor} is {@code node} or above it: a walk up its one chain of parents. */
@ACS_Requestable(9)
public boolean reaches(int ancestor, int node) {
	for(int at = node; ; at = parentOf(at)) {
		if(at == ancestor) { return true; }
		if(at == 0) { return false; }
	}
}

IntArrayList entryOf(int edge) { return edge >= 0 && edge < entries.size() ? entries.get(edge) : null; }

/** The edges whose parent is 0, the root excepted. */
IntArrayList topEdges() {
	IntArrayList top = new IntArrayList();
	for(int edge = 0; edge < entries.size(); edge++) {
		IntArrayList entry = entries.get(edge);
		if(entry != null && entry.getInt(1) == 0 && entry.getInt(0) != 0) { top.add(edge); }
	}
	return top;
}


/** The node's edge, captured through the core if it has none yet. */
@ACS_Requestable(10)
public int ensureEdge(int node, String comment) {
	requireNode(node);
	int held = edgeOf(node);
	if(held >= 0) { return held; }
	int request = journal.beginRecordingActivity(this, "ensureEdge", params, comment);
	int edge;
	try {
		edge = capture();
		set(edge, node, 0, 0, 1, -1);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return edge;
}

/**
 * Moves the node under {@code parent}, 0 for none, capturing the node's edge and the parent's if either has
 * none yet. False when it is already there; a refusal when the parent is the node or below it.
 * */
@ACS_Requestable(11)
public boolean setParent(int node, int parent, String comment) {
	requireNode(node);
	if(node == 0) { throw new IllegalArgumentException("the root takes no parent"); }
	if(parent != 0) { requireNode(parent); }
	if(node == parent) { throw new IllegalArgumentException("a node cannot be its own parent: " + node); }
	int old = parentOf(node);
	if(old == parent && edgeOf(node) >= 0) { return false; }
	if(parent != 0 && reaches(node, parent)) { throw new IllegalArgumentException("parent " + parent + " of " + node + " is below it"); }
	int request = journal.beginRecordingActivity(this, "setParent", params, comment);
	try {
		int made = 0, edge = edgeOf(node), parentEdge = parent == 0 ? -1 : edgeOf(parent);
		if(edge < 0) { edge = capture(); made |= 1; }
		if(parent != 0 && parentEdge < 0) { parentEdge = capture(); made |= 2; }
		set(edge, node, old, parent, made, parentEdge);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

@ACS_Requestable(12)
public boolean removeParent(int node, String comment) { return setParent(node, 0, comment); }

/** An edge leaving its arity takes its node out, its children left without a parent; a node leaving its arity removes its edge through the core. */
@Override
public boolean forget(int arity, int identity) {
	if(arity == edgeArity) {
		IntArrayList entry = entryOf(identity);
		if(entry == null) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", detachParams, null);
		detachParams.clear();
		detachParams.add(identity);
		detachParams.add(entry.getInt(0));
		detachParams.add(entry.getInt(1));
		detachParams.add(entry.size() - 2);
		detachParams.addElements(4, entry.elements(), 2, entry.size() - 2);
		detachUnchecked(identity);
		journal.recordActivityStep(DETACH, detachParams.size());
		complete(request);
		return true;
	}
	if(arity != nodeArity) { return false; }
	int edge = edgeOf(identity);
	if(edge < 0) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	core.delegateRemoval(edgeArity, edge, null);
	complete(request);
	return true;
}


int capture() { return Math.toIntExact(core.captureIdentity(edgeArity, null)); }

/** Applies and records the one step. */
void set(int edge, int node, int oldParent, int newParent, int made, int parentEdge) {
	params[0] = edge;
	params[1] = node;
	params[2] = oldParent;
	params[3] = newParent;
	params[4] = made;
	params[5] = parentEdge;
	setUnchecked(params, 0);
	journal.recordActivityStep(SET, 6);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case SET -> setUnchecked(params, index);
		case -SET -> unsetUnchecked(params, index);
		case DETACH -> detachUnchecked(params[index]);
		case -DETACH -> attachUnchecked(params, index);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

void setUnchecked(int[] p, int at) {
	if((p[at + 4] & 1) != 0) { makeEntry(p[at], p[at + 1]); }
	if((p[at + 4] & 2) != 0) { makeEntry(p[at + 5], p[at + 3]); }
	move(p[at], p[at + 2], p[at + 3]);
}

void unsetUnchecked(int[] p, int at) {
	move(p[at], p[at + 3], p[at + 2]);
	if((p[at + 4] & 2) != 0) { dropEntry(p[at + 5], p[at + 3]); }
	if((p[at + 4] & 1) != 0) { dropEntry(p[at], p[at + 1]); }
}

/** Takes the edge out of its parent's list and drops it, its children left without a parent. */
void detachUnchecked(int edge) {
	IntArrayList entry = entries.get(edge);
	move(edge, entry.getInt(1), 0);
	for(int i = 2; i < entry.size(); i++) { entries.get(entry.getInt(i)).set(1, 0); }
	dropEntry(edge, entry.getInt(0));
}

/** The inverse of a detach, from the step's params: the entry back with its children, then under its parent again. */
void attachUnchecked(int[] p, int at) {
	int edge = p[at], node = p[at + 1], parent = p[at + 2], children = p[at + 3];
	makeEntry(edge, node);
	IntArrayList entry = entries.get(edge);
	for(int i = 0; i < children; i++) {
		int child = p[at + 4 + i];
		entries.get(child).set(1, node);
		entry.add(child);
	}
	move(edge, 0, parent);
}

/** A fresh entry, its parent 0, listed under no one. */
void makeEntry(int edge, int node) {
	while(entries.size() <= edge) { entries.add(null); }
	entries.set(edge, IntArrayList.of(node, 0));
	while(edgeOfNode.size() <= node) { edgeOfNode.add(-1); }
	edgeOfNode.set(node, edge);
}

void dropEntry(int edge, int node) {
	entries.set(edge, null);
	edgeOfNode.set(node, -1);
}

/** Off the old parent's list and onto the new one's; 0 is listed nowhere. */
void move(int edge, int from, int to) {
	if(from != 0) {
		IntArrayList list = entries.get(edgeOfNode.getInt(from));
		list.removeInt(list.lastIndexOf(edge));
	}
	if(to != 0) { entries.get(edgeOfNode.getInt(to)).add(edge); }
	entries.get(edge).set(1, to);
}


/** The arity slots and the root rule, then each edge as (edge, node, parent), so the file carries the forest and not its lists. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(nodeArity);
	dos.writeInt(edgeArity);
	dos.writeBoolean(skipFirstNode);
	int count = 0;
	for(IntArrayList entry : entries) { if(entry != null) { count++; } }
	dos.writeInt(count);
	for(int edge = 0; edge < entries.size(); edge++) {
		IntArrayList entry = entries.get(edge);
		if(entry == null) { continue; }
		dos.writeInt(edge);
		dos.writeInt(entry.getInt(0));
		dos.writeInt(entry.getInt(1));
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal: every entry first, then each listed under its parent. */
public static ForestNodeEdgeComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int nodeArity = dis.readInt();
	int edgeArity = dis.readInt();
	ForestNodeEdgeComponent forest = new ForestNodeEdgeComponent(core, journal, nodeArity, edgeArity, dis.readBoolean());
	IntArrayList parents = new IntArrayList();
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int edge = dis.readInt(), node = dis.readInt(), parent = dis.readInt();
		if(edge < 0 || node < 0 || parent < 0 || forest.entryOf(edge) != null || forest.edgeOf(node) >= 0) { throw new IOException("edge " + edge + " of node " + node + " is not a stored edge"); }
		forest.makeEntry(edge, node);
		parents.add(edge);
		parents.add(parent);
	}
	for(int i = 0; i < parents.size(); i += 2) {
		int edge = parents.getInt(i), parent = parents.getInt(i + 1);
		if(parent != 0 && forest.edgeOf(parent) < 0) { throw new IOException("edge " + edge + " names parent " + parent + ", which has no edge"); }
		forest.move(edge, 0, parent);
	}
	return forest;
}


/** A node its arity holds, and not node 0 when the arity has no root. */
void requireNode(int node) {
	if(node < 0 || (node == 0 && skipFirstNode) || !core.getArity(nodeArity).occupied().contains(node)) {
		throw new IllegalArgumentException(node + " is not a node of this forest");
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


/** {@code first} becomes the parent of {@code last}, as {@link #setParent} moves it. False when it is; refused when {@code last} has another parent, since an element has one. */
@ACS_Requestable(13)
public boolean relateToUnary(int first, int last, String comment) {
	if(isRelatedToUnary(first, last)) { return false; }
	if(parentOf(last) != 0) { throw new IllegalArgumentException(last + " has the parent " + parentOf(last) + " already: one parent an element"); }
	return setParent(last, first, comment);
}

/** {@code last} back to 0, as {@link #removeParent} moves it; false when {@code first} is not its parent. */
@ACS_Requestable(14)
public boolean unrelateFromUnary(int first, int last, String comment) { return first != 0 && parentOf(last) == first && removeParent(last, comment); }

@ACS_Requestable(15)
public boolean isRelatedToUnary(int first, int last) { return parentOf(last) == first && (first != 0 || (!skipFirstNode && last != 0)); }

/** The children of {@code first}, in the order {@link #copyChildrenInto} gives them, for the outside: no request fills the caller's array. */
@ACS_Requestable(16)
public int[] lastsOf(int first) {
	if(first == 0 && skipFirstNode) { return new int[0]; }
	int[] children = new int[childCount(first)];
	copyChildrenInto(first, children, 0);
	return children;
}

/** The parent of {@code last}, one at most, as {@link #isRelatedToUnary} counts it. */
@ACS_Requestable(17)
public int[] firstsOf(int last) {
	int parent = parentOf(last);
	if(parent != 0) { return new int[] { parent }; }
	return !skipFirstNode && last != 0 ? new int[] { 0 } : new int[0];
}

@ACS_Requestable(18)
public int lastCount(int first) { return lastsOf(first).length; }

@ACS_Requestable(19)
public int firstCount(int last) { return firstsOf(last).length; }

/**
 * The children of {@code first} become {@code lasts}, a repeat counting once: every other child moved back
 * to 0, then each new one moved under {@code first}, from wherever it was, as one request.
 * Refused for 0, whose children are every element without a parent. False when nothing changes.
 * */
@ACS_Requestable(20)
public boolean setLasts(int first, int[] lasts, String comment) {
	if(first == 0) { throw new IllegalArgumentException("the elements without a parent are set by their own parents"); }
	IntOpenHashSet kept = new IntOpenHashSet(lasts);
	int[] held = lastsOf(first);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, "setLasts", params, comment);
	try {
		for(int child : held) { if(!kept.contains(child)) { removeParent(child, null); } }
		for(int child : lasts) { if(had.add(child)) { setParent(child, first, null); } }
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

/** The parent of {@code last} becomes the one of {@code firsts}, 0 not counted: {@link #setParent}, or {@link #removeParent} for none; more than one is refused. */
@ACS_Requestable(21)
public boolean setFirsts(int last, int[] firsts, String comment) {
	IntOpenHashSet kept = new IntOpenHashSet(firsts);
	kept.remove(0);
	if(kept.size() > 1) { throw new IllegalArgumentException(last + " given " + kept.size() + " parents: one an element"); }
	return kept.isEmpty() ? removeParent(last, comment) : setParent(last, kept.iterator().nextInt(), comment);
}



@Override
public ACS_Journal getJournal() { return journal; }

}

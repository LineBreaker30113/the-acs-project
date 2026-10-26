package org.halim.acse.structure.unary;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.arity.sets.IdentitiesSet;
import org.halim.acs.structure.ACS_UnaryStructure;
import org.halim.acs.structure.UnaryRestrictions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * The single-parent sibling of {@link TightSetHierarchyDirectedAcyclicIntGraphComponent}: every element
 * has exactly one parent, the root by default, so the graph is a tree hanging from {@link #ROOT}.
 * <p>
 * The encoding is the sibling's with the parent count fixed at one. {@link #hierarchy} holds the parent
 * of a leaf inline; an element with children holds {@code -slot}, and its entry is
 * {@code [parent, children...]}. Slot 0 is the root's children, maintained as a superset and filtered
 * on read. With one parent, reachability is a walk up a single chain, and no set can go redundant.
 * <p>
 * Every change is journaled as a move, {@link #MOVE}: the element's one parent changes, so adding and
 * removing an edge would be two steps on the same thing, and their order would matter. Not multi
 * thread safe.
 * */
public class FolderHierarchyDirectedAcyclicIntGraphComponent implements ACS_UnaryStructure {

public static final int ROOT = 0;
static final int ROOT_SLOT = 0;
/** [element, from, to]: the positive pair moves the element to {@code to}, the negative back to {@code from}. */
static final byte MOVE = 1;

final ACS_Core core;
final ACS_Journal journal;
/** The slot of the arity this graph is over. */
final int arity;
final int[] params = new int[3];

/** Per element: the parent when positive, under the root when zero, {@code -slot} when it has children. */
IntArrayList hierarchy;
/** Per slot: {@code [parent, children...]}. */
ArrayList<IntArrayList> multiData;
IntArrayList freeSlots;
boolean rootChildrenDirty;


public FolderHierarchyDirectedAcyclicIntGraphComponent(ACS_Core core, ACS_Journal journal, int arity) {
	this.core = core;
	this.journal = journal;
	this.arity = arity;
	clear();
}

/** Built by name through the core's addStructure: arity read from the request, in that order; nothing is written back. */
public FolderHierarchyDirectedAcyclicIntGraphComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt());
}

void clear() {
	hierarchy = new IntArrayList();
	multiData = new ArrayList<>();
	multiData.add(new IntArrayList());
	multiData.get(ROOT_SLOT).add(ROOT);
	freeSlots = new IntArrayList();
	rootChildrenDirty = false;
}

IdentitiesSet occupied() { return core.getArity(arity).occupied(); }


@Override
public int getArityCount() { return 1; }

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
	if(index != 0) { throw new IndexOutOfBoundsException("arity " + index + " of a graph over one"); }
	return arity;
}

@Override
public boolean isRelatedArity(int identity) { return identity == arity; }


/** Makes {@code identity} addressable; a new element reads as under the root. True when it had to grow. */
public boolean ensureCapacity(long identity) {
	int want = Math.toIntExact(identity + 1);
	if(hierarchy.size() >= want) { return false; }
	IntArrayList rootChildren = multiData.get(ROOT_SLOT);
	for(int i = Math.max(hierarchy.size(), 1); i < want; i++) { rootChildren.add(i); }
	hierarchy.size(want);
	rootChildrenDirty = true;
	return true;
}

public long elementCount() { return occupied().size(); }

public boolean isUnderRoot(int element) { return element != ROOT && parentOf(element) == ROOT; }

/** The root for an element under it; the root itself has none and answers -1. */
public int parentOf(int element) {
	if(element == ROOT) { return -1; }
	int h = h(element);
	if(h >= 0) { return h; }
	return multiData.get(-h).getInt(0);
}

public int childCount(int element) {
	int slot = slotOf(element);
	if(slot < 0 || (slot == 0 && element != ROOT)) { return 0; }
	return multiData.get(slot).size() - 1;
}

public int childAt(int element, int at) {
	int slot = slotOf(element);
	if(slot > 0 || element == ROOT) {
		IntArrayList entry = multiData.get(slot);
		if(at >= 0 && 1 + at < entry.size()) { return entry.getInt(1 + at); }
	}
	throw new IndexOutOfBoundsException("child " + at + " of " + element);
}

public void copyChildrenInto(int element, int[] dest, int offset) {
	int slot = slotOf(element);
	if(slot < 0 || (slot == 0 && element != ROOT)) { return; }
	IntArrayList entry = multiData.get(slot);
	for(int i = 1; i < entry.size(); i++) { dest[offset++] = entry.getInt(i); }
}

public boolean hasChild(int element, int child) { return child != ROOT && parentOf(child) == element; }

/** Whether {@code to} is {@code from} or below it: a walk up {@code to}'s only chain of parents. */
public boolean reaches(int from, int to) {
	if(from == ROOT) { return true; }
	for(int at = to; at != ROOT; at = parentOf(at)) {
		if(at == from) { return true; }
	}
	return false;
}


/**
 * Moves {@code element} under {@code parent}, the root included. False when it is already there, and a
 * refusal when the parent is below the element, which would close a cycle.
 * */
public boolean setParent(int element, int parent, String comment) {
	requireLive(element);
	if(parent != ROOT) { requireLive(parent); }
	if(element == ROOT) { throw new IllegalArgumentException("the root has no parent"); }
	if(element == parent) { throw new IllegalArgumentException("an element cannot be its own parent: " + element); }
	int old = parentOf(element);
	if(old == parent) { return false; }
	if(reaches(element, parent)) { throw new IllegalArgumentException("parent " + parent + " of " + element + " closes a cycle"); }
	int request = journal.beginRecordingActivity(this, "setParent", params, comment);
	move(element, old, parent);
	complete(request);
	return true;
}

public boolean addChild(int element, int child, String comment) { return setParent(child, element, comment); }

/** Moves the element back under the root. */
public boolean removeParent(int element, String comment) { return setParent(element, ROOT, comment); }

/** Drops every edge touching {@code element}; its children fall back under the root. */
public void detach(int element, String comment) {
	if(element == ROOT || (isUnderRoot(element) && childCount(element) == 0)) { return; }
	int request = journal.beginRecordingActivity(this, "detach", params, comment);
	detachEdges(element);
	complete(request);
}

@Override
public boolean forget(int arity, int identity) {
	if(arity != this.arity || identity == ROOT || (isUnderRoot(identity) && childCount(identity) == 0)) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	detachEdges(identity);
	complete(request);
	return true;
}

void detachEdges(int element) {
	if(!isUnderRoot(element)) { move(element, parentOf(element), ROOT); }
	while(childCount(element) > 0) { move(childAt(element, 0), element, ROOT); }
}


/** Moves and records it. */
void move(int element, int from, int to) {
	moveUnchecked(element, from, to);
	params[0] = element;
	params[1] = from;
	params[2] = to;
	journal.recordActivityStep(MOVE, 3);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case MOVE -> moveUnchecked(params[index], params[index + 1], params[index + 2]);
		case -MOVE -> moveUnchecked(params[index], params[index + 2], params[index + 1]);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

void moveUnchecked(int element, int from, int to) {
	if(from != ROOT) { unlinkUnchecked(element, from); }
	if(to != ROOT) { linkUnchecked(element, to); }
}

/** The edge with no check; the element must be under the root. */
void linkUnchecked(int element, int parent) {
	ensureCapacity(Math.max(element, parent));
	multiData.get(promote(parent)).add(element);
	int h = hierarchy.getInt(element);
	if(h == 0) { hierarchy.set(element, parent); }
	else { multiData.get(-h).set(0, parent); }
	rootChildrenDirty = true;
}

boolean unlinkUnchecked(int element, int parent) {
	if(element == ROOT || parent == ROOT || parentOf(element) != parent) { return false; }
	IntArrayList parentEntry = multiData.get(slotOf(parent));
	parentEntry.removeInt(indexOf(parentEntry, 1, parentEntry.size(), element));
	int h = hierarchy.getInt(element);
	if(h > 0) { hierarchy.set(element, 0); }
	else { multiData.get(-h).set(0, ROOT); }
	demoteIfInline(parent);
	demoteIfInline(element);
	multiData.get(ROOT_SLOT).add(element);
	rootChildrenDirty = true;
	return true;
}


/** The arity slot, then the edges as (element, parent) pairs, so the file carries the relation and not this encoding. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	IntArrayList edges = new IntArrayList();
	for(int element = 1; element < hierarchy.size(); element++) {
		if(isUnderRoot(element)) { continue; }
		edges.add(element);
		edges.add(parentOf(element));
	}
	dos.writeInt(arity);
	dos.writeInt(hierarchy.size());
	dos.writeInt(edges.size() / 2);
	for(int i = 0; i < edges.size(); i++) { dos.writeInt(edges.getInt(i)); }
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal. */
public static FolderHierarchyDirectedAcyclicIntGraphComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	FolderHierarchyDirectedAcyclicIntGraphComponent graph = new FolderHierarchyDirectedAcyclicIntGraphComponent(core, journal, dis.readInt());
	graph.ensureCapacity(dis.readInt() - 1);
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int element = dis.readInt();
		graph.linkUnchecked(element, dis.readInt());
	}
	return graph;
}


/** Filters slot 0 to what is under the root, occupied and listed once, after growing to the occupied set. */
void refreshRootChildren() {
	IdentitiesSet occupied = occupied();
	if(occupied.size() > 0) { ensureCapacity(occupied.identityAt(occupied.size() - 1)); }
	if(!rootChildrenDirty) { return; }
	IntArrayList entry = multiData.get(ROOT_SLOT);
	IntOpenHashSet seen = new IntOpenHashSet();
	int keep = 1;
	for(int i = 1; i < entry.size(); i++) {
		int candidate = entry.getInt(i);
		if(isUnderRoot(candidate) && occupied.contains(candidate) && seen.add(candidate)) { entry.set(keep++, candidate); }
	}
	entry.size(keep);
	rootChildrenDirty = false;
}

/** The element's int, or 0 - under the root - for an identity the graph has not grown to yet. */
int h(int element) { return element < hierarchy.size() ? hierarchy.getInt(element) : 0; }

/** The element's slot; 0 for the root, 0 or less for an element with no children. */
int slotOf(int element) {
	if(element == ROOT) {
		refreshRootChildren();
		return ROOT_SLOT;
	}
	return -h(element);
}

int promote(int element) {
	if(element == ROOT) { return ROOT_SLOT; }
	int h = hierarchy.getInt(element);
	if(h < 0) { return -h; }
	int slot = takeSlot();
	IntArrayList entry = multiData.get(slot);
	entry.clear();
	entry.add(h);
	hierarchy.set(element, -slot);
	return slot;
}

/** Back to the inline shape once the element has no children, returning its slot. */
void demoteIfInline(int element) {
	if(element == ROOT) { return; }
	int h = hierarchy.getInt(element);
	if(h >= 0) { return; }
	IntArrayList entry = multiData.get(-h);
	if(entry.size() > 1) { return; }
	hierarchy.set(element, entry.getInt(0));
	entry.clear();
	freeSlots.add(-h);
}

int takeSlot() {
	if(!freeSlots.isEmpty()) { return freeSlots.popInt(); }
	multiData.add(new IntArrayList());
	return multiData.size() - 1;
}

static int indexOf(IntArrayList entry, int from, int to, int value) {
	for(int i = from; i < to; i++) {
		if(entry.getInt(i) == value) { return i; }
	}
	return -1;
}

void requireLive(int element) {
	if(element < 0 || !occupied().contains(element)) {
		throw new IllegalArgumentException(element + " is not an occupied identity");
	}
}

void complete(int request) {
	int completed = journal.completeRecordingActivity();
	if(completed != request) { throw new IllegalStateException("began request " + request + ", completed " + completed); }
}


/** {@code first} becomes the parent of {@code last}, as {@link #setParent} moves it. False when it is; refused when {@code last} has another parent, since an element has one. */
@Override
public boolean relateToUnary(int first, int last, String comment) {
	if(isRelatedToUnary(first, last)) { return false; }
	if(parentOf(last) != ROOT) { throw new IllegalArgumentException(last + " has the parent " + parentOf(last) + " already: one parent an element"); }
	return setParent(last, first, comment);
}

/** {@code last} back to the root, as {@link #removeParent} moves it; false when {@code first} is not its parent. */
@Override
public boolean unrelateFromUnary(int first, int last, String comment) { return first != ROOT && parentOf(last) == first && removeParent(last, comment); }

@Override
public boolean isRelatedToUnary(int first, int last) { return last != ROOT && parentOf(last) == first; }

@Override
public int predecessorCount(int element) {
	return (element == ROOT || parentOf(element) == ROOT) ? 0 : 1;
}

@Override
public void copyPredecessorsInto(int element, int[] dest, int offset) {
	int parent = parentOf(element);
	if(element != ROOT && parent != ROOT) { dest[offset] = parent; }
}

@Override
public int successorCount(int element) { return childCount(element); }

@Override
public void copySuccessorsInto(int element, int[] dest, int offset) { copyChildrenInto(element, dest, offset); }

@Override
public boolean setPredecessor(int element, int predecessor, String comment) {
	return setParent(element, predecessor, comment);
}

@Override
public boolean setPredecessors(int element, int[] predecessors, String comment) {
	return setFirsts(element, predecessors, comment);
}

@Override
public boolean setSuccessors(int element, int[] successors, String comment) {
	return setLasts(element, successors, comment);
}

/** The children of {@code first}, in the order {@link #copyChildrenInto} gives them, for the outside: no request fills the caller's array. */
public int[] lastsOf(int first) {
	int[] children = new int[childCount(first)];
	copyChildrenInto(first, children, 0);
	return children;
}

/** The parent of {@code last}, one at most, as {@link #isRelatedToUnary} counts it. */
public int[] firstsOf(int last) { return last == ROOT ? new int[0] : new int[] { parentOf(last) }; }

public int lastCount(int first) { return lastsOf(first).length; }

public int firstCount(int last) { return firstsOf(last).length; }

/**
 * The children of {@code first} become {@code lasts}, a repeat counting once: every other child moved back
 * to the root, then each new one moved under {@code first}, from wherever it was, as one request.
 * Refused for the root, whose children are every element without a parent. False when nothing changes.
 * */
public boolean setLasts(int first, int[] lasts, String comment) {
	if(first == ROOT) { throw new IllegalArgumentException("the elements without a parent are set by their own parents"); }
	IntOpenHashSet kept = new IntOpenHashSet(lasts);
	int[] held = lastsOf(first);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, "setLasts", params, comment);
	try {
		for(int child : held) { if(!kept.contains(child)) { removeParent(child, null); } }
		for(int child : lasts) { if(had.add(child)) { setParent(child, first, null); } }
	} catch(RuntimeException | Error e) {
		journal.abortRecordingActivity();
		throw e;
	}
	complete(request);
	return true;
}

/** The parent of {@code last} becomes the one of {@code firsts}, the root not counted: {@link #setParent}, or {@link #removeParent} for none; more than one is refused. */
public boolean setFirsts(int last, int[] firsts, String comment) {
	IntOpenHashSet kept = new IntOpenHashSet(firsts);
	kept.remove(ROOT);
	if(kept.size() > 1) { throw new IllegalArgumentException(last + " given " + kept.size() + " parents: one an element"); }
	return kept.isEmpty() ? removeParent(last, comment) : setParent(last, kept.iterator().nextInt(), comment);
}

@Override
public ACS_Journal getJournal() { return journal; }

}

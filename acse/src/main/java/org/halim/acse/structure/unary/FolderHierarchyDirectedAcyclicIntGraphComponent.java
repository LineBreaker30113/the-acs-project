package org.halim.acse.structure.unary;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.arity.sets.IdentitiesSet;
import org.halim.acs.structure.ACS_StructureComponentBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
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
public class FolderHierarchyDirectedAcyclicIntGraphComponent implements ACS_StructureComponentBase {

public static final int ROOT = 0;
static final int ROOT_SLOT = 0;
/** [element, from, to]: the positive pair moves the element to {@code to}, the negative back to {@code from}. */
static final byte MOVE = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = requestNames(FolderHierarchyDirectedAcyclicIntGraphComponent.class);

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

@Override
public int getArityIdentity(int index) {
	if(index != 0) { throw new IndexOutOfBoundsException("arity " + index + " of a graph over one"); }
	return arity;
}

@Override
public boolean isRelatedArity(int identity) { return identity == arity; }


/** Makes {@code identity} addressable; a new element reads as under the root. True when it had to grow. */
@ACS_Requestable(0)
public boolean ensureCapacity(long identity) {
	int want = Math.toIntExact(identity + 1);
	if(hierarchy.size() >= want) { return false; }
	IntArrayList rootChildren = multiData.get(ROOT_SLOT);
	for(int i = Math.max(hierarchy.size(), 1); i < want; i++) { rootChildren.add(i); }
	hierarchy.size(want);
	rootChildrenDirty = true;
	return true;
}

@ACS_Requestable(1)
public long elementCount() { return occupied().size(); }

@ACS_Requestable(2)
public boolean isUnderRoot(int element) { return element != ROOT && parentOf(element) == ROOT; }

/** The root for an element under it; the root itself has none and answers -1. */
@ACS_Requestable(3)
public int parentOf(int element) {
	if(element == ROOT) { return -1; }
	int h = h(element);
	if(h >= 0) { return h; }
	return multiData.get(-h).getInt(0);
}

@ACS_Requestable(4)
public int childCount(int element) {
	int slot = slotOf(element);
	if(slot < 0 || (slot == 0 && element != ROOT)) { return 0; }
	return multiData.get(slot).size() - 1;
}

@ACS_Requestable(5)
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

@ACS_Requestable(6)
public boolean hasChild(int element, int child) { return child != ROOT && parentOf(child) == element; }

/** Whether {@code to} is {@code from} or below it: a walk up {@code to}'s only chain of parents. */
@ACS_Requestable(7)
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
@ACS_Requestable(8)
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

@ACS_Requestable(9)
public boolean addChild(int element, int child, String comment) { return setParent(child, element, comment); }

/** Moves the element back under the root. */
@ACS_Requestable(10)
public boolean removeParent(int element, String comment) { return setParent(element, ROOT, comment); }

/** Drops every edge touching {@code element}; its children fall back under the root. */
@ACS_Requestable(11)
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
@ACS_Requestable(12)
public boolean connectTo(int first, int last, String comment) {
	if(isConnectedTo(first, last)) { return false; }
	if(parentOf(last) != ROOT) { throw new IllegalArgumentException(last + " has the parent " + parentOf(last) + " already: one parent an element"); }
	return setParent(last, first, comment);
}

/** {@code last} back to the root, as {@link #removeParent} moves it; false when {@code first} is not its parent. */
@ACS_Requestable(13)
public boolean disconnectFrom(int first, int last, String comment) { return first != ROOT && parentOf(last) == first && removeParent(last, comment); }

@ACS_Requestable(14)
public boolean isConnectedTo(int first, int last) { return last != ROOT && parentOf(last) == first; }

/** The children of {@code first}, in the order {@link #copyChildrenInto} gives them, for the outside: no request fills the caller's array. */
@ACS_Requestable(15)
public int[] lastsOf(int first) {
	int[] children = new int[childCount(first)];
	copyChildrenInto(first, children, 0);
	return children;
}

/** The parent of {@code last}, one at most, as {@link #isConnectedTo} counts it. */
@ACS_Requestable(16)
public int[] firstsOf(int last) { return last == ROOT ? new int[0] : new int[] { parentOf(last) }; }

@ACS_Requestable(17)
public int lastCount(int first) { return lastsOf(first).length; }

@ACS_Requestable(18)
public int firstCount(int last) { return firstsOf(last).length; }

/**
 * The children of {@code first} become {@code lasts}, a repeat counting once: every other child moved back
 * to the root, then each new one moved under {@code first}, from wherever it was, as one request.
 * Refused for the root, whose children are every element without a parent. False when nothing changes.
 * */
@ACS_Requestable(19)
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
@ACS_Requestable(20)
public boolean setFirsts(int last, int[] firsts, String comment) {
	IntOpenHashSet kept = new IntOpenHashSet(firsts);
	kept.remove(ROOT);
	if(kept.size() > 1) { throw new IllegalArgumentException(last + " given " + kept.size() + " parents: one an element"); }
	return kept.isEmpty() ? removeParent(last, comment) : setParent(last, kept.iterator().nextInt(), comment);
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
			case 0 -> {
				long identity = parameters.readLong();
				query = REQUESTS[code] + "(" + identity + ")";
				boolean value = ensureCapacity(identity);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 1 -> {
				query = REQUESTS[code] + "()";
				long value = elementCount();
				recordQuery(query, Long.toString(value));
				returns.writeLong(value);
			}
			case 2 -> {
				int element = parameters.readInt();
				query = REQUESTS[code] + "(" + element + ")";
				boolean value = isUnderRoot(element);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 3 -> {
				int element = parameters.readInt();
				query = REQUESTS[code] + "(" + element + ")";
				int value = parentOf(element);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 4 -> {
				int element = parameters.readInt();
				query = REQUESTS[code] + "(" + element + ")";
				int value = childCount(element);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 5 -> {
				int element = parameters.readInt(), at = parameters.readInt();
				query = REQUESTS[code] + "(" + element + ", " + at + ")";
				int value = childAt(element, at);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 6 -> {
				int element = parameters.readInt(), child = parameters.readInt();
				query = REQUESTS[code] + "(" + element + ", " + child + ")";
				boolean value = hasChild(element, child);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 7 -> {
				int from = parameters.readInt(), to = parameters.readInt();
				query = REQUESTS[code] + "(" + from + ", " + to + ")";
				boolean value = reaches(from, to);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 8 -> returns.writeBoolean(setParent(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 9 -> returns.writeBoolean(addChild(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 10 -> returns.writeBoolean(removeParent(parameters.readInt(), ACS_Actor.readString(parameters)));
			case 11 -> detach(parameters.readInt(), ACS_Actor.readString(parameters));
			case 12 -> returns.writeBoolean(connectTo(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 13 -> returns.writeBoolean(disconnectFrom(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 14 -> {
				int first = parameters.readInt(), last = parameters.readInt();
				query = REQUESTS[code] + "(" + first + ", " + last + ")";
				boolean value = isConnectedTo(first, last);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 15 -> {
				int first = parameters.readInt();
				query = REQUESTS[code] + "(" + first + ")";
				int[] value = lastsOf(first);
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 16 -> {
				int last = parameters.readInt();
				query = REQUESTS[code] + "(" + last + ")";
				int[] value = firstsOf(last);
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 17 -> {
				int first = parameters.readInt();
				query = REQUESTS[code] + "(" + first + ")";
				int value = lastCount(first);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 18 -> {
				int last = parameters.readInt();
				query = REQUESTS[code] + "(" + last + ")";
				int value = firstCount(last);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 19 -> returns.writeBoolean(setLasts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 20 -> returns.writeBoolean(setFirsts(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
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

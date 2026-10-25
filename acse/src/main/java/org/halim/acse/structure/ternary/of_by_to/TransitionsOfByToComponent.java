package org.halim.acse.structure.ternary.of_by_to;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import static org.halim.acse.structure.TernaryRestrictions.*;


/**
 * Transitions: each identity of the by arity transforms one element of the of arity to one of the to arity,
 * and the by arity holds only transitions that exist - a transition is captured with its pair, and removed
 * through the core when its of or its to leaves, as node-edge makes and removes an edge with its
 * connection. One pair a by, so a transition's of and to are one int each, the identity plus one, 0 for
 * none; per of and per to, a list of the transitions on it, so both sides read without a scan.
 * <p>
 * It takes the kinds with {@link #TRANSITIONS} and keeps the element bits and {@link #NO_PARALLEL_VIAS} with
 * checks, and {@link #FULL} it refuses, since it captures one transition at
 * a time. A transition's pair is replaced by {@link #setPairs} and never removed alone, so
 * {@link #removePair} refuses: a transition goes through the core. A by captured any other way is no
 * transition until {@link #addPair} gives it its pair. A step flips the two ints, which makes it its own
 * inverse whatever the order undo replays it in. Not multi thread safe.
 * */
public class TransitionsOfByToComponent extends OfByToBase {

/** The transition (by, of, to) matched; the negative triple unmatches it, by the same flips. */
static final byte LINK = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = ACS_Actor.requestNames(TransitionsOfByToComponent.class);
static final int EVERY_BIT = NO_REPEATS | NO_PARALLEL_VIAS | AT_MOST_ONE_PRIMARY_PER_RELATION | AT_MOST_ONE_COMPLEMENT_PER_RELATION | AT_MOST_ONE_RELATION_PER_PRIMARY | AT_MOST_ONE_TO_PER_FROM | AT_MOST_ONE_RELATION_PER_COMPLEMENT | AT_MOST_ONE_FROM_PER_TO | EVERY_VIA_MATCHES;

final ACS_Core core;
final ACS_Journal journal;
final int ofArity, byArity, toArity, restrictions;
final int[] params = new int[3];

/** Per by, its of plus one, 0 for none. */
final IntArrayList ofs = new IntArrayList();
/** Per by, its to plus one, 0 for none. */
final IntArrayList tos = new IntArrayList();
/** Per of, the transitions from it; null until it has had one. */
final ArrayList<IntArrayList> takers = new ArrayList<>();
/** Per to, the transitions to it; null until it has had one. */
final ArrayList<IntArrayList> givers = new ArrayList<>();


public TransitionsOfByToComponent(ACS_Core core, ACS_Journal journal, int ofArity, int byArity, int toArity, int restrictions) {
	if(ofArity == byArity || byArity == toArity || ofArity == toArity) { throw new IllegalArgumentException("arities " + ofArity + ", " + byArity + " and " + toArity + " are not three slots"); }
	if((restrictions & FULL) != 0) { throw new IllegalArgumentException("FULL holds only where the by arity is strictly caused; transitions are captured one at a time"); }
	if((restrictions & ~EVERY_BIT) != 0) { throw new IllegalArgumentException("restrictions " + restrictions + " hold a bit of-by-to has not"); }
	if((restrictions & TRANSITIONS) != TRANSITIONS) { throw new IllegalArgumentException("transitions are built with AT_MOST_ONE_PRIMARY_PER_RELATION, AT_MOST_ONE_COMPLEMENT_PER_RELATION, NO_REPEATS and EVERY_VIA_MATCHES; given " + restrictions); }
	this.core = core;
	this.journal = journal;
	this.ofArity = ofArity;
	this.byArity = byArity;
	this.toArity = toArity;
	this.restrictions = restrictions;
}

/** Built by name through the core's addStructure: ofArity, byArity, toArity, restrictions read from the request, in that order; nothing is written back. */
public TransitionsOfByToComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readInt(), parameters.readInt());
}


@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> ofArity;
		case 1 -> byArity;
		case 2 -> toArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of an of-by-to over three");
	};
}

public int getRestrictions() { return restrictions; }

/** It makes no arity, so it strictly causes none; a via it captures through the core is the client's arity's too. */
@Override
public boolean isCaused(int index) { return false; }


@Override
public int pairCount(int by) { return ofOf(by) >= 0 ? 1 : 0; }

@Override
public void copyPairsInto(int by, int[] dest, int offset) {
	if(ofOf(by) < 0) { return; }
	dest[offset] = ofOf(by);
	dest[offset + 1] = toOf(by);
}

@Override
public int takerCount(int of) { return sizeOf(listAt(takers, of)); }

@Override
public void copyTakersInto(int of, int[] dest, int offset) { copySorted(listAt(takers, of), dest, offset); }

@Override
public int giverCount(int to) { return sizeOf(listAt(givers, to)); }

@Override
public void copyGiversInto(int to, int[] dest, int offset) { copySorted(listAt(givers, to), dest, offset); }

@Override
public void writeTosOf(int of, DataOutputStream dos) throws IOException {
	IntOpenHashSet found = new IntOpenHashSet();
	IntArrayList held = listAt(takers, of);
	for(int i = 0; i < sizeOf(held); i++) { found.add(toOf(held.getInt(i))); }
	writeSorted(dos, found);
}

@Override
public void writeFromsOf(int to, DataOutputStream dos) throws IOException {
	IntOpenHashSet found = new IntOpenHashSet();
	IntArrayList held = listAt(givers, to);
	for(int i = 0; i < sizeOf(held); i++) { found.add(ofOf(held.getInt(i))); }
	writeSorted(dos, found);
}


/**
 * The pair must be one, as of, to. Every restriction is checked before anything is recorded; the transition
 * is the by arity's lowest free identity, captured nested in this request.
 * */
@Override
public int ensureVia(int[] pairs, String comment) {
	requireOnePair(pairs);
	int of = pairs[0], to = pairs[1];
	requireLive(ofArity, of);
	requireLive(toArity, to);
	if(has(NO_PARALLEL_VIAS)) {
		int held = transitionOf(of, to);
		if(held >= 0) { return held; }
	}
	checkPair(-1, of, to);
	int request = journal.beginRecordingActivity(this, "ensureVia", params, comment);
	int by;
	try {
		by = Math.toIntExact(core.captureIdentity(byArity, null));
		link(by, of, to);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return by;
}

/** Gives a by captured elsewhere its pair; refuses a second pair on a transition. */
@Override
public boolean addPair(int by, int of, int to, String comment) {
	requireLive(byArity, by);
	requireLive(ofArity, of);
	requireLive(toArity, to);
	int heldOf = ofOf(by);
	if(heldOf >= 0) {
		if(heldOf == of && toOf(by) == to) { return false; }
		throw new IllegalArgumentException("transition " + by + " matches (" + heldOf + ", " + toOf(by) + ") already: one pair a transition");
	}
	checkPair(by, of, to);
	int request = journal.beginRecordingActivity(this, "addPair", params, comment);
	link(by, of, to);
	complete(request);
	return true;
}

/** Refuses whenever the pair is there: every transition matches its pair, so the transition goes through the core instead. */
@Override
public boolean removePair(int by, int of, int to, String comment) {
	if(ofOf(by) < 0 || ofOf(by) != of || toOf(by) != to) { return false; }
	throw new IllegalArgumentException("every transition matches its pair: remove transition " + by + " through the core instead");
}

/** Moves the transition to its new pair, which must be one: an unmatch and a match, recorded as two steps of one request. */
@Override
public boolean setPairs(int by, int[] pairs, String comment) {
	requireOnePair(pairs);
	int of = pairs[0], to = pairs[1];
	requireLive(byArity, by);
	requireLive(ofArity, of);
	requireLive(toArity, to);
	int heldOf = ofOf(by), heldTo = toOf(by);
	if(heldOf == of && heldTo == to) { return false; }
	checkPair(by, of, to);
	int request = journal.beginRecordingActivity(this, "setPairs", params, comment);
	if(heldOf >= 0) { unlink(by, heldOf, heldTo); }
	link(by, of, to);
	complete(request);
	return true;
}

/** A transition leaving its arity is unmatched here; an of or a to leaving its arity removes its transitions through the core. */
@Override
public boolean forget(int arity, int identity) {
	if(arity == byArity) {
		int of = ofOf(identity);
		if(of < 0) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", params, null);
		unlink(identity, of, toOf(identity));
		complete(request);
		return true;
	}
	IntArrayList held = arity == ofArity ? listAt(takers, identity) : arity == toArity ? listAt(givers, identity) : null;
	if(sizeOf(held) == 0) { return false; }
	int[] transitions = held.toIntArray();
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	for(int by : transitions) { core.delegateRemoval(byArity, by, null); }
	complete(request);
	return true;
}


/** Refuses the pair for {@code by}, -1 for a new transition, where another transition and a restriction forbid it. */
void checkPair(int by, int of, int to) {
	if(has(NO_PARALLEL_VIAS)) {
		int held = transitionOf(of, to);
		if(held >= 0 && held != by) { throw new IllegalArgumentException("transition " + held + " goes from " + of + " to " + to + " already: no parallel bys"); }
	}
	IntArrayList fromOf = listAt(takers, of), intoTo = listAt(givers, to);
	for(int i = 0; i < sizeOf(fromOf); i++) {
		int other = fromOf.getInt(i);
		if(other == by) { continue; }
		if(has(AT_MOST_ONE_RELATION_PER_PRIMARY)) { throw new IllegalArgumentException("of " + of + " is in transition " + other + " already: one by at most"); }
		if(has(AT_MOST_ONE_TO_PER_FROM) && toOf(other) != to) { throw new IllegalArgumentException("of " + of + " goes to " + toOf(other) + " already: one to at most"); }
	}
	for(int i = 0; i < sizeOf(intoTo); i++) {
		int other = intoTo.getInt(i);
		if(other == by) { continue; }
		if(has(AT_MOST_ONE_RELATION_PER_COMPLEMENT)) { throw new IllegalArgumentException("to " + to + " is in transition " + other + " already: one by at most"); }
		if(has(AT_MOST_ONE_FROM_PER_TO) && ofOf(other) != of) { throw new IllegalArgumentException("to " + to + " comes from " + ofOf(other) + " already: one of at most"); }
	}
}


/** Matches and records it. */
void link(int by, int of, int to) {
	linkUnchecked(by, of, to);
	params[0] = by;
	params[1] = of;
	params[2] = to;
	journal.recordActivityStep(LINK, 3);
}

void unlink(int by, int of, int to) {
	unlinkUnchecked(by, of, to);
	params[0] = by;
	params[1] = of;
	params[2] = to;
	journal.recordActivityStep((byte) -LINK, 3);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case LINK -> linkUnchecked(params[index], params[index + 1], params[index + 2]);
		case -LINK -> unlinkUnchecked(params[index], params[index + 1], params[index + 2]);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

/** Flips the by's two ints and lists it under its of and its to. Grows what it needs to reach them. */
void linkUnchecked(int by, int of, int to) {
	flip(by, of, to);
	grownAt(takers, of).add(by);
	grownAt(givers, to).add(by);
}

/** The same flips, and the by off both lists; the by must be listed there. */
void unlinkUnchecked(int by, int of, int to) {
	flip(by, of, to);
	IntArrayList fromOf = takers.get(of), intoTo = givers.get(to);
	fromOf.removeInt(fromOf.lastIndexOf(by));
	intoTo.removeInt(intoTo.lastIndexOf(by));
}

void flip(int by, int of, int to) {
	if(ofs.size() <= by) {
		ofs.size(by + 1);
		tos.size(by + 1);
	}
	ofs.set(by, ofs.getInt(by) ^ (of + 1));
	tos.set(by, tos.getInt(by) ^ (to + 1));
}


/** The three arity slots and the restrictions, then each transition as (by, of, to). */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(ofArity);
	dos.writeInt(byArity);
	dos.writeInt(toArity);
	dos.writeInt(restrictions);
	int count = 0;
	for(int by = 0; by < ofs.size(); by++) { if(ofs.getInt(by) != 0) { count++; } }
	dos.writeInt(count);
	for(int by = 0; by < ofs.size(); by++) {
		if(ofs.getInt(by) == 0) { continue; }
		dos.writeInt(by);
		dos.writeInt(ofOf(by));
		dos.writeInt(toOf(by));
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses a transition written twice and one a restriction forbids. */
public static TransitionsOfByToComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int ofArity = dis.readInt(), byArity = dis.readInt(), toArity = dis.readInt(), restrictions = dis.readInt();
	TransitionsOfByToComponent transitions;
	try { transitions = new TransitionsOfByToComponent(core, journal, ofArity, byArity, toArity, restrictions); }
	catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int by = dis.readInt(), of = dis.readInt(), to = dis.readInt();
		if(by < 0 || of < 0 || to < 0) { throw new IOException("transition " + by + " (" + of + ", " + to + ") has a negative identity"); }
		if(transitions.ofOf(by) >= 0) { throw new IOException("transition " + by + " is written twice"); }
		try { transitions.checkPair(by, of, to); }
		catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
		transitions.linkUnchecked(by, of, to);
	}
	return transitions;
}


/** The transition's of, -1 for none. */
int ofOf(int by) { return by >= 0 && by < ofs.size() ? ofs.getInt(by) - 1 : -1; }
/** The transition's to, -1 for none. */
int toOf(int by) { return by >= 0 && by < tos.size() ? tos.getInt(by) - 1 : -1; }

/** The lowest transition from {@code of} to {@code to}, -1 for none. */
int transitionOf(int of, int to) {
	IntArrayList held = listAt(takers, of);
	int found = -1;
	for(int i = 0; i < sizeOf(held); i++) {
		int by = held.getInt(i);
		if(toOf(by) == to && (found < 0 || by < found)) { found = by; }
	}
	return found;
}

void requireOnePair(int[] pairs) {
	if(pairs.length != 2) { throw new IllegalArgumentException("a transition matches one pair, given as of, to; given " + pairs.length + " ints"); }
}

boolean has(int restriction) { return (restrictions & restriction) != 0; }

void requireLive(int arity, int identity) {
	if(identity < 0 || !core.getArity(arity).occupied().contains(identity)) {
		throw new IllegalArgumentException(identity + " is not an occupied identity of arity " + arity);
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

static IntArrayList listAt(ArrayList<IntArrayList> lists, int at) { return at >= 0 && at < lists.size() ? lists.get(at) : null; }

static int sizeOf(IntArrayList list) { return list == null ? 0 : list.size(); }

static IntArrayList grownAt(ArrayList<IntArrayList> lists, int at) {
	while(lists.size() <= at) { lists.add(null); }
	IntArrayList held = lists.get(at);
	if(held == null) {
		held = new IntArrayList();
		lists.set(at, held);
	}
	return held;
}

/** The list into {@code dest}, ascending there; the list itself keeps its order. */
static void copySorted(IntArrayList list, int[] dest, int offset) {
	if(list == null) { return; }
	list.getElements(0, dest, offset, list.size());
	IntArrays.quickSort(dest, offset, offset + list.size());
}

static void writeSorted(DataOutputStream dos, IntOpenHashSet found) throws IOException {
	int[] sorted = found.toIntArray();
	Arrays.sort(sorted);
	dos.writeInt(sorted.length);
	for(int identity : sorted) { dos.writeInt(identity); }
}


/** {@link #addPair}, as primary-relation-complement names it: the primary is the of, the relation the by, the complement the to. */
@Override
public boolean relateToTernary(int primary, int relation, int complement, String comment) { return addPair(relation, primary, complement, comment); }

/**
 * The pair is the transition's one, and a transition losing its pair is no transition any more, so the
 * relation leaves its arity through the core, nested in this request, taking both sides with it. False when
 * the relation does not hold the pair.
 * */
@Override
public boolean unrelateFromTernary(int primary, int relation, int complement, String comment) {
	if(!isRelatedToTernary(primary, relation, complement)) { return false; }
	int request = journal.beginRecordingActivity(this, "unrelateFromTernary", params, comment);
	try { core.delegateRemoval(byArity, relation, null); }
	catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return true;
}

/** Whether the relation holds the pair, a walk of its pairs. */
@Override
public boolean isRelatedToTernary(int primary, int relation, int complement) {
	int[] pairs = pairsOf(relation);
	for(int at = 0; at < pairs.length; at += 2) { if(pairs[at] == primary && pairs[at + 1] == complement) { return true; } }
	return false;
}

/** The relation's pairs as primary, complement, primary, complement, in the order {@link #copyPairsInto} gives them, for the outside: no request fills the caller's array. */
@Override
public int[] pairsOf(int relation) {
	int[] pairs = new int[2 * pairCount(relation)];
	copyPairsInto(relation, pairs, 0);
	return pairs;
}

/** The relations pairing the primary, each once and ascending. */
@Override
public int[] takersOf(int primary) {
	int[] takers = new int[takerCount(primary)];
	copyTakersInto(primary, takers, 0);
	return takers;
}

/** The relations pairing the complement, each once and ascending. */
@Override
public int[] giversOf(int complement) {
	int[] givers = new int[giverCount(complement)];
	copyGiversInto(complement, givers, 0);
	return givers;
}

/** What {@link #writeTosOf} writes, as an int[], for the outside: no request takes a stream. */
@Override
public int[] tosOf(int of) { return across(takers, of, true); }

/** What {@link #writeFromsOf} writes, as an int[]. */
@Override
public int[] fromsOf(int to) { return across(givers, to, false); }

/** The other end of each transition holding the identity, each once and ascending: its to when {@code toward}, its of otherwise. */
int[] across(ArrayList<IntArrayList> side, int identity, boolean toward) {
	IntOpenHashSet found = new IntOpenHashSet();
	IntArrayList held = listAt(side, identity);
	for(int i = 0; i < sizeOf(held); i++) { found.add(toward ? toOf(held.getInt(i)) : ofOf(held.getInt(i))); }
	int[] sorted = found.toIntArray();
	Arrays.sort(sorted);
	return sorted;
}


/**
 * The requests, by the codes {@link OfByToBase} marks: each reads its inputs, calls the public method,
 * which keeps its checks, and writes the result. A read is recorded as a query: its method's name and
 * inputs, then its answer, or its refusal; a list is answered by its length.
 * */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	String query = null;
	try {
		switch(code) {
			case 0 -> returns.writeBoolean(relateToTernary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 1 -> returns.writeBoolean(unrelateFromTernary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 2 -> {
				int primary = parameters.readInt(), relation = parameters.readInt(), complement = parameters.readInt();
				query = REQUESTS[code] + "(" + primary + ", " + relation + ", " + complement + ")";
				boolean value = isRelatedToTernary(primary, relation, complement);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 3 -> returns.writeBoolean(take(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 4 -> returns.writeBoolean(untake(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 5 -> returns.writeBoolean(give(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 6 -> returns.writeBoolean(ungive(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 7 -> returns.writeBoolean(setTaken(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 8 -> returns.writeBoolean(setGiven(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 9 -> returns.writeBoolean(moveTernary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 10 -> returns.writeBoolean(mergeTernary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 15 -> returns.writeInt(ensureVia(readInts(parameters), ACS_Actor.readString(parameters)));
			case 16 -> returns.writeBoolean(addPair(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 17 -> returns.writeBoolean(removePair(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 18 -> returns.writeBoolean(setPairs(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 11, 12, 13, 14, 19, 20, 21 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int[] value = switch(code) {
					case 11 -> takenOf(identity);
					case 12 -> givenOf(identity);
					case 13 -> takersOf(identity);
					case 14 -> giversOf(identity);
					case 19 -> pairsOf(identity);
					case 20 -> tosOf(identity);
					default -> fromsOf(identity);
				};
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
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


/** An int[] as {@link #writeInts} writes it, its elements taken as they arrive rather than the length allocated first, so a garbled length ends in an EOFException. */
static int[] readInts(DataInputStream dis) throws IOException {
	int length = dis.readInt();
	if(length < 0) { throw new IOException("int[] length " + length); }
	IntArrayList ints = new IntArrayList();
	for(int i = 0; i < length; i++) { ints.add(dis.readInt()); }
	return ints.toIntArray();
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


}

package org.halim.acse.structure.ternary.of_by_to;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.halim.acse.structure.TernaryRestrictions.*;


/**
 * Of-by-to as int lists: per by, its pairs as of, to, of, to, in the order matched; per of and per to, a by
 * for each pair holding it, so both sides read without a scan. It takes every kind but {@link #FULL}, which
 * no storage adding bys one at a time keeps, and checks each restriction when a list changes, on the by's
 * new list as a whole against every other by's pairs.
 * <p>
 * A step is one pair added last or its last occurrence removed, which undo and redo replay in any order. A
 * by matching no pair is parallel to none, as a new identity matches none. On {@link #TRANSITIONS}, an of or a to leaving removes the bys
 * holding it through the core, as the contract says. Not multi thread safe.
 * */
public class ArrayListsOfByToComponent extends OfByToBase {

/** The pair (by, of, to) added last to the by's list; the negative triple removes its last occurrence. */
static final byte PAIR = 1;
static final int EVERY_BIT = NO_REPEATS | NO_PARALLEL_VIAS | AT_MOST_ONE_PRIMARY_PER_RELATION | AT_MOST_ONE_COMPLEMENT_PER_RELATION | AT_MOST_ONE_RELATION_PER_PRIMARY | AT_MOST_ONE_TO_PER_FROM | AT_MOST_ONE_RELATION_PER_COMPLEMENT | AT_MOST_ONE_FROM_PER_TO | EVERY_VIA_MATCHES;

final ACS_Core core;
final ACS_Journal journal;
final int ofArity, byArity, toArity, restrictions;
final int[] params = new int[3];

/** Per by, its pairs as of, to, of, to; null until it has had one. */
final ArrayList<IntArrayList> lists = new ArrayList<>();
/** Per of, a by for each pair holding it, so a by pairing it twice is there twice; null until it has had one. */
final ArrayList<IntArrayList> takers = new ArrayList<>();
/** Per to, a by for each pair holding it. */
final ArrayList<IntArrayList> givers = new ArrayList<>();


public ArrayListsOfByToComponent(ACS_Core core, ACS_Journal journal, int ofArity, int byArity, int toArity, int restrictions) {
	if(ofArity == byArity || byArity == toArity || ofArity == toArity) { throw new IllegalArgumentException("arities " + ofArity + ", " + byArity + " and " + toArity + " are not three slots"); }
	if((restrictions & FULL) != 0) { throw new IllegalArgumentException("FULL holds only where the by arity is strictly caused; this storage adds bys one at a time"); }
	if((restrictions & ~EVERY_BIT) != 0) { throw new IllegalArgumentException("restrictions " + restrictions + " hold a bit of-by-to has not"); }
	this.core = core;
	this.journal = journal;
	this.ofArity = ofArity;
	this.byArity = byArity;
	this.toArity = toArity;
	this.restrictions = restrictions;
}

/** Built by name through the core's addStructure: ofArity, byArity, toArity, restrictions read from the request, in that order; nothing is written back. */
public ArrayListsOfByToComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
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
public int pairCount(int by) { return sizeOf(listAt(lists, by)) / 2; }

/** In the order matched; undo restores the pairs but not that order. */
@Override
public void copyPairsInto(int by, int[] dest, int offset) {
	IntArrayList held = listAt(lists, by);
	if(held != null) { held.getElements(0, dest, offset, held.size()); }
}

@Override
public int takerCount(int of) { return distinct(listAt(takers, of)).length; }

@Override
public void copyTakersInto(int of, int[] dest, int offset) {
	int[] bys = distinct(listAt(takers, of));
	System.arraycopy(bys, 0, dest, offset, bys.length);
}

@Override
public int giverCount(int to) { return distinct(listAt(givers, to)).length; }

@Override
public void copyGiversInto(int to, int[] dest, int offset) {
	int[] bys = distinct(listAt(givers, to));
	System.arraycopy(bys, 0, dest, offset, bys.length);
}

@Override
public void writeTosOf(int of, DataOutputStream dos) throws IOException { writeInts(dos, partners(of, 0)); }

@Override
public void writeFromsOf(int to, DataOutputStream dos) throws IOException { writeInts(dos, partners(to, 1)); }

static void writeInts(DataOutputStream dos, int[] ints) throws IOException {
	dos.writeInt(ints.length);
	for(int identity : ints) { dos.writeInt(identity); }
}


/**
 * Every restriction is checked on the list as a whole before anything is recorded; the by is the by arity's
 * lowest free identity, captured nested in this request, and its pairs are added in their order.
 * */
@Override
public int ensureVia(int[] pairs, String comment) {
	requireLivePairs(pairs);
	if(has(NO_PARALLEL_VIAS)) {
		int held = parallelOf(-1, pairs);
		if(held >= 0) { return held; }
	}
	checkList(-1, pairs);
	int request = journal.beginRecordingActivity(this, "ensureVia", params, comment);
	int by;
	try {
		by = Math.toIntExact(core.captureIdentity(byArity, null));
		for(int at = 0; at < pairs.length; at += 2) { pair(by, pairs[at], pairs[at + 1]); }
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return by;
}

@Override
public boolean addPair(int by, int of, int to, String comment) {
	requireLive(byArity, by);
	requireLive(ofArity, of);
	requireLive(toArity, to);
	int[] before = listArray(by);
	if(has(NO_REPEATS) && lastIndexOf(before, of, to) >= 0) { return false; }
	int[] after = Arrays.copyOf(before, before.length + 2);
	after[before.length] = of;
	after[before.length + 1] = to;
	checkList(by, after);
	int request = journal.beginRecordingActivity(this, "addPair", params, comment);
	pair(by, of, to);
	complete(request);
	return true;
}

@Override
public boolean removePair(int by, int of, int to, String comment) {
	int[] before = listArray(by);
	int[] after = without(before, of, to);
	if(after.length == before.length) { return false; }
	checkList(by, after);
	int request = journal.beginRecordingActivity(this, "removePair", params, comment);
	for(int i = (before.length - after.length) / 2; i > 0; i--) { unpair(by, of, to); }
	complete(request);
	return true;
}

/** False when the pairs are the ones there: the same ones as many times. */
@Override
public boolean setPairs(int by, int[] pairs, String comment) {
	requireLive(byArity, by);
	requireLivePairs(pairs);
	int[] before = listArray(by);
	if(sameList(before, pairs)) { return false; }
	checkList(by, pairs);
	int request = journal.beginRecordingActivity(this, "setPairs", params, comment);
	for(int at = before.length - 2; at >= 0; at -= 2) { unpair(by, before[at], before[at + 1]); }
	for(int at = 0; at < pairs.length; at += 2) { pair(by, pairs[at], pairs[at + 1]); }
	complete(request);
	return true;
}

/**
 * A by leaving its arity loses its pairs; an of or a to leaving its arity is dropped from every pair holding
 * it and the bys stay, but on transitions, whose bys holding it are removed through the core.
 * */
@Override
public boolean forget(int arity, int identity) {
	if(arity == byArity) {
		int[] before = listArray(identity);
		if(before.length == 0) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", params, null);
		for(int at = before.length - 2; at >= 0; at -= 2) { unpair(identity, before[at], before[at + 1]); }
		complete(request);
		return true;
	}
	int side = arity == ofArity ? 0 : arity == toArity ? 1 : -1;
	if(side < 0) { return false; }
	int[] bys = distinct(listAt(side == 0 ? takers : givers, identity));
	if(bys.length == 0) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	for(int by : bys) {
		if(isTransitions()) {
			core.delegateRemoval(byArity, by, null);
			continue;
		}
		int[] before = listArray(by);
		for(int at = before.length - 2; at >= 0; at -= 2) { if(before[at + side] == identity) { unpair(by, before[at], before[at + 1]); } }
	}
	complete(request);
	return true;
}


/**
 * Refuses {@code list} as what {@code by} matches, -1 for a new by, where a restriction forbids it: the by's
 * own bits on the list alone, the element bits and {@link #NO_PARALLEL_VIAS} against every other by's pairs.
 * */
void checkList(int by, int[] list) {
	if(list.length == 0) {
		if(has(EVERY_VIA_MATCHES)) { throw new IllegalArgumentException("every by matches a pair"); }
		return;
	}
	LongOpenHashSet met = new LongOpenHashSet();
	for(int at = 0; at < list.length; at += 2) {
		int of = list[at], to = list[at + 1];
		if(!met.add(key(of, to))) {
			if(has(NO_REPEATS)) { throw new IllegalArgumentException("pair (" + of + ", " + to + ") twice in one by's list: no repeats"); }
			continue;
		}
		if(has(AT_MOST_ONE_PRIMARY_PER_RELATION) && of != list[0]) { throw new IllegalArgumentException("a by's pairs share one of: " + list[0] + " and " + of); }
		if(has(AT_MOST_ONE_COMPLEMENT_PER_RELATION) && to != list[1]) { throw new IllegalArgumentException("a by's pairs share one to: " + list[1] + " and " + to); }
		checkElement(by, list, of, 0);
		checkElement(by, list, to, 1);
	}
	if(has(NO_PARALLEL_VIAS)) {
		int held = parallelOf(by, list);
		if(held >= 0) { throw new IllegalArgumentException("by " + held + " matches the same pairs: no parallel bys"); }
	}
}

/**
 * The element bits for {@code identity} at {@code side}, 0 the of and 1 the to: in one by at most, and paired
 * with one partner at most, counting the pairs of {@code list} and those of every other by.
 * */
void checkElement(int by, int[] list, int identity, int side) {
	boolean oneBy = has(side == 0 ? AT_MOST_ONE_RELATION_PER_PRIMARY : AT_MOST_ONE_RELATION_PER_COMPLEMENT);
	boolean onePartner = has(side == 0 ? AT_MOST_ONE_TO_PER_FROM : AT_MOST_ONE_FROM_PER_TO);
	if(!oneBy && !onePartner) { return; }
	String role = side == 0 ? "of " : "to ";
	int partner = -1;
	for(int at = 0; at < list.length; at += 2) {
		if(list[at + side] != identity) { continue; }
		int other = list[at + 1 - side];
		if(onePartner && partner >= 0 && other != partner) { throw new IllegalArgumentException(role + identity + " is paired with " + partner + " and " + other + ": one at most"); }
		partner = other;
	}
	for(int other : distinct(listAt(side == 0 ? takers : givers, identity))) {
		if(other == by) { continue; }
		if(oneBy) { throw new IllegalArgumentException(role + identity + " is in the pairs of by " + other + " already: one by at most"); }
		IntArrayList held = lists.get(other);
		for(int at = 0; at < held.size(); at += 2) {
			if(held.getInt(at + side) == identity && held.getInt(at + 1 - side) != partner) { throw new IllegalArgumentException(role + identity + " is paired with " + held.getInt(at + 1 - side) + " by by " + other + " already: one at most"); }
		}
	}
}

/** The lowest other by matching the same pairs as {@code list}, -1 for none; a by matching no pair is parallel to none. */
int parallelOf(int by, int[] list) {
	if(list.length == 0) { return -1; }
	for(int other : distinct(listAt(takers, list[0]))) {
		if(other != by && sameList(listArray(other), list)) { return other; }
	}
	return -1;
}

/** The same pairs: the same ones as many times. */
boolean sameList(int[] held, int[] list) {
	if(held.length != list.length) { return false; }
	return Arrays.equals(sortedKeys(held), sortedKeys(list));
}


/** Adds the pair last and records it. */
void pair(int by, int of, int to) {
	pairUnchecked(by, of, to);
	params[0] = by;
	params[1] = of;
	params[2] = to;
	journal.recordActivityStep(PAIR, 3);
}

void unpair(int by, int of, int to) {
	unpairUnchecked(by, of, to);
	params[0] = by;
	params[1] = of;
	params[2] = to;
	journal.recordActivityStep((byte) -PAIR, 3);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case PAIR -> pairUnchecked(params[index], params[index + 1], params[index + 2]);
		case -PAIR -> unpairUnchecked(params[index], params[index + 1], params[index + 2]);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

/** Adds the pair last to the by's list and the by to both ends' lists. Grows what it needs to reach them. */
void pairUnchecked(int by, int of, int to) {
	IntArrayList held = grownAt(lists, by);
	held.add(of);
	held.add(to);
	grownAt(takers, of).add(by);
	grownAt(givers, to).add(by);
}

/** Removes the pair's last occurrence from the by's list, and the by once from both ends' lists; the pair must be there. */
void unpairUnchecked(int by, int of, int to) {
	IntArrayList held = lists.get(by);
	int at = held.size() - 2;
	while(held.getInt(at) != of || held.getInt(at + 1) != to) { at -= 2; }
	held.removeElements(at, at + 2);
	unindex(by, of, to);
}

/** The by's list made {@code list}, both ends' lists following; {@code list} is copied, not kept. */
void listUnchecked(int by, int[] list) {
	IntArrayList held = listAt(lists, by);
	if(held != null) {
		for(int at = 0; at < held.size(); at += 2) { unindex(by, held.getInt(at), held.getInt(at + 1)); }
		held.clear();
	}
	for(int at = 0; at < list.length; at += 2) { pairUnchecked(by, list[at], list[at + 1]); }
}

void unindex(int by, int of, int to) {
	IntArrayList fromOf = takers.get(of), intoTo = givers.get(to);
	fromOf.removeInt(fromOf.lastIndexOf(by));
	intoTo.removeInt(intoTo.lastIndexOf(by));
}


/** The three arity slots and the restrictions, then each by matching a pair, with its pairs in its list's order, so the file carries the relation and not this encoding. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(ofArity);
	dos.writeInt(byArity);
	dos.writeInt(toArity);
	dos.writeInt(restrictions);
	int count = 0;
	for(IntArrayList held : lists) { if(sizeOf(held) > 0) { count++; } }
	dos.writeInt(count);
	for(int by = 0; by < lists.size(); by++) {
		IntArrayList held = lists.get(by);
		if(sizeOf(held) == 0) { continue; }
		dos.writeInt(by);
		dos.writeInt(held.size() / 2);
		for(int i = 0; i < held.size(); i++) { dos.writeInt(held.getInt(i)); }
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses a by written twice and a list a restriction forbids. */
public static ArrayListsOfByToComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int ofArity = dis.readInt(), byArity = dis.readInt(), toArity = dis.readInt(), restrictions = dis.readInt();
	ArrayListsOfByToComponent structure;
	try { structure = new ArrayListsOfByToComponent(core, journal, ofArity, byArity, toArity, restrictions); }
	catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int by = dis.readInt(), pairCount = dis.readInt();
		if(by < 0 || pairCount <= 0) { throw new IOException("by " + by + " with " + pairCount + " pairs is not a stored by"); }
		if(structure.pairCount(by) > 0) { throw new IOException("by " + by + " is written twice"); }
		int[] list = new int[2 * pairCount];
		for(int at = 0; at < list.length; at++) {
			list[at] = dis.readInt();
			if(list[at] < 0) { throw new IOException("by " + by + " pairs a negative identity"); }
		}
		try { structure.checkList(by, list); }
		catch(IllegalArgumentException e) { throw new IOException("by " + by + ": " + e.getMessage(), e); }
		structure.listUnchecked(by, list);
	}
	return structure;
}


/** The by's pairs as a new array, empty for none. */
int[] listArray(int by) {
	IntArrayList held = listAt(lists, by);
	return held == null ? new int[0] : held.toIntArray();
}

/** The other ends of the pairs holding {@code identity} at {@code side}, 0 the of and 1 the to, each once and ascending. */
int[] partners(int identity, int side) {
	IntOpenHashSet found = new IntOpenHashSet();
	for(int by : distinct(listAt(side == 0 ? takers : givers, identity))) {
		IntArrayList held = lists.get(by);
		for(int at = 0; at < held.size(); at += 2) { if(held.getInt(at + side) == identity) { found.add(held.getInt(at + 1 - side)); } }
	}
	int[] sorted = found.toIntArray();
	Arrays.sort(sorted);
	return sorted;
}

void requireLivePairs(int[] pairs) {
	if(pairs.length % 2 != 0) { throw new IllegalArgumentException("pairs come as of, to; given " + pairs.length + " ints"); }
	for(int at = 0; at < pairs.length; at += 2) {
		requireLive(ofArity, pairs[at]);
		requireLive(toArity, pairs[at + 1]);
	}
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

/** The list's values, each once and ascending; empty for none. */
static int[] distinct(IntArrayList list) {
	if(sizeOf(list) == 0) { return new int[0]; }
	int[] sorted = list.toIntArray();
	Arrays.sort(sorted);
	int kept = 1;
	for(int i = 1; i < sorted.length; i++) { if(sorted[i] != sorted[kept - 1]) { sorted[kept++] = sorted[i]; } }
	return Arrays.copyOf(sorted, kept);
}

static long key(int of, int to) { return (long) of << 32 | (to & 0xFFFFFFFFL); }

static long[] sortedKeys(int[] list) {
	long[] keys = new long[list.length / 2];
	for(int i = 0; i < keys.length; i++) { keys[i] = key(list[2 * i], list[2 * i + 1]); }
	Arrays.sort(keys);
	return keys;
}

/** The pair's last place in {@code list}, -1 for none. */
static int lastIndexOf(int[] list, int of, int to) {
	for(int at = list.length - 2; at >= 0; at -= 2) { if(list[at] == of && list[at + 1] == to) { return at; } }
	return -1;
}

/** {@code list} without the pair, every time it is there. */
static int[] without(int[] list, int of, int to) {
	IntArrayList kept = new IntArrayList(list.length);
	for(int at = 0; at < list.length; at += 2) { if(list[at] != of || list[at + 1] != to) { kept.add(list[at]); kept.add(list[at + 1]); } }
	return kept.toIntArray();
}


/** {@link #addPair}, as primary-relation-complement names it: the primary is the of, the relation the by, the complement the to. */
@Override
public boolean relateToTernary(int primary, int relation, int complement, String comment) { return addPair(relation, primary, complement, comment); }

/** {@link #removePair}, as primary-relation-complement names it. */
@Override
public boolean unrelateFromTernary(int primary, int relation, int complement, String comment) { return removePair(relation, primary, complement, comment); }

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
public int[] tosOf(int of) { return partners(of, 0); }

/** What {@link #writeFromsOf} writes, as an int[]. */
@Override
public int[] fromsOf(int to) { return partners(to, 1); }



@Override
public ACS_Journal getJournal() { return journal; }

}

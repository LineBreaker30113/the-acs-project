package org.halim.acse.structure.ternary.primary_relation_complement;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.structure.ACS_TernaryStructure;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * The most formal ternary structure as int lists: each identity of the relation arity, at index 1, relates
 * the primaries it takes, of the arity at index 0, to the complements it gives, of the arity at index 2 - a
 * few related to a few, so neither side needs the other, and a request edits one side of one relation, or
 * both at once. Of-by-to is the restriction pairing each primary with one complement; on a directed reading
 * the primaries point to the complements, as a link's first points to its last.
 * <p>
 * A relation is any identity its arity holds, captured elsewhere. Each side is a set, kept in the order
 * taken or given: a primary is taken by a relation once at most, a complement given once at most. Per
 * primary and per complement, a list of the relations holding it, so every side reads without a scan.
 * <p>
 * A step is one primary taken or one complement given, or its inverse, each changing one thing, so undo and
 * redo replay a request's steps in any order; the order of a side is the storage's, and undo may not restore
 * it, since the journal records relations only. A relation leaving its arity loses both sides; a primary or
 * a complement leaving its arity is dropped from every relation holding it, and the relations stay. Not
 * multi thread safe.
 * */
public class ArrayListsPrimaryRelationComplementComponent implements ACS_TernaryStructure {

/** The primary taken by the relation, (primary, relation); the negative pair drops it. */
static final byte TAKE = 1;
/** The complement given by the relation, (relation, complement); the negative pair drops it. */
static final byte GIVE = 2;

final ACS_Core core;
final ACS_Journal journal;
final int primaryArity, relationArity, complementArity;
final int[] params = new int[2];

/** Per relation, the primaries it takes in the order taken; null until it has taken one. */
final ArrayList<IntArrayList> taken = new ArrayList<>();
/** Per relation, the complements it gives in the order given. */
final ArrayList<IntArrayList> given = new ArrayList<>();
/** Per primary, the relations taking it. */
final ArrayList<IntArrayList> takers = new ArrayList<>();
/** Per complement, the relations giving it. */
final ArrayList<IntArrayList> givers = new ArrayList<>();


public ArrayListsPrimaryRelationComplementComponent(ACS_Core core, ACS_Journal journal, int primaryArity, int relationArity, int complementArity) {
	if(primaryArity == relationArity || relationArity == complementArity || primaryArity == complementArity) { throw new IllegalArgumentException("arities " + primaryArity + ", " + relationArity + " and " + complementArity + " are not three slots"); }
	this.core = core;
	this.journal = journal;
	this.primaryArity = primaryArity;
	this.relationArity = relationArity;
	this.complementArity = complementArity;
}

/** Built by name through the core's addStructure: primaryArity, relationArity, complementArity read from the request, in that order; nothing is written back. */
public ArrayListsPrimaryRelationComplementComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readInt());
}


@Override
public int getArityCount() { return 3; }

@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> primaryArity;
		case 1 -> relationArity;
		case 2 -> complementArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of a primary-relation-complement over three");
	};
}

/** It keeps every set of relations, so it is the most formal case. */
public int getRestrictions() { return 0; }

/** It makes no arity, so it strictly causes none; a via it captures through the core is the client's arity's too. */
@Override
public boolean isCaused(int index) { return false; }

@Override
public boolean isRelatedArity(int identity) { return identity == primaryArity || identity == relationArity || identity == complementArity; }


/** The relation takes the primary. False when it already does; refuses an identity its arity does not hold. */
@Override
public boolean take(int primary, int relation, String comment) {
	requireLive(primaryArity, primary);
	requireLive(relationArity, relation);
	if(takes(relation, primary)) { return false; }
	int request = journal.beginRecordingActivity(this, "take", params, comment);
	record(TAKE, primary, relation);
	complete(request);
	return true;
}

/** The relation no longer takes the primary; false when it did not. */
@Override
public boolean untake(int primary, int relation, String comment) {
	if(!takes(relation, primary)) { return false; }
	int request = journal.beginRecordingActivity(this, "untake", params, comment);
	record((byte) -TAKE, primary, relation);
	complete(request);
	return true;
}

/** The relation gives the complement, as {@link #take} takes. */
@Override
public boolean give(int relation, int complement, String comment) {
	requireLive(relationArity, relation);
	requireLive(complementArity, complement);
	if(gives(relation, complement)) { return false; }
	int request = journal.beginRecordingActivity(this, "give", params, comment);
	record(GIVE, relation, complement);
	complete(request);
	return true;
}

/** The relation no longer gives the complement; false when it did not. */
@Override
public boolean ungive(int relation, int complement, String comment) {
	if(!gives(relation, complement)) { return false; }
	int request = journal.beginRecordingActivity(this, "ungive", params, comment);
	record((byte) -GIVE, relation, complement);
	complete(request);
	return true;
}

/**
 * The relation takes the primary and gives the complement, as one request, so the two are related along
 * with whatever the relation holds already. False when it did both already.
 * */
@Override
public boolean relateToTernary(int primary, int relation, int complement, String comment) {
	requireLive(primaryArity, primary);
	requireLive(relationArity, relation);
	requireLive(complementArity, complement);
	boolean taking = !takes(relation, primary), giving = !gives(relation, complement);
	if(!taking && !giving) { return false; }
	int request = journal.beginRecordingActivity(this, "relateToTernary", params, comment);
	if(taking) { record(TAKE, primary, relation); }
	if(giving) { record(GIVE, relation, complement); }
	complete(request);
	return true;
}

/** The relation drops both the primary and the complement, as one request; false when it held neither. */
@Override
public boolean unrelateFromTernary(int primary, int relation, int complement, String comment) {
	boolean taking = takes(relation, primary), giving = gives(relation, complement);
	if(!taking && !giving) { return false; }
	int request = journal.beginRecordingActivity(this, "unrelateFromTernary", params, comment);
	if(taking) { record((byte) -TAKE, primary, relation); }
	if(giving) { record((byte) -GIVE, relation, complement); }
	complete(request);
	return true;
}

/** Whether the relation takes the primary and gives the complement. */
@Override
public boolean isRelatedToTernary(int primary, int relation, int complement) { return takes(relation, primary) && gives(relation, complement); }

/**
 * What the relation takes becomes {@code primaries}, a repeat counting once: every other primary dropped,
 * then each new one taken, as one request. False when nothing changes.
 * */
@Override
public boolean setTaken(int relation, int[] primaries, String comment) {
	requireLive(relationArity, relation);
	for(int primary : primaries) { requireLive(primaryArity, primary); }
	IntOpenHashSet kept = new IntOpenHashSet(primaries);
	int[] held = takenOf(relation);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, "setTaken", params, comment);
	for(int primary : held) { if(!kept.contains(primary)) { record((byte) -TAKE, primary, relation); } }
	for(int primary : primaries) { if(had.add(primary)) { record(TAKE, primary, relation); } }
	complete(request);
	return true;
}

/** What the relation gives becomes {@code complements}, as {@link #setTaken} sets what it takes. */
@Override
public boolean setGiven(int relation, int[] complements, String comment) {
	requireLive(relationArity, relation);
	for(int complement : complements) { requireLive(complementArity, complement); }
	IntOpenHashSet kept = new IntOpenHashSet(complements);
	int[] held = givenOf(relation);
	IntOpenHashSet had = new IntOpenHashSet(held);
	if(kept.equals(had)) { return false; }
	int request = journal.beginRecordingActivity(this, "setGiven", params, comment);
	for(int complement : held) { if(!kept.contains(complement)) { record((byte) -GIVE, relation, complement); } }
	for(int complement : complements) { if(had.add(complement)) { record(GIVE, relation, complement); } }
	complete(request);
	return true;
}


/** The primaries the relation takes, in the order taken. */
@Override
public int[] takenOf(int relation) { return arrayOf(listAt(taken, relation)); }

/** The complements the relation gives, in the order given. */
@Override
public int[] givenOf(int relation) { return arrayOf(listAt(given, relation)); }

/** The relations taking the primary, ascending. */
@Override
public int[] takersOf(int primary) { return sorted(listAt(takers, primary)); }

/** The relations giving the complement, ascending. */
@Override
public int[] giversOf(int complement) { return sorted(listAt(givers, complement)); }

@Override
public int takenCount(int relation) { return sizeOf(listAt(taken, relation)); }

@Override
public int givenCount(int relation) { return sizeOf(listAt(given, relation)); }

@Override
public int takerCount(int primary) { return sizeOf(listAt(takers, primary)); }

@Override
public int giverCount(int complement) { return sizeOf(listAt(givers, complement)); }


/** A relation leaving its arity drops both sides; a primary or a complement leaving its arity is dropped from every relation holding it. */
@Override
public boolean forget(int arity, int identity) {
	if(arity == relationArity) {
		int[] primaries = takenOf(identity), complements = givenOf(identity);
		if(primaries.length == 0 && complements.length == 0) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", params, null);
		for(int primary : primaries) { record((byte) -TAKE, primary, identity); }
		for(int complement : complements) { record((byte) -GIVE, identity, complement); }
		complete(request);
		return true;
	}
	if(arity != primaryArity && arity != complementArity) { return false; }
	boolean primary = arity == primaryArity;
	int[] relations = primary ? takersOf(identity) : giversOf(identity);
	if(relations.length == 0) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	for(int relation : relations) {
		if(primary) { record((byte) -TAKE, identity, relation); }
		else { record((byte) -GIVE, relation, identity); }
	}
	complete(request);
	return true;
}


/** Applies the step and records it: {@code (primary, relation)} for a take, {@code (relation, complement)} for a give. */
void record(byte step, int first, int second) {
	params[0] = first;
	params[1] = second;
	doTraceableStep(step, params, 0);
	journal.recordActivityStep(step, 2);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	int first = params[index], second = params[index + 1];
	switch(stepPair) {
		case TAKE -> {
			grownAt(taken, second).add(first);
			grownAt(takers, first).add(second);
		}
		case -TAKE -> {
			taken.get(second).rem(first);
			takers.get(first).rem(second);
		}
		case GIVE -> {
			grownAt(given, first).add(second);
			grownAt(givers, second).add(first);
		}
		case -GIVE -> {
			given.get(first).rem(second);
			givers.get(second).rem(first);
		}
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}


/** The three arity slots, then every take as (primary, relation) and every give as (relation, complement), relation by relation, so the file carries the relation and not this encoding. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(primaryArity);
	dos.writeInt(relationArity);
	dos.writeInt(complementArity);
	writeSide(dos, taken, true);
	writeSide(dos, given, false);
	return true;
}

void writeSide(DataOutputStream dos, ArrayList<IntArrayList> side, boolean primaryFirst) throws IOException {
	int count = 0;
	for(IntArrayList held : side) { count += sizeOf(held); }
	dos.writeInt(count);
	for(int relation = 0; relation < side.size(); relation++) {
		IntArrayList held = side.get(relation);
		for(int i = 0; i < sizeOf(held); i++) {
			dos.writeInt(primaryFirst ? held.getInt(i) : relation);
			dos.writeInt(primaryFirst ? relation : held.getInt(i));
		}
	}
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses a negative identity and a take or a give written twice. */
public static ArrayListsPrimaryRelationComplementComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int primaryArity = dis.readInt(), relationArity = dis.readInt(), complementArity = dis.readInt();
	ArrayListsPrimaryRelationComplementComponent structure;
	try { structure = new ArrayListsPrimaryRelationComplementComponent(core, journal, primaryArity, relationArity, complementArity); }
	catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
	for(byte step : new byte[] { TAKE, GIVE }) {
		for(int i = 0, count = dis.readInt(); i < count; i++) {
			int first = dis.readInt(), second = dis.readInt();
			if(first < 0 || second < 0) { throw new IOException("(" + first + ", " + second + ") has a negative identity"); }
			if(step == TAKE ? structure.takes(second, first) : structure.gives(first, second)) { throw new IOException("(" + first + ", " + second + ") is written twice"); }
			structure.doTraceableStep(step, new int[] { first, second }, 0);
		}
	}
	return structure;
}





boolean takes(int relation, int primary) {
	IntArrayList held = listAt(taken, relation);
	return held != null && held.contains(primary);
}

boolean gives(int relation, int complement) {
	IntArrayList held = listAt(given, relation);
	return held != null && held.contains(complement);
}

void requireLive(int arity, int identity) {
	if(identity < 0 || !core.getArity(arity).occupied().contains(identity)) {
		throw new IllegalArgumentException(identity + " is not an occupied identity of arity " + arity);
	}
}

void complete(int request) {
	int completed = journal.completeRecordingActivity();
	if(completed != request) { throw new IllegalStateException("began request " + request + ", completed " + completed); }
}

static IntArrayList listAt(ArrayList<IntArrayList> lists, int at) { return at >= 0 && at < lists.size() ? lists.get(at) : null; }

static int sizeOf(IntArrayList list) { return list == null ? 0 : list.size(); }

static int[] arrayOf(IntArrayList list) { return list == null ? new int[0] : list.toIntArray(); }

static int[] sorted(IntArrayList list) {
	int[] values = arrayOf(list);
	Arrays.sort(values);
	return values;
}

static IntArrayList grownAt(ArrayList<IntArrayList> lists, int at) {
	while(lists.size() <= at) { lists.add(null); }
	IntArrayList held = lists.get(at);
	if(held == null) {
		held = new IntArrayList();
		lists.set(at, held);
	}
	return held;
}

@Override
public ACS_Journal getJournal() { return journal; }

}

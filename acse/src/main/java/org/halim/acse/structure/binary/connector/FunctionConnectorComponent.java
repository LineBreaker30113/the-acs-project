package org.halim.acse.structure.binary.connector;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.structure.ACS_BinaryStructure;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.halim.acse.structure.BinaryRestrictions.*;


/**
 * A function from the primary arity to the complement: each primary connected to one complement at most.
 * The primary's side is one int a primary, so its complement is a lookup; the complement's side is a list a
 * complement of the primaries connected to it, so both directions read without a scan, as on
 * {@link DoubleArrayedConnectorComponent}. It takes the kinds with {@link #AT_MOST_ONE_COMPLEMENT} and
 * {@link #NO_REPEATS}, since one int holds neither a second complement nor a repeat, and keeps the other
 * restrictions with checks.
 * <p>
 * The int holds the complement plus one, 0 for none, and a connection made or broken flips it by that
 * value. Undo replays a request's steps in the order recorded, so a move - a break, then a connection, on
 * one primary - undoes only if each step is its own inverse whatever the order, as the arity's writes are
 * kept as {@code old ^ new}. Not multi thread safe.
 * */
public class FunctionConnectorComponent implements ACS_BinaryStructure {

/** The connection (primary, complement); the negative pair breaks it. */
static final byte CONNECT = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = ACS_Actor.requestNames(FunctionConnectorComponent.class);
static final int EVERY_BIT = AT_MOST_ONE_COMPLEMENT | AT_MOST_ONE_PRIMARY | EVERY_PRIMARY_CONNECTED | EVERY_COMPLEMENT_CONNECTED | NO_REPEATS;

final ACS_Core core;
final ACS_Journal journal;
final int primaryArity, complementArity, restrictions;
final int[] params = new int[2];

/** Per primary, its complement plus one, 0 for none. */
final IntArrayList complements = new IntArrayList();
/** Per complement, its primaries in the order connected; null until it has had one. */
final ArrayList<IntArrayList> primaries = new ArrayList<>();


public FunctionConnectorComponent(ACS_Core core, ACS_Journal journal, int primaryArity, int complementArity, int restrictions) {
	if(primaryArity == complementArity) { throw new IllegalArgumentException("primaries and complements in one arity " + primaryArity + ": a relation within one arity is a unary graph"); }
	if((restrictions & ~EVERY_BIT) != 0) { throw new IllegalArgumentException("restrictions " + restrictions + " hold a bit no connector has"); }
	if((restrictions & (AT_MOST_ONE_COMPLEMENT | NO_REPEATS)) != (AT_MOST_ONE_COMPLEMENT | NO_REPEATS)) { throw new IllegalArgumentException("a function is built with AT_MOST_ONE_COMPLEMENT and NO_REPEATS; given " + restrictions); }
	this.core = core;
	this.journal = journal;
	this.primaryArity = primaryArity;
	this.complementArity = complementArity;
	this.restrictions = restrictions;
}

/** Built by name through the core's addStructure: primaryArity, complementArity, restrictions read from the request, in that order; nothing is written back. */
public FunctionConnectorComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
	this(core, journal, parameters.readInt(), parameters.readInt(), parameters.readInt());
}


@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> primaryArity;
		case 1 -> complementArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of a connector over two");
	};
}

public int getRestrictions() { return restrictions; }

/** It makes no arity, so it strictly causes none. */
@Override
public boolean isCaused(int index) { return false; }


@Override
public boolean isRelatedToBinary(int primary, int complement) { return complement >= 0 && complementOf(primary) == complement; }

@Override
public int complementCount(int primary) { return complementOf(primary) >= 0 ? 1 : 0; }

@Override
public int primaryCount(int complement) {
	IntArrayList held = primariesOf(complement);
	return held == null ? 0 : held.size();
}

@Override
public void copyComplementsInto(int primary, int[] dest, int offset) {
	int complement = complementOf(primary);
	if(complement >= 0) { dest[offset] = complement; }
}

/** In the order connected, which undo may not restore: the journal records relations only. */
@Override
public void copyPrimariesInto(int complement, int[] dest, int offset) {
	IntArrayList held = primariesOf(complement);
	if(held != null) { held.getElements(0, dest, offset, held.size()); }
}


@Override
public boolean relateToBinary(int primary, int complement, String comment) {
	requireLive(primaryArity, primary);
	requireLive(complementArity, complement);
	int held = complementOf(primary);
	if(held == complement) { return false; }
	if(held >= 0) { throw new IllegalArgumentException(primary + " is connected to " + held + " already: a function's primary has one complement at most"); }
	if(has(AT_MOST_ONE_PRIMARY) && primaryCount(complement) > 0) { throw new IllegalArgumentException(complement + " is connected already: one primary at most"); }
	int request = journal.beginRecordingActivity(this, "relateToBinary", params, comment);
	connect(primary, complement);
	complete(request);
	return true;
}

@Override
public boolean unrelateFromBinary(int primary, int complement, String comment) {
	if(!isRelatedToBinary(primary, complement)) { return false; }
	if(has(EVERY_PRIMARY_CONNECTED)) { throw new IllegalArgumentException("every primary is connected, and " + complement + " is " + primary + "'s only complement"); }
	if(has(EVERY_COMPLEMENT_CONNECTED) && primaryCount(complement) == 1) { throw new IllegalArgumentException("every complement is connected, and " + primary + " is " + complement + "'s only primary"); }
	int request = journal.beginRecordingActivity(this, "unrelateFromBinary", params, comment);
	disconnect(primary, complement);
	complete(request);
	return true;
}

/** A move from the complement it has is a break and a connection, recorded as two steps of one request. */
@Override
public boolean setComplement(int primary, int complement, String comment) {
	requireLive(primaryArity, primary);
	requireLive(complementArity, complement);
	int held = complementOf(primary);
	if(held == complement) { return false; }
	if(has(AT_MOST_ONE_PRIMARY) && primaryCount(complement) > 0) { throw new IllegalArgumentException(complement + " is connected already: one primary at most"); }
	if(held >= 0 && has(EVERY_COMPLEMENT_CONNECTED) && primaryCount(held) == 1) { throw new IllegalArgumentException("every complement is connected, and " + primary + " is " + held + "'s only primary"); }
	int request = journal.beginRecordingActivity(this, "setComplement", params, comment);
	if(held >= 0) { disconnect(primary, held); }
	connect(primary, complement);
	complete(request);
	return true;
}

/** Refuses a primary connected elsewhere, which would hold two complements. */
@Override
public boolean setPrimary(int complement, int primary, String comment) {
	requireLive(complementArity, complement);
	requireLive(primaryArity, primary);
	int held = complementOf(primary);
	if(held >= 0 && held != complement) { throw new IllegalArgumentException(primary + " is connected to " + held + " already: a function's primary has one complement at most"); }
	IntArrayList others = primariesOf(complement) == null ? new IntArrayList() : new IntArrayList(primariesOf(complement));
	others.rem(primary);
	if(held == complement && others.isEmpty()) { return false; }
	if(!others.isEmpty() && has(EVERY_PRIMARY_CONNECTED)) { throw new IllegalArgumentException("every primary is connected, and " + complement + " is " + others.getInt(0) + "'s only complement"); }
	int request = journal.beginRecordingActivity(this, "setPrimary", params, comment);
	for(int i = others.size() - 1; i >= 0; i--) { disconnect(others.getInt(i), complement); }
	if(held != complement) { connect(primary, complement); }
	complete(request);
	return true;
}

/** Breaks every connection the identity has on the side its arity is. */
@Override
public boolean forget(int arity, int identity) {
	if(arity == primaryArity) {
		int held = complementOf(identity);
		if(held < 0) { return false; }
		int request = journal.beginRecordingActivity(this, "forget", params, null);
		disconnect(identity, held);
		complete(request);
		return true;
	}
	if(arity != complementArity || primaryCount(identity) == 0) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	IntArrayList held = primariesOf(identity);
	while(!held.isEmpty()) { disconnect(held.getInt(held.size() - 1), identity); }
	complete(request);
	return true;
}


/** Connects and records it. */
void connect(int primary, int complement) {
	connectUnchecked(primary, complement);
	params[0] = primary;
	params[1] = complement;
	journal.recordActivityStep(CONNECT, 2);
}

void disconnect(int primary, int complement) {
	disconnectUnchecked(primary, complement);
	params[0] = primary;
	params[1] = complement;
	journal.recordActivityStep((byte) -CONNECT, 2);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case CONNECT -> connectUnchecked(params[index], params[index + 1]);
		case -CONNECT -> disconnectUnchecked(params[index], params[index + 1]);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

/** Flips the primary's int by the complement and lists the primary under it. Grows either side to reach them. */
void connectUnchecked(int primary, int complement) {
	if(complements.size() <= primary) { complements.size(primary + 1); }
	complements.set(primary, complements.getInt(primary) ^ (complement + 1));
	while(primaries.size() <= complement) { primaries.add(null); }
	if(primaries.get(complement) == null) { primaries.set(complement, new IntArrayList()); }
	primaries.get(complement).add(primary);
}

/** The same flip, and the primary off the complement's list; the two must be connected. */
void disconnectUnchecked(int primary, int complement) {
	complements.set(primary, complements.getInt(primary) ^ (complement + 1));
	IntArrayList held = primaries.get(complement);
	held.removeInt(held.lastIndexOf(primary));
}


/** The two arity slots and the restrictions, then the connections as (primary, complement) pairs, primary by primary. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(primaryArity);
	dos.writeInt(complementArity);
	dos.writeInt(restrictions);
	int count = 0;
	for(int primary = 0; primary < complements.size(); primary++) { if(complements.getInt(primary) != 0) { count++; } }
	dos.writeInt(count);
	for(int primary = 0; primary < complements.size(); primary++) {
		if(complements.getInt(primary) == 0) { continue; }
		dos.writeInt(primary);
		dos.writeInt(complements.getInt(primary) - 1);
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses a primary written twice, and a complement given a second primary where one is the most. */
public static FunctionConnectorComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int primaryArity = dis.readInt(), complementArity = dis.readInt(), restrictions = dis.readInt();
	FunctionConnectorComponent function;
	try { function = new FunctionConnectorComponent(core, journal, primaryArity, complementArity, restrictions); }
	catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int primary = dis.readInt(), complement = dis.readInt();
		if(primary < 0 || complement < 0) { throw new IOException("connection (" + primary + ", " + complement + ") has a negative identity"); }
		if(function.complementOf(primary) >= 0) { throw new IOException("primary " + primary + " is written twice"); }
		if(function.has(AT_MOST_ONE_PRIMARY) && function.primaryCount(complement) > 0) { throw new IOException("complement " + complement + " has two primaries, one at most"); }
		function.connectUnchecked(primary, complement);
	}
	return function;
}


/** The primary's complement, -1 for none. */
int complementOf(int primary) { return primary >= 0 && primary < complements.size() ? complements.getInt(primary) - 1 : -1; }
IntArrayList primariesOf(int complement) { return complement >= 0 && complement < primaries.size() ? primaries.get(complement) : null; }

boolean has(int restriction) { return (restrictions & restriction) != 0; }

void requireLive(int arity, int identity) {
	if(identity < 0 || !core.getArity(arity).occupied().contains(identity)) {
		throw new IllegalArgumentException(identity + " is not an occupied identity of arity " + arity);
	}
}

void complete(int request) {
	int completed = journal.completeRecordingActivity();
	if(completed != request) { throw new IllegalStateException("began request " + request + ", completed " + completed); }
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



/** The element's clusters become {@code clusters}, a repeat counting once: one at most, so this is {@link #setComplement}, or {@link #unrelateFromBinary} for none. False when nothing changes. */
@Override
public boolean setComplementsOf(int element, int[] clusters, String comment) {
	int[] wanted = new IntOpenHashSet(clusters).toIntArray();
	if(wanted.length > 1) { throw new IllegalArgumentException(element + " given " + wanted.length + " clusters: a primary has one complement at most"); }
	if(wanted.length == 1) { return setComplement(element, wanted[0], comment); }
	int held = complementOf(element);
	return held >= 0 && unrelateFromBinary(element, held, comment);
}

/**
 * The cluster's elements become {@code elements}, a repeat counting once: every other one broken, then
 * each new one connected, as one request, refused for what the new list breaks and never for what breaks
 * only midway. Refuses an element connected elsewhere, which would hold two. False when nothing changes.
 * */
@Override
public boolean setPrimariesOf(int cluster, int[] elements, String comment) {
	requireLive(complementArity, cluster);
	IntOpenHashSet wanted = new IntOpenHashSet(elements);
	for(int element : elements) {
		requireLive(primaryArity, element);
		int held = complementOf(element);
		if(held >= 0 && held != cluster) { throw new IllegalArgumentException(element + " is connected to " + held + " already: a function's primary has one complement at most"); }
	}
	int[] held = getPrimariesOf(cluster);
	if(wanted.equals(new IntOpenHashSet(held))) { return false; }
	if(has(AT_MOST_ONE_PRIMARY) && wanted.size() > 1) { throw new IllegalArgumentException(cluster + " given " + wanted.size() + " elements: one primary at most"); }
	if(has(EVERY_COMPLEMENT_CONNECTED) && wanted.isEmpty()) { throw new IllegalArgumentException("every complement is connected, and " + cluster + " would have none"); }
	for(int element : held) { if(!wanted.contains(element) && has(EVERY_PRIMARY_CONNECTED)) { throw new IllegalArgumentException("every primary is connected, and " + cluster + " is " + element + "'s only complement"); } }
	int request = journal.beginRecordingActivity(this, "setPrimariesOf", params, comment);
	for(int element : held) { if(!wanted.contains(element)) { disconnect(element, cluster); } }
	for(int element : elements) { if(!isRelatedToBinary(element, cluster)) { connect(element, cluster); } }
	complete(request);
	return true;
}


/**
 * The requests, by the codes {@link ACS_BinaryStructure} marks: each reads its inputs, calls the public
 * method, which keeps its checks, and writes the result. A read is recorded as a query: its method's name
 * and inputs, then its answer, or its refusal; a list is answered by its length.
 * */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	String query = null;
	try {
		switch(code) {
			case 0 -> returns.writeBoolean(relateToBinary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 1 -> returns.writeBoolean(unrelateFromBinary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 2 -> {
				int primary = parameters.readInt(), complement = parameters.readInt();
				query = REQUESTS[code] + "(" + primary + ", " + complement + ")";
				boolean value = isRelatedToBinary(primary, complement);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 3 -> returns.writeBoolean(setComplement(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 4 -> returns.writeBoolean(setPrimary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 5 -> returns.writeBoolean(setComplementsOf(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 6 -> returns.writeBoolean(setPrimariesOf(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 7 -> returns.writeBoolean(moveBinary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 8 -> returns.writeBoolean(mergeBinary(parameters.readInt(), parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 9, 10 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int value = code == 9 ? complementCount(identity) : primaryCount(identity);
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 11, 12 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int[] value = code == 11 ? getComplementsOf(identity) : getPrimariesOf(identity);
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

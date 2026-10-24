package org.halim.acse.structure.binary.connector;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.structure.binary.connector.ACS_ConnectorComponentBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;


/**
 * A bijective function between the primary arity and the complement: each primary matched with one
 * complement at most and each complement with one primary, kept as two int lists, one a side, so each
 * direction is a lookup and a match costs an int on both ends. It takes the kinds with
 * {@link #AT_MOST_ONE_COMPLEMENT}, {@link #AT_MOST_ONE_PRIMARY} and {@link #NO_REPEATS}, and keeps
 * {@link #EVERY_PRIMARY_CONNECTED} and {@link #EVERY_COMPLEMENT_CONNECTED} with checks, each counted from an
 * identity's first match, so neither makes the function total over a growing arity.
 * <p>
 * Each int holds the other side plus one, 0 for none, and a match made or broken flips both by those
 * values, so every step is its own inverse whatever the order undo replays it in, as on
 * {@link FunctionConnectorComponent}. Not multi thread safe.
 * */
public class BijectiveFunctionConnectorComponent implements ACS_ConnectorComponentBase {

/** The match (primary, complement); the negative pair breaks it, by the same flips. */
static final byte CONNECT = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = requestNames(BijectiveFunctionConnectorComponent.class);
static final int EVERY_BIT = AT_MOST_ONE_COMPLEMENT | AT_MOST_ONE_PRIMARY | EVERY_PRIMARY_CONNECTED | EVERY_COMPLEMENT_CONNECTED | NO_REPEATS;
static final int REQUIRED = AT_MOST_ONE_COMPLEMENT | AT_MOST_ONE_PRIMARY | NO_REPEATS;

final ACS_Core core;
final ACS_Journal journal;
final int primaryArity, complementArity, restrictions;
final int[] params = new int[2];

/** Per primary, its complement plus one, 0 for none. */
final IntArrayList complements = new IntArrayList();
/** Per complement, its primary plus one, 0 for none. */
final IntArrayList primaries = new IntArrayList();


public BijectiveFunctionConnectorComponent(ACS_Core core, ACS_Journal journal, int primaryArity, int complementArity, int restrictions) {
	if(primaryArity == complementArity) { throw new IllegalArgumentException("primaries and complements in one arity " + primaryArity + ": a relation within one arity is a unary graph"); }
	if((restrictions & ~EVERY_BIT) != 0) { throw new IllegalArgumentException("restrictions " + restrictions + " hold a bit no connector has"); }
	if((restrictions & REQUIRED) != REQUIRED) { throw new IllegalArgumentException("a bijective function is built with AT_MOST_ONE_COMPLEMENT, AT_MOST_ONE_PRIMARY and NO_REPEATS; given " + restrictions); }
	this.core = core;
	this.journal = journal;
	this.primaryArity = primaryArity;
	this.complementArity = complementArity;
	this.restrictions = restrictions;
}

/** Built by name through the core's addStructure: primaryArity, complementArity, restrictions read from the request, in that order; nothing is written back. */
public BijectiveFunctionConnectorComponent(ACS_Core core, ACS_Journal journal, DataInputStream parameters, DataOutputStream returns) throws IOException {
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

@Override
public int getRestrictions() { return restrictions; }


@ACS_Requestable(0)
@Override
public boolean isConnected(int primary, int complement) { return complement >= 0 && complementOf(primary) == complement; }

@ACS_Requestable(5)
@Override
public int complementCount(int primary) { return complementOf(primary) >= 0 ? 1 : 0; }

@ACS_Requestable(6)
@Override
public int primaryCount(int complement) { return primaryOf(complement) >= 0 ? 1 : 0; }

@Override
public void copyComplementsInto(int primary, int[] dest, int offset) {
	int complement = complementOf(primary);
	if(complement >= 0) { dest[offset] = complement; }
}

@Override
public void copyPrimariesInto(int complement, int[] dest, int offset) {
	int primary = primaryOf(complement);
	if(primary >= 0) { dest[offset] = primary; }
}


@ACS_Requestable(1)
@Override
public boolean ensureConnection(int primary, int complement, String comment) {
	requireLive(primaryArity, primary);
	requireLive(complementArity, complement);
	int held = complementOf(primary);
	if(held == complement) { return false; }
	if(held >= 0) { throw new IllegalArgumentException(primary + " is matched with " + held + " already: one complement at most"); }
	if(primaryOf(complement) >= 0) { throw new IllegalArgumentException(complement + " is matched with " + primaryOf(complement) + " already: one primary at most"); }
	int request = journal.beginRecordingActivity(this, "ensureConnection", params, comment);
	connect(primary, complement);
	complete(request);
	return true;
}

@ACS_Requestable(2)
@Override
public boolean breakConnection(int primary, int complement, String comment) {
	if(!isConnected(primary, complement)) { return false; }
	if(has(EVERY_PRIMARY_CONNECTED)) { throw new IllegalArgumentException("every primary is connected, and " + complement + " is " + primary + "'s only complement"); }
	if(has(EVERY_COMPLEMENT_CONNECTED)) { throw new IllegalArgumentException("every complement is connected, and " + primary + " is " + complement + "'s only primary"); }
	int request = journal.beginRecordingActivity(this, "breakConnection", params, comment);
	disconnect(primary, complement);
	complete(request);
	return true;
}

/** Refuses a complement matched with another primary, which would hold two. */
@ACS_Requestable(3)
@Override
public boolean setComplement(int primary, int complement, String comment) {
	requireLive(primaryArity, primary);
	requireLive(complementArity, complement);
	int held = complementOf(primary);
	if(held == complement) { return false; }
	if(primaryOf(complement) >= 0) { throw new IllegalArgumentException(complement + " is matched with " + primaryOf(complement) + " already: one primary at most"); }
	if(held >= 0 && has(EVERY_COMPLEMENT_CONNECTED)) { throw new IllegalArgumentException("every complement is connected, and " + primary + " is " + held + "'s only primary"); }
	int request = journal.beginRecordingActivity(this, "setComplement", params, comment);
	if(held >= 0) { disconnect(primary, held); }
	connect(primary, complement);
	complete(request);
	return true;
}

/** Refuses a primary matched with another complement, which would hold two. */
@ACS_Requestable(4)
@Override
public boolean setPrimary(int complement, int primary, String comment) {
	requireLive(complementArity, complement);
	requireLive(primaryArity, primary);
	int held = primaryOf(complement);
	if(held == primary) { return false; }
	if(complementOf(primary) >= 0) { throw new IllegalArgumentException(primary + " is matched with " + complementOf(primary) + " already: one complement at most"); }
	if(held >= 0 && has(EVERY_PRIMARY_CONNECTED)) { throw new IllegalArgumentException("every primary is connected, and " + complement + " is " + held + "'s only complement"); }
	int request = journal.beginRecordingActivity(this, "setPrimary", params, comment);
	if(held >= 0) { disconnect(held, complement); }
	connect(primary, complement);
	complete(request);
	return true;
}

/** Breaks the identity's match on the side its arity is. */
@Override
public boolean forget(int arity, int identity) {
	int primary, complement;
	if(arity == primaryArity) {
		primary = identity;
		complement = complementOf(identity);
	} else if(arity == complementArity) {
		primary = primaryOf(identity);
		complement = identity;
	} else { return false; }
	if(primary < 0 || complement < 0) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	disconnect(primary, complement);
	complete(request);
	return true;
}


/** Matches and records it. */
void connect(int primary, int complement) {
	flip(primary, complement);
	params[0] = primary;
	params[1] = complement;
	journal.recordActivityStep(CONNECT, 2);
}

void disconnect(int primary, int complement) {
	flip(primary, complement);
	params[0] = primary;
	params[1] = complement;
	journal.recordActivityStep((byte) -CONNECT, 2);
}

/** A match and its break are the same flips, so the step's sign does not matter here. */
@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	if(stepPair != CONNECT && stepPair != -CONNECT) { throw new IllegalArgumentException("no step " + stepPair); }
	flip(params[index], params[index + 1]);
}

/** Flips each side's int by the other side plus one. Grows either side to reach them. */
void flip(int primary, int complement) {
	if(complements.size() <= primary) { complements.size(primary + 1); }
	if(primaries.size() <= complement) { primaries.size(complement + 1); }
	complements.set(primary, complements.getInt(primary) ^ (complement + 1));
	primaries.set(complement, primaries.getInt(complement) ^ (primary + 1));
}


/** The two arity slots and the restrictions, then the matches as (primary, complement) pairs, primary by primary. */
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

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses an identity written twice on either side. */
public static BijectiveFunctionConnectorComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int primaryArity = dis.readInt(), complementArity = dis.readInt(), restrictions = dis.readInt();
	BijectiveFunctionConnectorComponent bijection;
	try { bijection = new BijectiveFunctionConnectorComponent(core, journal, primaryArity, complementArity, restrictions); }
	catch(IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
	for(int i = 0, count = dis.readInt(); i < count; i++) {
		int primary = dis.readInt(), complement = dis.readInt();
		if(primary < 0 || complement < 0) { throw new IOException("match (" + primary + ", " + complement + ") has a negative identity"); }
		if(bijection.complementOf(primary) >= 0 || bijection.primaryOf(complement) >= 0) { throw new IOException("match (" + primary + ", " + complement + ") meets one written before"); }
		bijection.flip(primary, complement);
	}
	return bijection;
}


/** The primary's complement, -1 for none. */
int complementOf(int primary) { return primary >= 0 && primary < complements.size() ? complements.getInt(primary) - 1 : -1; }
/** The complement's primary, -1 for none. */
int primaryOf(int complement) { return complement >= 0 && complement < primaries.size() ? primaries.getInt(complement) - 1 : -1; }

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


/** {@link #ensureConnection}, as elements-clusters names it: the element is the primary, the cluster the complement. */
@ACS_Requestable(7)
@Override
public boolean cluster(int element, int cluster, String comment) { return ensureConnection(element, cluster, comment); }

/** {@link #breakConnection}, as elements-clusters names it. */
@ACS_Requestable(8)
@Override
public boolean uncluster(int element, int cluster, String comment) { return breakConnection(element, cluster, comment); }

/** {@link #isConnected}, as elements-clusters names it. */
@ACS_Requestable(9)
@Override
public boolean isClustered(int element, int cluster) { return isConnected(element, cluster); }

/** The element's clusters, in the order {@link #copyComplementsInto} gives them, for the outside: no request fills the caller's array. */
@ACS_Requestable(10)
@Override
public int[] clustersOf(int element) {
	int[] clusters = new int[complementCount(element)];
	copyComplementsInto(element, clusters, 0);
	return clusters;
}

/** The cluster's elements, in the order {@link #copyPrimariesInto} gives them. */
@ACS_Requestable(11)
@Override
public int[] elementsOf(int cluster) {
	int[] elements = new int[primaryCount(cluster)];
	copyPrimariesInto(cluster, elements, 0);
	return elements;
}

@ACS_Requestable(12)
@Override
public int clusterCount(int element) { return complementCount(element); }

@ACS_Requestable(13)
@Override
public int elementCount(int cluster) { return primaryCount(cluster); }

/** The element's clusters become {@code clusters}, a repeat counting once: one at most, so this is {@link #setComplement}, or {@link #breakConnection} for none. False when nothing changes. */
@ACS_Requestable(14)
@Override
public boolean setClusters(int element, int[] clusters, String comment) {
	int[] wanted = new IntOpenHashSet(clusters).toIntArray();
	if(wanted.length > 1) { throw new IllegalArgumentException(element + " given " + wanted.length + " clusters: a primary has one complement at most"); }
	if(wanted.length == 1) { return setComplement(element, wanted[0], comment); }
	int held = complementOf(element);
	return held >= 0 && breakConnection(element, held, comment);
}

/** As {@link #setClusters}, from the cluster's side: {@link #setPrimary}, or {@link #breakConnection} for none. */
@ACS_Requestable(15)
@Override
public boolean setElements(int cluster, int[] elements, String comment) {
	int[] wanted = new IntOpenHashSet(elements).toIntArray();
	if(wanted.length > 1) { throw new IllegalArgumentException(cluster + " given " + wanted.length + " elements: a complement has one primary at most"); }
	if(wanted.length == 1) { return setPrimary(cluster, wanted[0], comment); }
	int held = primaryOf(cluster);
	return held >= 0 && breakConnection(held, cluster, comment);
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
			case 0, 9 -> {
				int primary = parameters.readInt(), complement = parameters.readInt();
				query = REQUESTS[code] + "(" + primary + ", " + complement + ")";
				boolean value = code == 0 ? isConnected(primary, complement) : isClustered(primary, complement);
				recordQuery(query, Boolean.toString(value));
				returns.writeBoolean(value);
			}
			case 1 -> returns.writeBoolean(ensureConnection(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 2 -> returns.writeBoolean(breakConnection(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 3 -> returns.writeBoolean(setComplement(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 4 -> returns.writeBoolean(setPrimary(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 5, 6, 12, 13 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int value = switch(code) {
					case 5 -> complementCount(identity);
					case 6 -> primaryCount(identity);
					case 12 -> clusterCount(identity);
					default -> elementCount(identity);
				};
				recordQuery(query, Integer.toString(value));
				returns.writeInt(value);
			}
			case 7 -> returns.writeBoolean(cluster(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 8 -> returns.writeBoolean(uncluster(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters)));
			case 10, 11 -> {
				int identity = parameters.readInt();
				query = REQUESTS[code] + "(" + identity + ")";
				int[] value = code == 10 ? clustersOf(identity) : elementsOf(identity);
				recordQuery(query, "int[" + value.length + "]");
				writeInts(returns, value);
			}
			case 14 -> returns.writeBoolean(setClusters(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
			case 15 -> returns.writeBoolean(setElements(parameters.readInt(), readInts(parameters), ACS_Actor.readString(parameters)));
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

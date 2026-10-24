package org.halim.acse.structure.binary.connector;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.structure.binary.connector.ACS_ConnectorComponentBase;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.RoaringBitmap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;


/**
 * The indexer as one Roaring bitmap: every possible connection maps to an int index, and the index is in
 * the bitmap exactly when the connection exists. The persistent arity, at index 0, is the one whose size
 * is not likely to change, and that size, given at construction, is the stride: the index is
 * {@code volatile * persistentSize + persistent}, so persistent is {@code index % persistentSize} and
 * volatile is {@code index / persistentSize}. The volatile arity, at index 1, grows without moving an
 * index. The persistent one is not promised fixed, only less volatile than the volatile one, so a
 * connection that does not fit - a persistent identity at or past the stride, or an index past an int -
 * makes the check calculate the stride again, as the smaller of the largest that keeps every index in an
 * int and 1.3 times the persistent side's size, and move every index to it: one pass over the connections,
 * with room left for the persistent side to grow before the next. A connection no stride holds - the two
 * sides' sizes multiplied past 2^31 - is refused as a value that does not fit.
 * <p>
 * The volatile side is the cheap one. A volatile identity's connections are one run of
 * {@code persistentSize} indexes, so counting them is one range count, listing them is one walk from the
 * start of the run, ascending, and forgetting it walks the same run. A persistent identity's connections lie
 * one per run, so counting or listing them probes every volatile identity up to the highest connected one.
 * A relation read often from the persistent side wants a two-sided form, as
 * {@link DoubleArrayedConnectorComponent} is.
 * <p>
 * What it buys is space and speed for a relation that fits: the bitmap is the 32-bit one, and the runs sit
 * with no gap between them, so a small relation is a few of Roaring's containers - each 2^16 indexes, kept
 * as whichever of an array, a bitmap or a list of runs is smallest - and a dense block costs a bit or less
 * a connection. There is one structure, so there is no second side to keep in step. Not multi thread safe.
 * */
public class SingleRoaringConnectorComponent implements ACS_ConnectorComponentBase {

/** The connection (persistent, volatile); the negative pair breaks it. */
static final byte CONNECT = 1;
/** Each request's method name by its code, read once from the marks, for the queries a read records. */
static final String[] REQUESTS = requestNames(SingleRoaringConnectorComponent.class);

final ACS_Core core;
final ACS_Journal journal;
/** The slots of the arities at index 0 and 1; they may be the same slot. */
final int persistentArity, volatileArity;
/** The stride: every connected persistent identity is below it. Written only through {@link #setPersistentSize}, which moves the indexes with it, and which a connection that does not fit calls. */
int persistentSize;
final int[] params = new int[2];

/** An index per existing connection. */
RoaringBitmap connections = new RoaringBitmap();


public SingleRoaringConnectorComponent(ACS_Core core, ACS_Journal journal, int persistentArity, int volatileArity, int persistentSize) {
	if(persistentSize < 1) { throw new IllegalArgumentException("persistent size " + persistentSize); }
	this.core = core;
	this.journal = journal;
	this.persistentArity = persistentArity;
	this.volatileArity = volatileArity;
	this.persistentSize = persistentSize;
}


@Override
public int getArityIdentity(int index) {
	return switch(index) {
		case 0 -> persistentArity;
		case 1 -> volatileArity;
		default -> throw new IndexOutOfBoundsException("arity " + index + " of an indexer over two");
	};
}

/** One index holds one connection, so none repeats; no other restriction is kept. */
@Override
public int getRestrictions() { return NO_REPEATS; }

public int getPersistentSize() { return persistentSize; }

/**
 * Changes the stride, moving every connection to its index under the new one: a new bitmap, built in one
 * pass and put in place only when every connection fits it, so a refusal leaves the connector as it was.
 * Refused when a connected persistent identity is at or past the new size, or an index would pass an int.
 * <p>
 * Not journaled: the stride is how the relation is represented, not the relation, and every step records
 * its connection as a (persistent, volatile) pair rather than as an index, so undo and redo replay under
 * whatever stride holds when they run.
 * */
public void setPersistentSize(int persistentSize) {
	if(persistentSize < 1) { throw new IllegalArgumentException("persistent size " + persistentSize); }
	RoaringBitmap moved = new RoaringBitmap();
	IntIterator walk = connections.getIntIterator();
	while(walk.hasNext()) {
		int index = walk.next();
		int persistentIdentity = index % this.persistentSize, volatileIdentity = index / this.persistentSize;
		if(persistentIdentity >= persistentSize) { throw new IllegalArgumentException("persistent " + persistentIdentity + " is connected, past the size " + persistentSize); }
		long next = (long) volatileIdentity * persistentSize + persistentIdentity;
		if(next > Integer.MAX_VALUE) { throw new ArithmeticException("connection (" + persistentIdentity + ", " + volatileIdentity + ") is past an int index under the size " + persistentSize); }
		moved.add((int) next);
	}
	this.persistentSize = persistentSize;
	this.connections = moved;
}

/** Where the connection sits, or -1 when it has no index: an identity out of range, or an index past an int. */
long indexOf(int persistentIdentity, int volatileIdentity) {
	if(persistentIdentity < 0 || persistentIdentity >= persistentSize || volatileIdentity < 0) { return -1; }
	long index = (long) volatileIdentity * persistentSize + persistentIdentity;
	return index > Integer.MAX_VALUE ? -1 : index;
}

/**
 * The index of a connection to be made, under the stride, or under a new one when it does not fit there:
 * {@code min(maxFunctioningValue, ceil(persistentSize * 1.3))}. The functioning maximum is the largest
 * stride that keeps every index in an int, the new connection's included, {@code 2^31 / (highest
 * volatile + 1)}; the persistent size is the persistent arity's, or one past its highest connected
 * identity when that is more. Refused when even that stride does not reach the persistent identity.
 * */
int fit(int persistentIdentity, int volatileIdentity) {
	long index = indexOf(persistentIdentity, volatileIdentity);
	if(index >= 0) { return (int) index; }
	if(persistentIdentity < 0 || volatileIdentity < 0) { throw new IllegalArgumentException("connection (" + persistentIdentity + ", " + volatileIdentity + ") has a negative identity"); }
	int highestPersistent = persistentIdentity, highestVolatile = volatileIdentity;
	if(!connections.isEmpty()) { highestVolatile = Math.max(highestVolatile, connections.last() / persistentSize); }
	IntIterator walk = connections.getIntIterator();
	while(walk.hasNext()) { highestPersistent = Math.max(highestPersistent, walk.next() % persistentSize); }
	long size = Math.max(core.getArity(persistentArity).occupied().size(), highestPersistent + 1L);
	long functioning = Math.min(Integer.MAX_VALUE, (1L << 31) / (highestVolatile + 1L));
	long stride = Math.min(functioning, (long) Math.ceil(size * 1.3));
	if(stride <= highestPersistent) {
		throw new ArithmeticException("connection (" + persistentIdentity + ", " + volatileIdentity + ") fits no int index: a stride past " + highestPersistent + " leaves volatile " + highestVolatile + " past 2^31");
	}
	setPersistentSize((int) stride);
	return (int) indexOf(persistentIdentity, volatileIdentity);
}

/** The index of a connection that exists, which always fits. */
int held(int persistentIdentity, int volatileIdentity) {
	long index = indexOf(persistentIdentity, volatileIdentity);
	if(index < 0) { throw new IllegalStateException("connection (" + persistentIdentity + ", " + volatileIdentity + ") has no index, so it cannot exist"); }
	return (int) index;
}


@ACS_Requestable(0)
@Override
public boolean isConnected(int persistentIdentity, int volatileIdentity) {
	long index = indexOf(persistentIdentity, volatileIdentity);
	return index >= 0 && connections.contains((int) index);
}

/** A probe per volatile identity up to the highest connected one. */
@ACS_Requestable(5)
@Override
public int complementCount(int persistentIdentity) {
	return volatilesOf(persistentIdentity).size();
}

/** One range count over the volatile identity's run. */
@ACS_Requestable(6)
@Override
public int primaryCount(int volatileIdentity) {
	long start = indexOf(0, volatileIdentity);
	return start < 0 ? 0 : (int) connections.rangeCardinality(start, Math.min(start + persistentSize, (long) Integer.MAX_VALUE + 1));
}

/** Ascending, a probe per volatile identity up to the highest connected one. */
@Override
public void copyComplementsInto(int persistentIdentity, int[] dest, int offset) {
	IntArrayList held = volatilesOf(persistentIdentity);
	held.getElements(0, dest, offset, held.size());
}

/** Ascending, one walk over the volatile identity's run. */
@Override
public void copyPrimariesInto(int volatileIdentity, int[] dest, int offset) {
	long start = indexOf(0, volatileIdentity);
	if(start < 0) { return; }
	long end = start + persistentSize;
	PeekableIntIterator walk = connections.getIntIterator();
	walk.advanceIfNeeded((int) start);
	while(walk.hasNext()) {
		int index = walk.next();
		if(index >= end) { break; }
		dest[offset++] = (int) (index - start);
	}
}


@ACS_Requestable(1)
@Override
public boolean ensureConnection(int persistentIdentity, int volatileIdentity, String comment) {
	requireLive(persistentArity, persistentIdentity);
	requireLive(volatileArity, volatileIdentity);
	// Fitting first, outside the request: a new stride is not journaled, and a refusal records nothing.
	if(connections.contains(fit(persistentIdentity, volatileIdentity))) { return false; }
	int request = journal.beginRecordingActivity(this, "ensureConnection", params, comment);
	connect(persistentIdentity, volatileIdentity);
	complete(request);
	return true;
}

@ACS_Requestable(2)
@Override
public boolean breakConnection(int persistentIdentity, int volatileIdentity, String comment) {
	if(!isConnected(persistentIdentity, volatileIdentity)) { return false; }
	int request = journal.beginRecordingActivity(this, "breakConnection", params, comment);
	disconnect(persistentIdentity, volatileIdentity);
	complete(request);
	return true;
}

/** Every other connection of the persistent identity broken, then the new one made unless it was there, as one request; fitting first, as {@link #ensureConnection} does. */
@ACS_Requestable(3)
@Override
public boolean setComplement(int persistentIdentity, int volatileIdentity, String comment) {
	requireLive(persistentArity, persistentIdentity);
	requireLive(volatileArity, volatileIdentity);
	IntArrayList others = volatilesOf(persistentIdentity);
	boolean held = others.rem(volatileIdentity);
	if(held && others.isEmpty()) { return false; }
	if(!held) { fit(persistentIdentity, volatileIdentity); }
	int request = journal.beginRecordingActivity(this, "setComplement", params, comment);
	for(int i = 0; i < others.size(); i++) { disconnect(persistentIdentity, others.getInt(i)); }
	if(!held) { connect(persistentIdentity, volatileIdentity); }
	complete(request);
	return true;
}

/** As {@link #setComplement}, from the volatile identity's run. */
@ACS_Requestable(4)
@Override
public boolean setPrimary(int volatileIdentity, int persistentIdentity, String comment) {
	requireLive(volatileArity, volatileIdentity);
	requireLive(persistentArity, persistentIdentity);
	IntArrayList others = persistentsOf(volatileIdentity);
	boolean held = others.rem(persistentIdentity);
	if(held && others.isEmpty()) { return false; }
	if(!held) { fit(persistentIdentity, volatileIdentity); }
	int request = journal.beginRecordingActivity(this, "setPrimary", params, comment);
	for(int i = 0; i < others.size(); i++) { disconnect(others.getInt(i), volatileIdentity); }
	if(!held) { connect(persistentIdentity, volatileIdentity); }
	complete(request);
	return true;
}

/**
 * Breaks every connection the identity has on the side or sides its arity is, each found before any is
 * broken, so nothing is broken while the bitmap is being walked.
 * */
@Override
public boolean forget(int arity, int identity) {
	IntArrayList volatiles = arity == persistentArity ? volatilesOf(identity) : new IntArrayList();
	IntArrayList persistents = arity == volatileArity ? persistentsOf(identity) : new IntArrayList();
	if(volatiles.isEmpty() && persistents.isEmpty()) { return false; }
	int request = journal.beginRecordingActivity(this, "forget", params, null);
	for(int i = 0; i < volatiles.size(); i++) { disconnect(identity, volatiles.getInt(i)); }
	// Over one arity, the identity's connection to itself is on both lists and already broken by now.
	for(int i = 0; i < persistents.size(); i++) {
		if(isConnected(persistents.getInt(i), identity)) { disconnect(persistents.getInt(i), identity); }
	}
	complete(request);
	return true;
}

/** The volatile identities the persistent one is connected to, ascending. */
IntArrayList volatilesOf(int persistentIdentity) {
	IntArrayList held = new IntArrayList();
	if(persistentIdentity < 0 || persistentIdentity >= persistentSize || connections.isEmpty()) { return held; }
	int last = connections.last() / persistentSize;
	for(int volatileIdentity = 0; volatileIdentity <= last; volatileIdentity++) {
		if(isConnected(persistentIdentity, volatileIdentity)) { held.add(volatileIdentity); }
	}
	return held;
}

/** The persistent identities the volatile one is connected to, ascending. */
IntArrayList persistentsOf(int volatileIdentity) {
	int count = primaryCount(volatileIdentity);
	IntArrayList held = new IntArrayList(count);
	held.size(count);
	copyPrimariesInto(volatileIdentity, held.elements(), 0);
	return held;
}


/** Connects and records it. */
void connect(int persistentIdentity, int volatileIdentity) {
	connections.add(fit(persistentIdentity, volatileIdentity));
	params[0] = persistentIdentity;
	params[1] = volatileIdentity;
	journal.recordActivityStep(CONNECT, 2);
}

void disconnect(int persistentIdentity, int volatileIdentity) {
	connections.remove(held(persistentIdentity, volatileIdentity));
	params[0] = persistentIdentity;
	params[1] = volatileIdentity;
	journal.recordActivityStep((byte) -CONNECT, 2);
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case CONNECT -> connections.add(fit(params[index], params[index + 1]));
		case -CONNECT -> connections.remove(held(params[index], params[index + 1]));
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}


/**
 * The two arity slots and the size, then the connections as (persistent, volatile) pairs in index order,
 * so the file carries the relation and not Roaring's encoding of it, which is the library's and may change
 * with it.
 * */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	dos.writeInt(persistentArity);
	dos.writeInt(volatileArity);
	dos.writeInt(persistentSize);
	dos.writeLong(connections.getLongCardinality());
	IntIterator walk = connections.getIntIterator();
	while(walk.hasNext()) {
		int index = walk.next();
		dos.writeInt(index % persistentSize);
		dos.writeInt(index / persistentSize);
	}
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, attached to the core, outside the journal; refuses a connection written twice or past the size. */
public static SingleRoaringConnectorComponent loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	int persistentArity = dis.readInt();
	int volatileArity = dis.readInt();
	SingleRoaringConnectorComponent indexer = new SingleRoaringConnectorComponent(core, journal, persistentArity, volatileArity, dis.readInt());
	for(long i = 0, count = dis.readLong(); i < count; i++) {
		int persistentIdentity = dis.readInt();
		int volatileIdentity = dis.readInt();
		long index = indexer.indexOf(persistentIdentity, volatileIdentity);
		if(index < 0) { throw new IOException("connection (" + persistentIdentity + ", " + volatileIdentity + ") has no index under the size " + indexer.persistentSize); }
		if(!indexer.connections.checkedAdd((int) index)) { throw new IOException("connection (" + persistentIdentity + ", " + volatileIdentity + ") is written twice"); }
	}
	return indexer;
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

/**
 * The element's clusters become {@code clusters}, a repeat counting once: every other one broken, then each
 * new one connected, as one request, so nothing is refused for what breaks only midway. False when
 * nothing changes.
 * */
@ACS_Requestable(14)
@Override
public boolean setClusters(int element, int[] clusters, String comment) {
	requireLive(persistentArity, element);
	for(int cluster : clusters) { requireLive(volatileArity, cluster); }
	IntOpenHashSet wanted = new IntOpenHashSet(clusters);
	int[] held = clustersOf(element);
	if(wanted.equals(new IntOpenHashSet(held))) { return false; }
	// Fitting first, outside the request, as ensureConnection does.
	for(int cluster : clusters) { if(!isConnected(element, cluster)) { fit(element, cluster); } }
	int request = journal.beginRecordingActivity(this, "setClusters", params, comment);
	try {
		for(int cluster : held) { if(!wanted.contains(cluster)) { disconnect(element, cluster); } }
		for(int cluster : clusters) { if(!isConnected(element, cluster)) { connect(element, cluster); } }
	} catch(RuntimeException | Error e) {
		journal.abortRecordingActivity();
		throw e;
	}
	complete(request);
	return true;
}

/** As {@link #setClusters}, from the cluster's side. */
@ACS_Requestable(15)
@Override
public boolean setElements(int cluster, int[] elements, String comment) {
	requireLive(volatileArity, cluster);
	for(int element : elements) { requireLive(persistentArity, element); }
	IntOpenHashSet wanted = new IntOpenHashSet(elements);
	int[] held = elementsOf(cluster);
	if(wanted.equals(new IntOpenHashSet(held))) { return false; }
	for(int element : elements) { if(!isConnected(element, cluster)) { fit(element, cluster); } }
	int request = journal.beginRecordingActivity(this, "setElements", params, comment);
	try {
		for(int element : held) { if(!wanted.contains(element)) { disconnect(element, cluster); } }
		for(int element : elements) { if(!isConnected(element, cluster)) { connect(element, cluster); } }
	} catch(RuntimeException | Error e) {
		journal.abortRecordingActivity();
		throw e;
	}
	complete(request);
	return true;
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

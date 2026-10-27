package org.halim.acse.database;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.database.ACS_Actor;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_Requestable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Objects;

/**
 * Requests and their steps in flat lists. A client request and every request nested in it form one
 * transaction, which undo and redo move as a whole; a new client request drops the transactions undone
 * before it; during an inspection a new client request is refused instead, so the way back is kept.
 * Nothing here is multi thread safe.
 * */
public class ACSE_JournalBasic implements ACS_Journal {

final ACS_Core core;

/** Steps as [pair, count, params...]. */
final IntArrayList data = new IntArrayList();
/** A bit per value rather than a byte. A bit set cannot tell a trailing false from no value, so the count is kept beside it. */
final BitSet booleans = new BitSet();
int booleanCount;
final ArrayList<Object> objects = new ArrayList<>();
/** [dataStart, request] per run of steps by one request; a new run opens whenever the recording request changes. */
final IntArrayList segments = new IntArrayList();
/** [transaction, firstSegment, actor, parent, booleansStart, objectsStart] per request, nested ones included; parent is -1 for a client request, actor as {@link #actorIdOf} packs it. */
final IntArrayList requests = new IntArrayList();
static final int FIELDS = 6, TRANSACTION = 0, SEGMENT = 1, ACTOR = 2, PARENT = 3, BOOLEANS = 4, OBJECTS = 5;
/** An actor's type, as a request's address names it; the journal's own, 2, never records. */
static final byte DESCRIPTION = 0, CORE = 1, JOURNAL = 2, ARITY = 3, ROW = 4, STRUCTURE = 5, PROCEDURE = 7;
/** [methodName, comment] per request. */
final ArrayList<String> texts = new ArrayList<>();
/** First request of each transaction. */
final IntArrayList transactions = new IntArrayList();
/** [request, response] per query. */
final ArrayList<String> queries = new ArrayList<>();

final IntArrayList openRequests = new IntArrayList();
final ArrayList<Object> openParams = new ArrayList<>();
final IntArrayList openQueries = new IntArrayList();

/** Transactions applied; the ones from here on are undone. */
int done;
/** Transactions below this can no longer be undone. */
int horizon;
/** The first inspectAt's {@code done}, -1 without an inspection; not stored, a loaded journal starts without one. */
int returnPoint = -1;
/** The request the last inspectAt named, for the refusal. */
int inspected;
boolean replaying;

public ACSE_JournalBasic(ACS_Core core) { this.core = core; }


@Override
public int beginRecordingActivity(ACS_Actor actor, String methodName, Object paramsMemory, String comment) {
	checkNotReplaying();
	if(returnPoint >= 0) { throw new IllegalStateException("an inspection at request " + inspected + " stands; call endInspection before recording"); }
	int actorId = actorIdOf(actor);
	int parent = openRequests.isEmpty() ? -1 : openRequests.topInt();
	if(parent < 0) {
		dropRedo();
		transactions.add(requestCount());
	}
	int request = requestCount();
	openSegment(request);
	requests.add(transactionCount() - 1);
	requests.add(segmentCount() - 1);
	requests.add(actorId);
	requests.add(parent);
	requests.add(booleanCount);
	requests.add(objects.size());
	texts.add(methodName);
	texts.add(comment);
	openRequests.push(request);
	openParams.add(paramsMemory);
	return request;
}

@Override
public int completeRecordingActivity() {
	checkOpen();
	int request = openRequests.popInt();
	openParams.remove(openParams.size() - 1);
	if(openRequests.isEmpty()) { done = transactionCount(); }
	else { openSegment(openRequests.topInt()); }
	return request;
}

@Override
public int abortRecordingActivity() {
	checkOpen();
	int request = openRequests.topInt();
	int firstSegment = field(request, SEGMENT);
	int dataStart = segments.getInt(2 * firstSegment);
	for(int at = dataStart; at < data.size(); at += 2 + data.getInt(at + 1)) {
		if(data.getInt(at) == 0) { throw new IllegalStateException("request " + request + " recorded a step that cannot be undone"); }
	}
	replaying = true;
	try { for(int segment = segmentCount() - 1; segment >= firstSegment; segment--) { replaySegment(segment, true); } }
	finally { replaying = false; }
	data.size(dataStart);
	truncateBooleans(field(request, BOOLEANS));
	objects.subList(field(request, OBJECTS), objects.size()).clear();
	segments.size(2 * firstSegment);
	requests.size(FIELDS * request);
	texts.subList(2 * request, texts.size()).clear();
	openRequests.popInt();
	openParams.remove(openParams.size() - 1);
	if(openRequests.isEmpty()) { transactions.size(transactions.size() - 1); }
	else { openSegment(openRequests.topInt()); }
	return request;
}

@Override
public boolean isRecordingActivity() { return !openRequests.isEmpty(); }

@Override
public void recordActivityStep(byte stepPair, int paramCount) {
	checkOpen();
	Object memory = openParams.get(openParams.size() - 1);
	int[] params;
	int available;
	if(memory instanceof int[] array) { params = array; available = array.length; }
	else if(memory instanceof IntArrayList list) { params = list.elements(); available = list.size(); }
	else { throw new IllegalArgumentException("params memory is " + className(memory) + ", not an int[] or IntArrayList"); }
	if(paramCount < 0 || paramCount > available) { throw new IndexOutOfBoundsException("paramCount " + paramCount + ", params memory holds " + available); }
	data.add(stepPair);
	data.add(paramCount);
	data.addElements(data.size(), params, 0, paramCount);
	if(stepPair == 0) { horizon = transactionCount(); }
}

@Override
public int recordBoolean(boolean value) {
	checkOpen();
	booleans.set(booleanCount, value);
	return booleanCount++;
}

@Override
public int recordObject(Object value) {
	checkOpen();
	objects.add(value);
	return objects.size() - 1;
}

@Override
public boolean getBoolean(int index) { return booleans.get(Objects.checkIndex(index, booleanCount)); }

@Override
public Object getObject(int index) { return objects.get(index); }


@Override
public int recordQueryRequest(String description) {
	int query = queries.size() / 2;
	queries.add(description);
	queries.add(null);
	openQueries.push(query);
	return query;
}

@Override
public int recordQueryResponse(String description) {
	if(openQueries.isEmpty()) { throw new IllegalStateException("no query is open"); }
	int query = openQueries.popInt();
	queries.set(2 * query + 1, description);
	return query;
}


@ACS_Requestable(0)
@Override
public void undoLastActivity() {
	checkIdle();
	if(done == 0) { throw new IllegalStateException("nothing to undo"); }
	moveTo(done - 1);
}

@ACS_Requestable(1)
@Override
public void redoLastActivity() {
	checkIdle();
	if(done == transactionCount()) { throw new IllegalStateException("nothing to redo"); }
	moveTo(done + 1);
}

/** Leaves the state right after the transaction holding {@code activityIndex}. */
@ACS_Requestable(2)
@Override
public void undoUntilActivityIndex(int activityIndex) {
	checkIdle();
	int target = transactionOf(activityIndex) + 1;
	if(target > done) { throw new IllegalStateException("request " + activityIndex + " is undone; redo to reach it"); }
	moveTo(target);
}

/** Leaves the state right after the transaction holding {@code activityIndex}. */
@ACS_Requestable(3)
@Override
public void redoUntilActivityIndex(int activityIndex) {
	checkIdle();
	int target = transactionOf(activityIndex) + 1;
	if(target < done) { throw new IllegalStateException("request " + activityIndex + " is applied; undo to reach it"); }
	moveTo(target);
}

/**
 * Leaves the state right after the transaction holding {@code activityIndex}, undoing or redoing as
 * needed. The first inspectAt keeps the point it left; a later one, and undo or redo meanwhile, move
 * without changing it, so the way back stays one call.
 * */
@ACS_Requestable(4)
@Override
public void inspectAt(int activityIndex) {
	checkIdle();
	int before = done;
	moveTo(transactionOf(activityIndex) + 1);
	if(returnPoint < 0) { returnPoint = before; }
	inspected = activityIndex;
}

@ACS_Requestable(5)
@Override
public void endInspection() {
	checkIdle();
	if(returnPoint < 0) { throw new IllegalStateException("no inspection stands"); }
	moveTo(returnPoint);
	returnPoint = -1;
}

@ACS_Requestable(6)
@Override
public boolean isInspecting() { return returnPoint >= 0; }

@ACS_Requestable(7)
@Override
public int getCurrentActivityIndex() { return done < transactionCount() ? transactions.getInt(done) - 1 : requestCount() - 1; }

@ACS_Requestable(8)
@Override
public int getPendingActivityCount() { return requestCount() - getCurrentActivityIndex() - 1; }

void moveTo(int target) {
	if(target < horizon) { throw new IllegalStateException("undo history is broken before transaction " + horizon); }
	replaying = true;
	try {
		while(done > target) { replayTransaction(done - 1, true); done--; }
		while(done < target) { replayTransaction(done, false); done++; }
	} finally { replaying = false; }
}

/** Segments last-first on undo; the steps inside a segment run forward, since their order does not matter. */
void replayTransaction(int transaction, boolean undo) {
	int first = firstSegmentOf(transaction);
	int end = endSegmentOf(transaction);
	if(undo) { for(int segment = end - 1; segment >= first; segment--) { replaySegment(segment, true); } }
	else { for(int segment = first; segment < end; segment++) { replaySegment(segment, false); } }
}

void replaySegment(int segment, boolean undo) {
	ACS_Actor actor = actorOf(field(segments.getInt(2 * segment + 1), ACTOR));
	int at = segments.getInt(2 * segment);
	int end = segmentEnd(segment);
	int[] elements = data.elements();
	while(at < end) {
		byte pair = (byte) elements[at];
		actor.doTraceableStep(undo ? (byte) -pair : pair, elements, at + 2);
		at += 2 + elements[at + 1];
	}
}


/**
 * JSON Lines: a line naming the components by slot, then one line per transaction holding its
 * requests, their steps and its recorded values, then one line per query.
 * */
@Override
public void storeAll(DataOutputStream dos) throws IOException {
	StringBuilder line = new StringBuilder("{\"arities\":[");
	for(int i = 0; i < core.getArityCount(); i++) { comma(line, i); string(line, className(core.getArity(i))); }
	line.append("],\"structures\":[");
	for(int i = 0; i < core.getStructureCount(); i++) { comma(line, i); string(line, className(core.getStructure(i))); }
	line.append("]}");
	writeLine(dos, line);
	for(int transaction = 0; transaction < transactionCount(); transaction++) {
		appendTransaction(line, transaction);
		writeLine(dos, line);
	}
	for(int query = 0; query < queries.size() / 2; query++) {
		line.append("{\"query\":").append(query).append(",\"request\":");
		string(line, queries.get(2 * query));
		line.append(",\"response\":");
		string(line, queries.get(2 * query + 1));
		line.append('}');
		writeLine(dos, line);
	}
}

void appendTransaction(StringBuilder line, int transaction) {
	String state = transaction < done ? "applied" : !openRequests.isEmpty() && transaction == transactionCount() - 1 ? "open" : "undone";
	line.append("{\"transaction\":").append(transaction).append(",\"state\":\"").append(state)
		.append("\",\"undoable\":").append(transaction >= horizon).append(",\"requests\":[");
	int firstRequest = transactions.getInt(transaction);
	int endRequest = transaction + 1 < transactionCount() ? transactions.getInt(transaction + 1) : requestCount();
	for(int request = firstRequest; request < endRequest; request++) {
		comma(line, request - firstRequest);
		line.append("{\"request\":").append(request).append(",\"parent\":").append(field(request, PARENT))
			.append(",\"type\":").append(field(request, ACTOR) >> 24).append(",\"index\":").append(field(request, ACTOR) & 0xFFFFFF).append(",\"method\":");
		string(line, texts.get(2 * request));
		line.append(",\"comment\":");
		string(line, texts.get(2 * request + 1));
		line.append(",\"steps\":[");
		boolean first = true;
		for(int segment = firstSegmentOf(transaction); segment < endSegmentOf(transaction); segment++) {
			if(segments.getInt(2 * segment + 1) != request) { continue; }
			for(int at = segments.getInt(2 * segment), end = segmentEnd(segment); at < end; at += 2 + data.getInt(at + 1)) {
				if(!first) { line.append(','); }
				first = false;
				line.append("{\"pair\":").append(data.getInt(at)).append(",\"params\":[");
				for(int i = 0; i < data.getInt(at + 1); i++) { comma(line, i); line.append(data.getInt(at + 2 + i)); }
				line.append("]}");
			}
		}
		line.append("]}");
	}
	int booleansStart = field(firstRequest, BOOLEANS);
	int booleansEnd = endRequest < requestCount() ? field(endRequest, BOOLEANS) : booleanCount;
	line.append("],\"booleans\":[");
	for(int i = booleansStart; i < booleansEnd; i++) { comma(line, i - booleansStart); line.append(booleans.get(i)); }
	int objectsStart = field(firstRequest, OBJECTS);
	int objectsEnd = endRequest < requestCount() ? field(endRequest, OBJECTS) : objects.size();
	line.append("],\"objects\":[");
	for(int i = objectsStart; i < objectsEnd; i++) { comma(line, i - objectsStart); string(line, className(objects.get(i))); }
	line.append("]}");
}

static void writeLine(DataOutputStream dos, StringBuilder line) throws IOException {
	line.append('\n');
	dos.write(line.toString().getBytes(StandardCharsets.UTF_8));
	line.setLength(0);
}

static void comma(StringBuilder line, int index) { if(index > 0) { line.append(','); } }

static void string(StringBuilder line, String string) {
	if(string == null) { line.append("null"); return; }
	line.append('"');
	for(int i = 0; i < string.length(); i++) {
		char c = string.charAt(i);
		switch(c) {
			case '"' -> line.append("\\\"");
			case '\\' -> line.append("\\\\");
			case '\n' -> line.append("\\n");
			case '\r' -> line.append("\\r");
			case '\t' -> line.append("\\t");
			default -> {
				if(c < 0x20) { line.append(String.format("\\u%04x", (int) c)); }
				else { line.append(c); }
			}
		}
	}
	line.append('"');
}

static String className(Object object) { return object == null ? null : object.getClass().getName(); }


/** The actor's type in the high byte and its slot in the low three, so one int per request holds both: description is 0, the core is 1, arity k is 3 << 24 | k, structure k is 5 << 24 | k. */
int actorIdOf(ACS_Actor actor) {
	if(actor == core.getDescriptionManager()) { return actorId(DESCRIPTION, 0); }
	if(actor == core) { return actorId(CORE, 0); }
	if(actor == this) { return actorId(JOURNAL, 0); }
	for(int i = 0; i < core.getArityCount(); i++) { if(core.getArity(i) == actor) { return actorId(ARITY, i); } }
	for(int i = 0; i < core.getStructureCount(); i++) { if(core.getStructure(i) == actor) { return actorId(STRUCTURE, i); } }
	throw new IllegalArgumentException(className(actor) + " is not added to the core");
}

static int actorId(byte type, int slot) {
	if(slot >= 1 << 24) { throw new IllegalArgumentException("slot " + slot + " does not fit the three bytes an actor id gives it"); }
	return type << 24 | slot;
}

ACS_Actor actorOf(int actor) {
	int slot = actor & 0xFFFFFF;
	return switch((byte) (actor >> 24)) {
		case DESCRIPTION -> core.getDescriptionManager();
		case CORE -> core;
		case JOURNAL -> this;
		case ARITY -> core.getArity(slot);
		case STRUCTURE -> core.getStructure(slot);
		default -> throw new IllegalStateException("actor id " + actor + " names no type");
	};
}

/** The journal records no steps of its own: a journal traced as an actor is a timeline of several branches, which is out of scope. */
@Override
public void doTraceableStep(byte stepPair, int[] params, int index) { throw new UnsupportedOperationException("This feature is out of MVP"); }

/** What a refused request writes back: the error's message, or its class when it has none. */
static String reason(RuntimeException e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getName(); }

/** Reuses the last segment when nothing was recorded in it. */
void openSegment(int request) {
	int count = segmentCount();
	if(count > 0 && segments.getInt(2 * count - 2) == data.size()) { segments.set(2 * count - 1, request); }
	else {
		segments.add(data.size());
		segments.add(request);
	}
}

void dropRedo() {
	if(done == transactionCount()) { return; }
	int firstRequest = transactions.getInt(done);
	int firstSegment = field(firstRequest, SEGMENT);
	data.size(segments.getInt(2 * firstSegment));
	truncateBooleans(field(firstRequest, BOOLEANS));
	objects.subList(field(firstRequest, OBJECTS), objects.size()).clear();
	segments.size(2 * firstSegment);
	requests.size(FIELDS * firstRequest);
	texts.subList(2 * firstRequest, texts.size()).clear();
	transactions.size(done);
}

int transactionOf(int request) {
	if(request < 0 || request >= requestCount()) { throw new IndexOutOfBoundsException("request " + request + ", count " + requestCount()); }
	return field(request, TRANSACTION);
}

void truncateBooleans(int count) {
	booleans.clear(count, booleanCount);
	booleanCount = count;
}

int field(int request, int field) { return requests.getInt(FIELDS * request + field); }
int firstSegmentOf(int transaction) { return field(transactions.getInt(transaction), SEGMENT); }
int endSegmentOf(int transaction) { return transaction + 1 < transactionCount() ? firstSegmentOf(transaction + 1) : segmentCount(); }
int segmentEnd(int segment) { return segment + 1 < segmentCount() ? segments.getInt(2 * segment + 2) : data.size(); }

int requestCount() { return requests.size() / FIELDS; }
int segmentCount() { return segments.size() / 2; }
int transactionCount() { return transactions.size(); }

void checkNotReplaying() {
	if(replaying) { throw new IllegalStateException("a step function recorded during undo or redo"); }
}

void checkOpen() {
	checkNotReplaying();
	if(openRequests.isEmpty()) { throw new IllegalStateException("no request is open"); }
}

void checkIdle() {
	checkNotReplaying();
	if(!openRequests.isEmpty()) { throw new IllegalStateException("request " + openRequests.topInt() + " is still open"); }
}

@Override
public ACS_Journal getJournal() { return this; }

}

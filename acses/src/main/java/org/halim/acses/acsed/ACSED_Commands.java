package org.halim.acses.acsed;

import org.halim.acs.database.ACS_Actor;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.database.ACS_Requestable;
import org.halim.acse.database.ACSE_CoreBasic;
import org.halim.acse.database.ACSE_PersistenceManagerBasic;
import org.halim.acses.acsess.ACSED_Request;
import org.halim.acses.acsess.ACSED_Request.Answer;
import org.halim.acses.acsess.ACSED_Request.Frame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;


/**
 * What acsed does with a connection's frames, over the databases it holds by index and by name. A loaded
 * database's classes are its own, built from its folder's jars, so every command reaches a core through the
 * contracts only: an {@code instanceof} against one of ACSE's classes would be false for every loaded
 * database. ACSE's classes are named only where a database or a new folder gets them, in {@link #createDB}
 * and {@link #newManager}.
 * <p>
 * acsed's own commands, under subject -1, are the methods marked here, and a database's, under the
 * database's acsed type, are {@link ACSED_Database}'s: each a switch on the code, outside ACS_Actor, since
 * acsed is not journaled, reading its inputs and refusing leftover bytes before anything runs. Every other
 * frame is an actor's request, run once its class is found at the place the frame gives in the database's
 * acse-language.txt; bytes it leaves unread refuse it after it ran. A refused command leaves the databases
 * held as they were. Paths must be absolute, since the daemon's working folder is not the caller's and a
 * relative path would name another place.
 * <p>
 * The sensitive commands - acquire, the daemon's and a database's, waitInspectionReturn, and a request
 * that starts an inspection - take their frame's timeout, else the boot's default; once it passes,
 * {@link #expire} ends what they started: the acquisition is let go, the wait refused, the inspection
 * ended as endInspection ends it. Booted with +forbidDefaultTimeout, a sensitive command without a timeout
 * is refused; an inspection shows only once its request ran, so that one is ended again and refused. acsed's
 * other commands refuse a timeout; an actor's request that starts no inspection leaves it unused.
 * */
class ACSED_Commands {

/** Where this daemon listens, for boot's first answer; the log, the lock and the two files acsess reads sit beside it. */
final Path socket;
/** The boot's timeout for a sensitive command whose frame gives none, in milliseconds, 0 for none; and whether the boot forbade one. */
final long defaultTimeout;
final boolean forbidDefault;
/** By name, in the order they came. */
final LinkedHashMap<String, ACSED_Database> databases = new LinkedHashMap<>();
/** By index, a slot per database ever held: an unloaded one leaves null, so no index is given twice while acsed runs and a stale one reaches no other database. */
final ArrayList<ACSED_Database> indexed = new ArrayList<>();
/** Each held folder's acse-language.txt, which the databases loaded from one folder share. */
final HashMap<Path, Language> languages = new HashMap<>();
/** Whether a boot was answered yet: the first is told the daemon started, every later one that it is running. */
boolean booted;
/** Set by shutdown; the daemon stops once its answers are sent. */
boolean stopping;
/** The clients a wait holds open, by database; the daemon owns the connections. */
final LinkedHashMap<ACSED_Database, ArrayList<Held>> waiting = new LinkedHashMap<>();
/** Held clients whose answer is ready, for the daemon to send once the current connection is answered. */
final ArrayList<Held> ready = new ArrayList<>();
/** The database the last wait holds for, and the wait's timeout, until the daemon hands its client to {@link #hold}. */
ACSED_Database holding;
long holdingTimeout;
/** The acquisition on the whole daemon, null when there is none; it and a database's acquisition never stand together, since a frame carries one key. */
Acquisition daemonAcquisition;
/** The acquisitions on databases, by index. */
final HashMap<Integer, Acquisition> acquisitions = new HashMap<>();
final Random random = new Random();
/** Set while a connection of several frames runs, so a wait among them cannot hold. */
boolean several;
/** The frame running, for its key. */
Frame frame;
/** The comment of the frame running, for its log line, when its command takes one. */
String comment;
/** The outcome of the frame just run, for its log line. */
String outcome;

ACSED_Commands(Path socket, long defaultTimeout, boolean forbidDefault) {
	this.socket = socket;
	this.defaultTimeout = defaultTimeout;
	this.forbidDefault = forbidDefault;
}


/** acsed's own language, beside the lock: its commands under subject -1, then a database's under the database's acsed type. */
void writeLanguage() throws IOException {
	StringBuilder text = new StringBuilder();
	Language.section(text, ACSED_Commands.class.getName(), lines(ACSED_Commands.class));
	Language.section(text, ACSED_Database.class.getName(), lines(ACSED_Database.class));
	ACSED_Request.replace(ACSED_Request.language(socket), text.toString());
}

/** A line per database held, its index, name and folder, tab-separated; rewritten whenever the databases change. */
void writeDatabases() throws IOException {
	StringBuilder text = new StringBuilder();
	for(ACSED_Database database : indexed) {
		if(database != null) { text.append(database.index).append('\t').append(database.name).append('\t').append(database.folder).append('\n'); }
	}
	ACSED_Request.replace(ACSED_Request.databases(socket), text.toString());
}

static String lines(Class<?> type) throws IOException {
	ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	ACS_Actor.writeRequestables(type, new DataOutputStream(bytes));
	return bytes.toString(StandardCharsets.UTF_8);
}


/**
 * The answers to a connection's frames, in order, or null when its one frame is a wait the daemon now holds,
 * its note then the only one; each frame's log line goes to {@code notes}. After a refusal the frames left
 * are not run, unless the connection says to run past it; after a shutdown none is.
 * */
ArrayList<Answer> run(ACSED_Request.Connection connection, ArrayList<String> notes) {
	ArrayList<Answer> answers = new ArrayList<>();
	boolean stopped = false;
	several = connection.frames().size() > 1;
	try {
		for(Frame one : connection.frames()) {
			if(stopped || stopping) {
				answers.add(new Answer(ACSED_Request.NOT_RUN, new byte[0]));
				notes.add(describe(one) + " -> not run");
				continue;
			}
			comment = null;
			Answer answer = runFrame(one);
			String note = describe(one) + (comment == null || comment.isEmpty() ? "" : " comment=" + ACSED_Daemon.quoted(comment));
			if(answer == null) {
				notes.add(note);
				return null;
			}
			answers.add(answer);
			notes.add(note + " -> " + outcome);
			if(answer.status() != ACSED_Request.DONE && !connection.continueOnRefusal()) { stopped = true; }
		}
	} finally { several = false; }
	return answers;
}

/** One frame's answer; whatever a command or a request throws becomes a refusal giving its reason. */
Answer runFrame(Frame one) {
	frame = one;
	try {
		if(one.timeout() < 0) { throw new IllegalArgumentException("timeout " + one.timeout() + ": a timeout is a positive number of milliseconds"); }
		if(one.subject() == ACSED_Request.ACSED) { return acsed(parameters(one)); }
		ACSED_Database database = database(one.subject());
		if(one.type() == ACSED_Request.ACSED) {
			if(one.index() != 0) { throw new IllegalArgumentException("a database's acsed type has index 0, not " + one.index()); }
			return database.run(parameters(one));
		}
		return actor(database, one);
	} catch(IOException | RuntimeException | LinkageError e) { return refused(reason(e)); }
}

/**
 * An actor's request: refused when the actor's class is not at the frame's place in the database's
 * acse-language.txt, since the frame was then translated by another class's lines; refused after it ran
 * when it left bytes of its parameters unread, the change it made staying. A journal request that ends an
 * inspection answers the waits held for the database; one that starts an inspection gives it its timeout,
 * or, refused one, ends it again.
 * */
Answer actor(ACSED_Database database, Frame one) throws IOException {
	ACS_Actor actor = actorOf(database.core, one.type(), one.index());
	Language language = languages.get(database.folder);
	String name = actor.getClass().getName();
	if(language.placeOf(name) != one.place()) { throw new IllegalStateException("place " + one.place() + " of " + language.file + " does not hold " + name + "; probe again"); }
	guard(database, false, false);
	ByteArrayInputStream bytes = new ByteArrayInputStream(one.parameters());
	ByteArrayOutputStream returns = new ByteArrayOutputStream();
	boolean inspecting = database.core.getJournal().isInspecting();
	boolean done = actor.request(new DataInputStream(bytes), new DataOutputStream(returns));
	boolean inspectingNow = database.core.getJournal().isInspecting();
	if(inspecting && !inspectingNow) {
		database.inspectionTimeout = 0;
		answerWaits(database, done(database.name + " returned: " + position(database.core)));
	}
	if(!inspecting && inspectingNow) {
		long timeout;
		try { timeout = timeoutFor("a request starting an inspection"); }
		catch(IllegalArgumentException e) {
			database.core.getJournal().endInspection();
			throw new IllegalStateException(e.getMessage() + "; the inspection it started is ended");
		}
		database.inspectionTimeout = timeout;
		database.inspectionUntil = until(timeout);
	}
	String unused = one.timeout() != 0 && (inspecting || !inspectingNow) ? " (timeout unused)" : "";
	if(bytes.available() > 0) { throw new IllegalStateException("the request ran, but left " + bytes.available() + " bytes of its parameters unread; its change stays"); }
	if(done) {
		outcome = "done" + unused;
		return new Answer(ACSED_Request.DONE, returns.toByteArray());
	}
	Answer answer = new Answer(ACSED_Request.REFUSED, returns.toByteArray());
	outcome = "refused: " + firstLine(ACSED_Request.readString(new DataInputStream(new ByteArrayInputStream(answer.returns())))) + unused;
	return answer;
}

/** The actor a frame's type and index name; the core and the journal are index 0. */
static ACS_Actor actorOf(ACS_Core core, int type, int index) {
	ACS_Actor actor = switch(type) {
		case ACSED_Request.DESCRIPTION -> index == 0 ? core.getDescriptionManager() : null;
		case ACSED_Request.CORE -> index == 0 ? core : null;
		case ACSED_Request.JOURNAL -> index == 0 ? core.getJournal() : null;
		case ACSED_Request.ARITY -> index >= 0 && index < core.getArityCount() ? core.getArity(index) : null;
		case ACSED_Request.STRUCTURE -> index >= 0 && index < core.getStructureCount() ? core.getStructure(index) : null;
		default -> throw new IllegalArgumentException("type " + type + " has no actors yet");
	};
	if(actor == null) { throw new IllegalArgumentException("type " + type + " has nothing at index " + index); }
	return actor;
}

/** acsed's commands under subject -1, by code in the order of the marks; acquire, code 4, is the one taking a timeout. */
Answer acsed(DataInputStream parameters) throws IOException {
	int code = parameters.readInt();
	if(code != 4) { untimed(); }
	return switch(code) {
		case 0 -> {
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, true, false);
			yield done(boot(comment));
		}
		case 1 -> {
			String path = ACSED_Request.readString(parameters), calledBy = ACSED_Request.readString(parameters), comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, false, false);
			yield done(createDB(path, calledBy, comment));
		}
		case 2 -> {
			String path = ACSED_Request.readString(parameters), calledBy = ACSED_Request.readString(parameters);
			boolean duplicate = parameters.readBoolean();
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, false, false);
			yield done(loadDB(path, calledBy, duplicate, comment));
		}
		case 3 -> {
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, true, false);
			yield done(databases(comment));
		}
		case 4 -> {
			boolean changesOnly = parameters.readBoolean();
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, false, false);
			yield done(acquire(changesOnly, comment));
		}
		case 5 -> {
			boolean force = parameters.readBoolean();
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, false, force);
			yield done(release(force, comment));
		}
		case 6 -> {
			String comment = noted(ACSED_Request.readString(parameters));
			ready(parameters, null, false, false);
			yield done(shutdown(comment));
		}
		default -> throw new IllegalArgumentException("acsed has no command " + code);
	};
}


@ACS_Requestable(0)
public String boot(String comment) {
	long pid = ProcessHandle.current().pid();
	String answer = (booted ? "running, pid " + pid : "started, pid " + pid + ", socket " + socket) + ", " + timeouts();
	booted = true;
	return answer;
}

/**
 * Makes a database in a new or empty folder, as citable.md's Persistence has it in its first case: acsed
 * writes the manager's jar into the folder, the manager is built from that jar, and a new core - ACSE's
 * basic one with its basic journal, from acsed's own classes - is stored through it. Every store then puts its own
 * jar of classes into its numbered folder, through the manager. A failure after the folder is made
 * leaves what was written there, and the folder, holding files now, is refused until it is emptied.
 * */
@ACS_Requestable(1)
public String createDB(String path, String calledBy, String comment) throws IOException {
	Path folder = absolute(path);
	String name = nameFor(calledBy, folder);
	if(Files.exists(folder) && !Files.isDirectory(folder)) { throw new IllegalArgumentException(folder + " is not a folder"); }
	if(Files.isDirectory(folder) && !isEmpty(folder)) { throw new IllegalArgumentException(folder + " holds files"); }
	Files.createDirectories(folder);
	Path jar = folder.resolve(ACS_PersistenceManager.MANAGER);
	ACSED_PersistenceJar.write(jar, ACSE_PersistenceManagerBasic.class);
	ACS_PersistenceManager manager = managerFrom(jar, folder);
	ACSE_CoreBasic core = new ACSE_CoreBasic();
	List<String> warnings;
	try {
		warnings = manager.store(core);
		add(new ACSED_Database(this, indexed.size(), name, folder, manager, core));
	} catch(IOException | RuntimeException | LinkageError e) {
		ACSED_Database.close(manager);
		throw e;
	}
	return withLines("created " + name + " at " + folder, warnings);
}

/**
 * Loads a stored folder through the manager its own jar holds, under the name the request gives, else
 * the folder's own. A folder another name holds is refused without +Duplicate, since two cores storing
 * into one folder is likely a mistake; +Duplicate where nothing else holds the folder only warns.
 * */
@ACS_Requestable(2)
public String loadDB(String path, String calledBy, boolean duplicate, String comment) throws IOException {
	Path folder = absolute(path);
	String name = nameFor(calledBy, folder);
	String holder = holderOf(folder, null);
	if(holder != null && !duplicate) { throw new IllegalStateException(folder + " is held already, by " + holder + "; loadDB +Duplicate loads it again"); }
	ACS_PersistenceManager manager = ACS_PersistenceManager.open(folder);
	try { add(new ACSED_Database(this, indexed.size(), name, folder, manager, manager.load())); }
	catch(IOException | RuntimeException | LinkageError e) {
		ACSED_Database.close(manager);
		throw e;
	}
	String warning = holder == null && duplicate ? "\nwarning: +Duplicate given, but nothing else holds " + folder : "";
	return "loaded " + name + " from " + manager.current() + warning;
}

@ACS_Requestable(3)
public String databases(String comment) {
	StringBuilder lines = new StringBuilder();
	databases.forEach((name, database) -> line(lines, name, database.folder.toString()));
	return lines.isEmpty() ? "no databases" : lines.toString();
}

/**
 * Acquires the whole daemon and answers the key alone: from here on every frame must carry the key, so a
 * request meant for another state of things is refused rather than run. It is for operational safety, not
 * security: the log shows the key. With +changesOnly the commands that only read go without it. An
 * acquisition where one stands already is refused, a database's included, since a frame carries one key.
 * Its timeout lets it go, as if released, once it passes.
 * */
@ACS_Requestable(4)
public String acquire(boolean changesOnly, String comment) {
	if(daemonAcquisition != null) { throw new IllegalStateException("the daemon is acquired already"); }
	if(!acquisitions.isEmpty()) { throw new IllegalStateException("databases are acquired: " + String.join(", ", acquired())); }
	daemonAcquisition = Acquisition.of(newKey(), changesOnly, timeoutFor("acquire"));
	return daemonAcquisition.key();
}

/** Ends the daemon's acquisition. The frame carries the key, which {@link #guard} saw to, or +force, for a key lost: the requester is taken to mean well, and an acquisition guards against mistakes only. */
@ACS_Requestable(5)
public String release(boolean force, String comment) {
	if(daemonAcquisition == null) { throw new IllegalStateException("the daemon is not acquired"); }
	daemonAcquisition = null;
	return "released the daemon" + (force ? ", without its key" : "");
}

/** Stops the daemon once this connection is answered, storing nothing, and refuses every wait held; the answer names what was not stored. */
@ACS_Requestable(6)
public String shutdown(String comment) {
	stopping = true;
	for(ACSED_Database database : List.copyOf(waiting.keySet())) { answerWaits(database, refused("the daemon stopped before an endInspection of " + database.name)); }
	return databases.isEmpty() ? "stopping" : "stopping; not stored: " + String.join(", ", databases.keySet());
}


/** Holds the database from here on: its index, its name, its folder's language - shared when another database holds the folder - and the list acsess reads. */
void add(ACSED_Database database) throws IOException {
	Language language = languages.get(database.folder);
	if(language == null) { language = Language.of(database.folder, database.core); }
	else { language.addActors(database.core); }
	languages.put(database.folder, language);
	databases.put(database.name, database);
	indexed.add(database);
	writeDatabases();
}

/** Lets the database go: its index stays used, its folder's language goes when no other database holds the folder. */
void remove(ACSED_Database database) throws IOException {
	databases.remove(database.name);
	indexed.set(database.index, null);
	if(holderOf(database.folder, database) == null) { languages.remove(database.folder); }
	writeDatabases();
}

/** Takes the client of the wait just answered with null, under the wait's timeout. */
void hold(SocketChannel client, OffsetDateTime time, String note) {
	waiting.computeIfAbsent(holding, database -> new ArrayList<>()).add(new Held(client, time, note, holdingTimeout));
	holding = null;
}

/** Makes the waits held for the database ready, each with the answer. */
void answerWaits(ACSED_Database database, Answer answer) {
	ArrayList<Held> held = waiting.remove(database);
	if(held == null) { return; }
	for(Held one : held) {
		one.answer = answer;
		one.outcome = answer.status() == ACSED_Request.DONE ? "done: " + firstLine(text(answer)) : "refused: " + firstLine(text(answer));
	}
	ready.addAll(held);
}

/** The timeout of the frame running, in milliseconds: its own, else the boot's default, 0 for none; refused when the boot forbade the default and the frame gives none. */
long timeoutFor(String command) {
	if(frame.timeout() > 0) { return frame.timeout(); }
	if(forbidDefault) { throw new IllegalArgumentException(command + " needs timeout=<ms>, since acsed was booted with +forbidDefaultTimeout"); }
	return defaultTimeout;
}

/** Refuses a timeout on one of acsed's commands that starts nothing a timeout could end. */
void untimed() {
	if(frame.timeout() != 0) { throw new IllegalArgumentException("this command starts nothing a timeout could end, so it takes no timeout="); }
}

String timeouts() { return forbidDefault ? "every sensitive command must give timeout=" : defaultTimeout > 0 ? "default timeout " + defaultTimeout + " ms" : "no default timeout"; }

/** The System.nanoTime a timeout of that many milliseconds ends at, from now; unused for 0. */
static long until(long timeout) { return System.nanoTime() + Math.multiplyExact(timeout, 1_000_000L); }

/**
 * Ends what has outlived its timeout and gives a log line for each: an acquisition is let go, a held wait
 * refused, an inspection ended through the journal, which answers the waits held for it. The answers go to
 * {@link #ready}, for the daemon to send.
 * */
List<String> expire() {
	long now = System.nanoTime();
	ArrayList<String> lines = new ArrayList<>();
	if(daemonAcquisition != null && daemonAcquisition.expired(now)) {
		lines.add("the daemon's acquisition ended by its timeout of " + daemonAcquisition.timeout() + " ms");
		daemonAcquisition = null;
	}
	acquisitions.entrySet().removeIf(entry -> {
		if(!entry.getValue().expired(now)) { return false; }
		lines.add(indexed.get(entry.getKey()).name + "'s acquisition ended by its timeout of " + entry.getValue().timeout() + " ms");
		return true;
	});
	for(ACSED_Database database : databases.values()) {
		if(database.inspectionTimeout == 0 || now - database.inspectionUntil < 0) { continue; }
		long timeout = database.inspectionTimeout;
		database.inspectionTimeout = 0;
		try {
			database.core.getJournal().endInspection();
			answerWaits(database, done(database.name + " returned: " + position(database.core) + ", its inspection ended by its timeout of " + timeout + " ms"));
			lines.add(database.name + "'s inspection ended by its timeout of " + timeout + " ms");
		} catch(RuntimeException e) { lines.add(database.name + "'s inspection outlived its timeout of " + timeout + " ms, but cannot end: " + reason(e)); }
	}
	for(var entry : waiting.entrySet()) {
		entry.getValue().removeIf(held -> {
			if(held.timeout == 0 || now - held.until < 0) { return false; }
			held.answer = refused("no return of " + entry.getKey().name + " within " + held.timeout + " ms");
			held.outcome = "refused: no return within " + held.timeout + " ms";
			ready.add(held);
			return true;
		});
	}
	waiting.values().removeIf(List::isEmpty);
	return lines;
}

/** How many milliseconds the daemon may sleep before the next timeout passes, at least 1; 0 when nothing is timed, for a sleep without end. */
long untilNext() {
	long now = System.nanoTime(), next = Long.MAX_VALUE;
	if(daemonAcquisition != null && daemonAcquisition.timeout() > 0) { next = Math.min(next, daemonAcquisition.until() - now); }
	for(Acquisition acquisition : acquisitions.values()) { if(acquisition.timeout() > 0) { next = Math.min(next, acquisition.until() - now); } }
	for(ACSED_Database database : databases.values()) { if(database.inspectionTimeout > 0) { next = Math.min(next, database.inspectionUntil - now); } }
	for(List<Held> held : waiting.values()) { for(Held one : held) { if(one.timeout > 0) { next = Math.min(next, one.until - now); } } }
	return next == Long.MAX_VALUE ? 0 : Math.max(1, next / 1_000_000 + 1);
}

/** Refuses leftover bytes before the command runs, then a frame its acquisition does not let through. */
void ready(DataInputStream parameters, ACSED_Database database, boolean reading, boolean force) throws IOException {
	if(parameters.available() > 0) { throw new IllegalArgumentException(parameters.available() + " bytes are left after the command's inputs; nothing ran"); }
	guard(database, reading, force);
}

/**
 * Refuses a frame an acquisition covers that does not carry the acquisition's key: the daemon's covers
 * every frame, a database's every frame whose subject is that database. An acquisition taken with
 * +changesOnly lets acsed's reading commands go, and release +force goes always; an actor's request always
 * needs the key, since acsed cannot tell its reads from its writes without names.
 * */
void guard(ACSED_Database database, boolean reading, boolean force) {
	if(force) { return; }
	String key = frame.key();
	if(daemonAcquisition != null && !(reading && daemonAcquisition.changesOnly()) && !daemonAcquisition.key().equals(key)) { throw new IllegalStateException("the daemon is acquired; give its key as the first argument"); }
	if(database == null) { return; }
	Acquisition acquisition = acquisitions.get(database.index);
	if(acquisition != null && !(reading && acquisition.changesOnly()) && !acquisition.key().equals(key)) { throw new IllegalStateException(database.name + " is acquired; give its key as the first argument"); }
}

String noted(String comment) {
	this.comment = comment;
	return comment;
}

String newKey() {
	String key;
	do { key = String.format("%08x", random.nextInt()); } while(isKey(key));
	return key;
}

boolean isKey(String key) {
	if(daemonAcquisition != null && daemonAcquisition.key().equals(key)) { return true; }
	for(Acquisition acquisition : acquisitions.values()) { if(acquisition.key().equals(key)) { return true; } }
	return false;
}

List<String> acquired() {
	ArrayList<String> names = new ArrayList<>();
	for(int index : acquisitions.keySet()) { names.add(indexed.get(index).name); }
	return names;
}

/** What inspectionState, waitInspectionReturn and an ended inspection answer with. */
static String position(ACS_Core core) { return "current activity index " + core.getJournal().getCurrentActivityIndex() + ", " + core.getJournal().getPendingActivityCount() + " pending"; }

static String underInspection(String name, String command) { return name + " is under inspection, so " + command + " would keep the inspected state; endInspection ends it"; }

/** The name the request gives, else the folder's own; refuses one in use, and one holding a colon, a tab or a line break, which an address or the list of databases could not carry. */
String nameFor(String calledBy, Path folder) {
	Path last = folder.getFileName();
	String name = !calledBy.isEmpty() ? calledBy : last == null ? "" : last.toString();
	if(name.isEmpty()) { throw new IllegalArgumentException("name the database with calledBy="); }
	if(name.chars().anyMatch(c -> c == ':' || c == '\t' || c == '\n' || c == '\r')) { throw new IllegalArgumentException("a database's name holds no colon, tab or line break; name it with calledBy="); }
	if(folder.toString().chars().anyMatch(c -> c == '\n' || c == '\r')) { throw new IllegalArgumentException("a database's folder holds no line break"); }
	ACSED_Database holder = databases.get(name);
	if(holder != null) { throw new IllegalArgumentException(name + " is in use, by " + holder.folder); }
	return name;
}

/** The name of a database other than {@code except} that holds the folder, or null. */
String holderOf(Path folder, ACSED_Database except) {
	for(var entry : databases.entrySet()) { if(entry.getValue() != except && entry.getValue().folder.equals(folder)) { return entry.getKey(); } }
	return null;
}

ACSED_Database database(int index) {
	ACSED_Database database = index >= 0 && index < indexed.size() ? indexed.get(index) : null;
	if(database == null) { throw new IllegalArgumentException("no database is at index " + index); }
	return database;
}

/** What a frame addresses, for the log: no names, since acsed reads none. */
static String describe(Frame one) {
	String code = one.parameters().length >= 4 ? Integer.toString(ByteBuffer.wrap(one.parameters(), 0, 4).getInt()) : "none";
	String timeout = one.timeout() == 0 ? "" : " timeout " + one.timeout();
	if(one.subject() == ACSED_Request.ACSED) { return "acsed code " + code + timeout; }
	String target = one.type() == ACSED_Request.ACSED ? "acsed" : "type " + one.type() + " index " + one.index() + " place " + one.place();
	return "database " + one.subject() + " " + target + " code " + code + timeout;
}

static DataInputStream parameters(Frame one) { return new DataInputStream(new ByteArrayInputStream(one.parameters())); }

/** The string an answer's returns hold: a refusal's reason, or a command's text. */
static String text(Answer answer) {
	try { return ACSED_Request.readString(new DataInputStream(new ByteArrayInputStream(answer.returns()))); }
	catch(IOException e) { return "(no text)"; }
}

/** The text's first line, with a count of the lines left out, so a frame stays one line of the log. */
static String firstLine(String text) {
	String[] lines = text.split("\n", -1);
	int more = lines.length - 1;
	return lines[0] + (more == 0 ? "" : more == 1 ? " (+1 line)" : " (+" + more + " lines)");
}

/** The path as given, refused when relative: the daemon's working folder is not the caller's. */
static Path absolute(String path) {
	Path given = Path.of(path);
	if(!given.isAbsolute()) { throw new IllegalArgumentException(path + " is relative; give an absolute path"); }
	return given.normalize();
}

static boolean isEmpty(Path folder) throws IOException {
	try(Stream<Path> entries = Files.list(folder)) { return entries.findAny().isEmpty(); }
}

/** Makes the folder, refusing a path that is not a folder or a folder holding files, as createDB does. */
static void makeEmptyFolder(Path folder) throws IOException {
	if(Files.exists(folder) && !Files.isDirectory(folder)) { throw new IllegalArgumentException(folder + " is not a folder"); }
	if(Files.isDirectory(folder) && !isEmpty(folder)) { throw new IllegalArgumentException(folder + " holds files"); }
	Files.createDirectories(folder);
}

/** A manager for a new folder, from the manager's jar acsed writes into it, as createDB makes one. */
static ACS_PersistenceManager newManager(Path folder) throws IOException {
	Path jar = folder.resolve(ACS_PersistenceManager.MANAGER);
	ACSED_PersistenceJar.write(jar, ACSE_PersistenceManagerBasic.class);
	return managerFrom(jar, folder);
}

/** What ACS keeps at a database's root: the pointer, the manager's jar, the numbered stored versions, and the language, which a load rewrites. */
static boolean isRecognized(Path entry) {
	String name = entry.getFileName().toString();
	return name.equals(ACS_PersistenceManager.POINTER) || name.equals(ACS_PersistenceManager.MANAGER) || isLanguage(entry) || (name.matches("\\d{1,9}") && Files.isDirectory(entry));
}

static boolean isLanguage(Path entry) { return entry.getFileName().toString().equals(ACSED_Request.DATABASE_LANGUAGE); }

/** Copies a file, or a folder with everything in it; a link is copied as a link. */
static void copyTree(Path from, Path to) throws IOException {
	try(Stream<Path> paths = Files.walk(from)) {
		for(Path path : (Iterable<Path>) paths::iterator) { Files.copy(path, to.resolve(from.relativize(path).toString()), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS); }
	}
}

static void deleteTree(Path folder) throws IOException {
	try(Stream<Path> paths = Files.walk(folder)) {
		for(Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) { Files.delete(path); }
	}
}

/**
 * The manager built from the folder's jar through its public {@code (Path)} constructor, as
 * {@link ACS_PersistenceManager#open} builds one; open itself needs the root file the first store
 * writes, which a folder being created has not got yet.
 * */
static ACS_PersistenceManager managerFrom(Path jar, Path folder) throws IOException {
	ACS_PersistenceManager.StoredClassLoader loader = new ACS_PersistenceManager.StoredClassLoader(jar.toUri().toURL(), ACS_PersistenceManager.class.getClassLoader());
	String name = ACSE_PersistenceManagerBasic.class.getName();
	try { return (ACS_PersistenceManager) Class.forName(name, true, loader).getConstructor(Path.class).newInstance(folder); }
	catch(ReflectiveOperationException | ClassCastException | LinkageError e) {
		loader.close();
		throw new IOException("cannot build " + name + " from " + jar + ": " + reason(e), e);
	}
}

/** One line of tab-separated fields, "-" standing for a field with nothing in it. */
static void line(StringBuilder lines, String... fields) {
	if(!lines.isEmpty()) { lines.append('\n'); }
	for(int i = 0; i < fields.length; i++) {
		if(i > 0) { lines.append('\t'); }
		lines.append(fields[i] == null ? "-" : fields[i]);
	}
}

static String withLines(String first, List<String> more) { return more.isEmpty() ? first : first + "\n" + String.join("\n", more); }

static String reason(Throwable e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getName(); }

Answer done(String text) {
	outcome = "done: " + firstLine(text);
	return new Answer(ACSED_Request.DONE, bytesOf(text));
}

Answer refused(String text) {
	outcome = "refused: " + firstLine(text);
	return new Answer(ACSED_Request.REFUSED, bytesOf(text));
}

static byte[] bytesOf(String text) {
	ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	try { ACSED_Request.writeString(new DataOutputStream(bytes), text); }
	catch(IOException e) { throw new IllegalStateException(e); }
	return bytes.toByteArray();
}


/**
 * A database folder's acse-language.txt as acsed holds it: the classes in their order in the file, each a
 * line {@code class <name> <count>} and then its requests' lines. A class is appended, never moved, so a
 * place a probe gave stays true until a load rewrites the file; the file is rewritten whole each time,
 * beside itself and renamed into place, so acsess reads it complete.
 * */
static final class Language {
	final Path file;
	final ArrayList<String> classes = new ArrayList<>();
	final StringBuilder text = new StringBuilder();

	Language(Path file) { this.file = file; }

	/** The language of a database just made or loaded: written anew with the classes of the core, its journal, and each arity and structure. */
	static Language of(Path folder, ACS_Core core) throws IOException {
		Language language = new Language(folder.resolve(ACSED_Request.DATABASE_LANGUAGE));
		language.addActors(core);
		if(language.text.isEmpty()) { language.write(language.text.toString()); }
		return language;
	}

	void addActors(ACS_Core core) throws IOException {
		place(core);
		place(core.getJournal());
		for(int slot = 0; slot < core.getArityCount(); slot++) { if(core.getArity(slot) != null) { place(core.getArity(slot)); } }
		for(int slot = 0; slot < core.getStructureCount(); slot++) { if(core.getStructure(slot) != null) { place(core.getStructure(slot)); } }
	}

	int placeOf(String name) { return classes.indexOf(name); }

	/** The class's place, its section appended and the file rewritten first when it is new; a failed write leaves the language as it was. */
	int place(ACS_Actor actor) throws IOException {
		String name = actor.getClass().getName();
		int place = classes.indexOf(name);
		if(place >= 0) { return place; }
		ByteArrayOutputStream lines = new ByteArrayOutputStream();
		actor.getRequestables(new DataOutputStream(lines));
		int length = text.length();
		section(text, name, lines.toString(StandardCharsets.UTF_8));
		try { write(text.toString()); }
		catch(IOException | RuntimeException e) {
			text.setLength(length);
			throw e;
		}
		classes.add(name);
		return classes.size() - 1;
	}

	void write(String content) throws IOException { ACSED_Request.replace(file, content); }

	static void section(StringBuilder text, String name, String lines) {
		text.append("class ").append(name).append(' ').append(lines.chars().filter(c -> c == '\n').count()).append('\n').append(lines);
	}
}

/** An acquisition's key, whether acsed's reading commands go without it, and its timeout in milliseconds, 0 for none, with the System.nanoTime it ends at. */
record Acquisition(String key, boolean changesOnly, long timeout, long until) {
	static Acquisition of(String key, boolean changesOnly, long timeout) { return new Acquisition(key, changesOnly, timeout, timeout == 0 ? 0 : ACSED_Commands.until(timeout)); }

	boolean expired(long now) { return timeout > 0 && now - until >= 0; }
}

/** A client a wait holds open, with its log line, its timeout in milliseconds, 0 for none, and the answer and its outcome once one is ready. */
static final class Held {
	final SocketChannel client;
	final OffsetDateTime time;
	final String note;
	final long timeout, until;
	Answer answer;
	String outcome;

	Held(SocketChannel client, OffsetDateTime time, String note, long timeout) {
		this.client = client;
		this.time = time;
		this.note = note;
		this.timeout = timeout;
		this.until = timeout == 0 ? 0 : until(timeout);
	}
}

}

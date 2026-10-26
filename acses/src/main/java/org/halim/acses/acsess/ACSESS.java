package org.halim.acses.acsess;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * The program a terminal runs: it turns a command line into frames, sends them, and prints the answers by
 * their types, a done one on the standard output with exit status 0, a refused one on the standard error
 * with 1. acsed reads no names, so the translation is all here: a first word holding a colon is an address,
 * {@code <database>:<actor>}, and the next word one of that actor's requests, as the database's
 * acse-language.txt gives them once a probe has said where the actor's class sits in it; any other first
 * word is one of acsed's own commands, as acsed-language.txt beside the lock gives them, and a command of a
 * database takes the database's name as its first plain argument. The database's name becomes its index
 * through acsed.databases. Two round trips a command, the probe and the request.
 * <p>
 * After the colon: nothing, for the core or the journal, whichever takes the request; {@code core};
 * {@code journal}; {@code arity-<k>}; {@code arity-<k>.row-<j>}, which fills the request's {@code catalog}
 * with j. The arguments fill a line's parameters: {@code +Name} a boolean one, matched ignoring the first
 * letter's case; {@code name=value} the parameter of that name, and {@code comment=} is the only way to the
 * comment; the plain arguments fill the rest in order; a string or a boolean not given is "" or false. Of
 * two requests sharing a name, the one the arguments fill is taken; an array is typed with commas between
 * its elements, and printed an element a line. A first argument of eight hexadecimal digits is an
 * acquisition's key. {@code timeout=<ms>}, anywhere among the arguments, is the frame's timeout, not a
 * parameter: acsed applies it to what the request starts - an acquisition, a held wait, an inspection -
 * and ends that once the time passes.
 * <p>
 * Only {@code boot} starts anything. With no daemon answering, it launches one as a separate process, so
 * this command line stays free, detached from the terminal, so closing the terminal leaves it running;
 * every other command fails at once when nothing answers, so a mistyped request never leaves a daemon
 * behind. Boot's {@code timeout=<ms>} is the daemon's default for a sensitive command that gives none, and
 * {@code +forbidDefaultTimeout} makes acsed refuse such a command instead; both are the daemon's arguments,
 * so they count only when this boot starts it. {@code beginSession} runs here alone: it reads requests line by line and sends nothing until a
 * line {@code Commit [+continueOnRefusal]}, which sends them all as one connection, so the daemon runs them
 * back to back.
 * */
public class ACSESS {

/** By name, not by import: this side knows the daemon only as a class to launch. */
static final String DAEMON = "org.halim.acses.acsed.ACSED_Daemon";
static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
/** How long boot waits for a started daemon's socket to answer. */
static final long WAIT_NANOS = 10_000_000_000L;
/** How a database's command says where its database's name goes, since its line does not hold it. */
static final String AFTER_NAME = ", the database's name first";
static final String USAGE = "usage: acsess [<key>] <command> [arguments], or acsess [<key>] <database>:[<actor>] <request> [arguments]";
/** The boot arguments that go to the daemon's command line rather than into boot's frame. */
static final String TIMEOUT = "timeout=", FORBID_DEFAULT = "+forbidDefaultTimeout";


public static void main(String[] args) {
	if(args.length == 0) { exit(ACSED_Request.REFUSED, USAGE); }
	Path socket = ACSED_Request.socket();
	List<String> words = List.of(args);
	try {
		if(words.get(0).equals("beginSession")) { session(socket, words); }
		else if(words.get(0).equals("boot")) { boot(socket, words); }
		else { once(socket, words); }
	} catch(NoDaemon e) { exit(ACSED_Request.REFUSED, e.getMessage()); }
	catch(IllegalArgumentException e) { exit(ACSED_Request.REFUSED, e.getMessage()); }
	catch(IOException e) { exit(ACSED_Request.REFUSED, "the daemon on " + socket + " broke off: " + e.getMessage()); }
}

/** One command, one frame. */
static void once(Path socket, List<String> words) throws IOException {
	Translated request = translate(socket, words);
	List<ACSED_Request.Answer> answers = ACSED_Request.send(socket, false, List.of(request.frame));
	if(answers == null) { throw new NoDaemon(socket); }
	ACSED_Request.Answer answer = answers.get(0);
	exit(answer.status(), printed(answer, request.line.result));
}

/**
 * Sends boot once acsed answers, launching it first when nothing does: as {@link #command} says, its input
 * from the null device and its output appended to the log, with the socket and boot's timeout arguments
 * as its arguments. The daemon's own answer names its pid and its timeouts: a boot racing this one may
 * have won the lock, and then the daemon that answers is not the process started here.
 * */
static void boot(Path socket, List<String> words) throws IOException {
	ArrayList<String> timeouts = new ArrayList<>(), rest = new ArrayList<>();
	for(String word : words) {
		if(word.startsWith(TIMEOUT)) { timeout(word); timeouts.add(word); }
		else if(word.equals(FORBID_DEFAULT)) { timeouts.add(word); }
		else { rest.add(word); }
	}
	if(timeouts.size() > 1) { throw new IllegalArgumentException("usage: boot [" + TIMEOUT + "<ms> | " + FORBID_DEFAULT + "]"); }
	if(answers(socket)) {
		if(!timeouts.isEmpty()) { System.err.println("warning: the daemon runs already, so " + timeouts.get(0) + " is not applied"); }
	} else {
		ACSED_Request.makeFolder(socket);
		Path log = ACSED_Request.log(socket);
		ProcessBuilder builder = new ProcessBuilder(command(socket, timeouts));
		builder.redirectInput(ProcessBuilder.Redirect.from(new File(WINDOWS ? "NUL" : "/dev/null")));
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
		builder.redirectErrorStream(true);
		Process daemon = builder.start();
		long until = System.nanoTime() + WAIT_NANOS;
		boolean alive = true;
		// Read before asking: a daemon that lost the lock to a racing boot exits, and the winner is still asked once more.
		while(!answers(socket)) {
			if(!alive || System.nanoTime() - until >= 0) { exit(ACSED_Request.REFUSED, (alive ? "no answer on " + socket + " within 10 s" : "the daemon exited with status " + daemon.exitValue()) + "; see " + log); }
			alive = daemon.isAlive();
			pause();
		}
	}
	once(socket, rest);
}

/** Whether something answers on the socket, by a connection carrying no frame, which acsed answers with nothing. */
static boolean answers(Path socket) {
	try { return ACSED_Request.send(socket, false, List.of()) != null; }
	catch(IOException e) { return false; }
}

/**
 * Queues one request per line of the standard input, split as {@link #split} says and translated at once,
 * with a prompt when it is a terminal; a line opening a multi-line quote goes on over the lines until it
 * closes; a line that cannot be split or translated is told on the standard error and the session goes on.
 * A Commit line sends the queue as one connection, and prints each request and its answer. The end of the
 * input without a Commit sends nothing.
 * */
static void session(Path socket, List<String> words) throws IOException {
	if(words.size() > 1) { exit(ACSED_Request.REFUSED, "usage: beginSession"); }
	BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
	boolean prompting = System.console() != null;
	ArrayList<Translated> queued = new ArrayList<>();
	ArrayList<String> typed = new ArrayList<>();
	while(true) {
		if(prompting) {
			System.out.print("acsess> ");
			System.out.flush();
		}
		String text = in.readLine();
		if(text == null) { exit(ACSED_Request.REFUSED, "the session ended without a Commit; nothing was sent"); }
		List<String> arguments;
		try {
			while((arguments = split(text)) == null) {
				if(prompting) {
					System.out.print("...> ");
					System.out.flush();
				}
				String more = in.readLine();
				if(more == null) { exit(ACSED_Request.REFUSED, "the session ended inside a multi-line quote; nothing was sent"); }
				text += "\n" + more;
			}
		} catch(IllegalArgumentException e) {
			System.err.println(e.getMessage() + "; the line is not queued");
			continue;
		}
		if(arguments.isEmpty()) { continue; }
		String command = arguments.get(0);
		if(command.equals("beginSession")) { System.err.println("a session is open already; the line is not queued"); continue; }
		if(command.equals("Commit")) {
			boolean continueOnRefusal = arguments.equals(List.of("Commit", "+continueOnRefusal"));
			if(arguments.size() > 1 && !continueOnRefusal) { System.err.println("usage: Commit [+continueOnRefusal]; the session goes on"); continue; }
			commit(socket, continueOnRefusal, queued, typed);
			return;
		}
		try {
			queued.add(translate(socket, arguments));
			typed.add(String.join(" ", arguments.stream().map(ACSESS::quoted).toList()));
		} catch(IllegalArgumentException | NoDaemon e) { System.err.println(e.getMessage() + "; the line is not queued"); }
	}
}

/** Sends the queue as one connection; the answer gives each request and its answer in order, and is a refusal when any request was refused or not run. */
static void commit(Path socket, boolean continueOnRefusal, List<Translated> queued, List<String> typed) throws IOException {
	if(queued.isEmpty()) { exit(ACSED_Request.DONE, "committed nothing"); }
	List<ACSED_Request.Answer> answers = ACSED_Request.send(socket, continueOnRefusal, queued.stream().map(request -> request.frame).toList());
	if(answers == null) { exit(ACSED_Request.REFUSED, new NoDaemon(socket).getMessage() + "; nothing was run"); }
	StringBuilder lines = new StringBuilder();
	boolean refusedAny = false;
	for(int i = 0; i < answers.size(); i++) {
		ACSED_Request.Answer answer = answers.get(i);
		String text = switch(answer.status()) {
			case ACSED_Request.DONE -> printed(answer, queued.get(i).line.result);
			case ACSED_Request.NOT_RUN -> "not run";
			default -> "refused: " + printed(answer, "String");
		};
		if(answer.status() != ACSED_Request.DONE) { refusedAny = true; }
		if(!lines.isEmpty()) { lines.append('\n'); }
		lines.append("> ").append(typed.get(i)).append("\n  ").append(text.replace("\n", "\n  "));
	}
	exit(refusedAny ? ACSED_Request.REFUSED : ACSED_Request.DONE, lines.toString());
}


/**
 * A command line as a frame and the line it was translated by. The key comes off first, then the
 * timeout; an address is probed, a command of acsed is looked up in acsed-language.txt, and either way
 * the arguments are written by the line they fill.
 * */
static Translated translate(Path socket, List<String> words) throws IOException {
	String key = "";
	if(words.size() > 1 && words.get(0).matches("[0-9a-f]{8}")) {
		key = words.get(0);
		words = words.subList(1, words.size());
	}
	long timeout = 0;
	ArrayList<String> kept = new ArrayList<>();
	for(String word : words) {
		if(!kept.isEmpty() && word.startsWith(TIMEOUT)) {
			if(timeout != 0) { throw new IllegalArgumentException(TIMEOUT + " is given twice"); }
			timeout = timeout(word);
		} else { kept.add(word); }
	}
	words = kept;
	String first = words.get(0);
	List<String> arguments = words.subList(1, words.size());
	List<Section> acsed = sections(ACSED_Request.language(socket), socket);
	if(first.indexOf(':') >= 0) { return addressed(socket, key, timeout, first, arguments, acsed); }
	List<Line> own = acsed.get(0).named(first), ofDatabase = acsed.get(1).named(first);
	int plain = firstPlain(arguments);
	if(!ofDatabase.isEmpty() && (own.isEmpty() || plain >= 0)) {
		if(plain < 0) { throw new IllegalArgumentException("usage: " + usage(ofDatabase, "", AFTER_NAME)); }
		ArrayList<String> rest = new ArrayList<>(arguments);
		rest.remove(plain);
		Line line = pick(ofDatabase, rest, -1, "", AFTER_NAME);
		byte[] parameters = written(line, fill(line, rest, -1));
		return new Translated(new ACSED_Request.Frame(database(socket, arguments.get(plain)).index, ACSED_Request.ACSED, 0, 0, key, timeout, parameters), line);
	}
	if(own.isEmpty()) {
		ArrayList<String> names = new ArrayList<>();
		for(Section section : acsed) { for(Line line : section.lines) { if(!names.contains(line.name)) { names.add(line.name); } } }
		throw new IllegalArgumentException("unknown command " + first + "; the commands: " + String.join(", ", names) + "; and beginSession, then Commit; or an address, <database>:<actor>");
	}
	Line line = pick(own, arguments, -1, "", "");
	return new Translated(new ACSED_Request.Frame(ACSED_Request.ACSED, 0, 0, 0, key, timeout, written(line, fill(line, arguments, -1))), line);
}

/** The milliseconds of a {@code timeout=<ms>}, refused unless positive. */
static long timeout(String word) {
	try {
		long timeout = Long.parseLong(word.substring(TIMEOUT.length()));
		if(timeout > 0) { return timeout; }
	} catch(NumberFormatException e) {}
	throw new IllegalArgumentException(word + ": a timeout is a positive number of milliseconds");
}

/** An actor's request: the database by its name, the actor by what follows the colon, its class's place by a probe, its lines from the database's language. */
static Translated addressed(Path socket, String key, long timeout, String address, List<String> arguments, List<Section> acsed) throws IOException {
	if(arguments.isEmpty()) { throw new IllegalArgumentException("usage: acsess " + address + " <request> [arguments]"); }
	int colon = address.lastIndexOf(':');
	Database database = database(socket, address.substring(0, colon));
	String actor = address.substring(colon + 1);
	int row = -1;
	ArrayList<int[]> targets = new ArrayList<>();
	if(actor.isEmpty()) {
		targets.add(new int[] {ACSED_Request.CORE, 0});
		targets.add(new int[] {ACSED_Request.JOURNAL, 0});
	} else if(actor.equals("core")) { targets.add(new int[] {ACSED_Request.CORE, 0}); }
	else if(actor.equals("journal")) { targets.add(new int[] {ACSED_Request.JOURNAL, 0}); }
	else if(actor.equals("description")) { targets.add(new int[] {ACSED_Request.DESCRIPTION, 0}); }
	else if(actor.matches("arity-\\d+(\\.row-\\d+)?")) {
		int dot = actor.indexOf(".row-");
		targets.add(new int[] {ACSED_Request.ARITY, number(actor.substring(6, dot < 0 ? actor.length() : dot))});
		if(dot >= 0) { row = number(actor.substring(dot + 5)); }
	} else if(actor.matches("structure-\\d+")) {
		targets.add(new int[] {ACSED_Request.STRUCTURE, number(actor.substring(10))});
	} else { throw new IllegalArgumentException(address + ": after the colon comes nothing, core, journal, description, arity-<k>, arity-<k>.row-<j> or structure-<k>"); }
	Line probe = only(acsed.get(1).named("probe"), "probe");
	ArrayList<ACSED_Request.Frame> probes = new ArrayList<>();
	for(int[] target : targets) { probes.add(new ACSED_Request.Frame(database.index, ACSED_Request.ACSED, 0, 0, key, 0, written(probe, List.of(Integer.toString(target[0]), Integer.toString(target[1]))))); }
	List<ACSED_Request.Answer> places = ACSED_Request.send(socket, false, probes);
	if(places == null) { throw new NoDaemon(socket); }
	List<Section> language = sections(database.folder.resolve(ACSED_Request.DATABASE_LANGUAGE), socket);
	String request = arguments.get(0);
	List<String> rest = arguments.subList(1, arguments.size());
	int found = -1, place = -1;
	List<Line> lines = List.of();
	for(int i = 0; i < targets.size(); i++) {
		ACSED_Request.Answer answer = places.get(i);
		if(answer.status() != ACSED_Request.DONE) { throw new IllegalArgumentException(printed(answer, "String")); }
		int at = new DataInputStream(new ByteArrayInputStream(answer.returns())).readInt();
		if(at < 0 || at >= language.size()) { throw new IllegalArgumentException("the probe gave place " + at + ", which " + database.folder.resolve(ACSED_Request.DATABASE_LANGUAGE) + " has not"); }
		List<Line> named = language.get(at).named(request);
		if(named.isEmpty()) { continue; }
		if(found >= 0) { throw new IllegalArgumentException("both the core and the journal take " + request + "; address one, " + address + "core or " + address + "journal"); }
		found = i;
		place = at;
		lines = named;
	}
	if(found < 0) {
		ArrayList<String> names = new ArrayList<>();
		for(int i = 0; i < targets.size(); i++) {
			int at = new DataInputStream(new ByteArrayInputStream(places.get(i).returns())).readInt();
			for(Line line : language.get(at).lines) { if(!names.contains(line.name)) { names.add(line.name); } }
		}
		throw new IllegalArgumentException(address + " takes no " + request + "; it takes " + String.join(", ", names));
	}
	if(row >= 0 && lines.stream().noneMatch(line -> line.names.contains("catalog"))) { throw new IllegalArgumentException(address + " names a row, and " + request + " takes no catalog"); }
	Line line = pick(lines, rest, row, address + " ", "");
	return new Translated(new ACSED_Request.Frame(database.index, targets.get(found)[0], targets.get(found)[1], place, key, timeout, written(line, fill(line, rest, row))), line);
}


/** The line the arguments fill; of several, the one leaving fewest strings to "", and two alike are refused as ambiguous. */
static Line pick(List<Line> lines, List<String> arguments, int row, String prefix, String suffix) {
	Line best = null;
	int bestLeft = Integer.MAX_VALUE;
	boolean tied = false;
	String reason = null;
	for(Line line : lines) {
		List<String> values;
		try { values = fill(line, arguments, row); }
		catch(IllegalArgumentException e) {
			reason = e.getMessage();
			continue;
		}
		int left = 0;
		for(int i = 0; i < values.size(); i++) { if(values.get(i) == null) { left++; } }
		if(left < bestLeft) { best = line; bestLeft = left; tied = false; }
		else if(left == bestLeft) { tied = true; }
	}
	if(best == null) { throw new IllegalArgumentException((lines.size() == 1 && reason != null ? reason + "; " : "") + "usage: " + usage(lines, prefix, suffix)); }
	if(tied) { throw new IllegalArgumentException("the arguments fill more than one " + best.name + "; usage: " + usage(lines, prefix, suffix)); }
	return best;
}

/**
 * The text of each parameter, in order, as the arguments fill it, the row's index filling {@code catalog};
 * a string left unfilled is null here, written as "", and a boolean left unfilled is false. Refuses a flag
 * the line has no parameter for, a parameter given twice, too many plain arguments, and a parameter left
 * that has no "" or false.
 * */
static List<String> fill(Line line, List<String> arguments, int row) {
	int count = line.names.size();
	String[] values = new String[count];
	if(row >= 0) {
		int at = line.names.indexOf("catalog");
		if(at < 0 || !line.types.get(at).equals("int")) { throw new IllegalArgumentException(line.name + " takes no int catalog for the row"); }
		values[at] = Integer.toString(row);
	}
	ArrayList<String> plain = new ArrayList<>();
	for(String argument : arguments) {
		if(argument.startsWith("+") && argument.length() > 1) {
			int at = parameter(line, argument.substring(1));
			if(at < 0 || !line.types.get(at).equals("boolean")) { throw new IllegalArgumentException(line.name + " takes no " + argument); }
			set(values, at, "true", argument);
			continue;
		}
		int equals = argument.indexOf('=');
		if(equals > 0 && isIdentifier(argument.substring(0, equals))) {
			int at = parameter(line, argument.substring(0, equals));
			if(at < 0 || line.types.get(at).equals("boolean")) { throw new IllegalArgumentException(line.name + " takes no " + argument.substring(0, equals + 1)); }
			if(equals == argument.length() - 1) { throw new IllegalArgumentException(argument + " needs a value"); }
			set(values, at, argument.substring(equals + 1), argument);
			continue;
		}
		plain.add(argument);
	}
	int next = 0;
	for(int i = 0; i < count && next < plain.size(); i++) {
		if(values[i] == null && !line.types.get(i).equals("boolean") && !line.names.get(i).equals("comment")) { values[i] = plain.get(next++); }
	}
	if(next < plain.size()) { throw new IllegalArgumentException(line.name + " takes " + next + " plain argument" + (next == 1 ? "" : "s") + ", given " + plain.size()); }
	for(int i = 0; i < count; i++) {
		if(values[i] != null) { continue; }
		String type = line.types.get(i);
		if(type.equals("boolean")) { values[i] = "false"; }
		else if(!type.equals("String")) { throw new IllegalArgumentException(line.name + " needs " + type + " " + line.names.get(i)); }
	}
	return Arrays.asList(values);
}

static void set(String[] values, int at, String value, String argument) {
	if(values[at] != null) { throw new IllegalArgumentException(argument + " fills a parameter given already"); }
	values[at] = value;
}

/** The parameter a flag names, its first letter's case ignored, or -1. */
static int parameter(Line line, String name) {
	for(int i = 0; i < line.names.size(); i++) {
		String parameter = line.names.get(i);
		if(parameter.length() == name.length() && parameter.regionMatches(true, 0, name, 0, 1) && parameter.regionMatches(1, name, 1, name.length() - 1)) { return i; }
	}
	return -1;
}

static boolean isIdentifier(String text) {
	if(!Character.isJavaIdentifierStart(text.charAt(0))) { return false; }
	return text.chars().allMatch(Character::isJavaIdentifierPart);
}

/** The first argument that is neither a switch nor a valued flag, or -1. */
static int firstPlain(List<String> arguments) {
	for(int i = 0; i < arguments.size(); i++) {
		String argument = arguments.get(i);
		int equals = argument.indexOf('=');
		if(argument.startsWith("+") || equals > 0 && isIdentifier(argument.substring(0, equals))) { continue; }
		return i;
	}
	return -1;
}

/** The parameters as the wire carries them: the request's code, then each value by its type. */
static byte[] written(Line line, List<String> values) {
	ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	DataOutputStream out = new DataOutputStream(bytes);
	try {
		out.writeInt(line.code);
		for(int i = 0; i < values.size(); i++) { write(out, line.types.get(i), values.get(i) == null ? "" : values.get(i), line.names.get(i)); }
	} catch(IOException e) { throw new IllegalStateException(e); }
	return bytes.toByteArray();
}

static void write(DataOutputStream out, String type, String value, String name) throws IOException {
	if(type.endsWith("[]")) {
		String element = type.substring(0, type.length() - 2);
		String[] parts = value.isEmpty() ? new String[0] : value.split(",", -1);
		out.writeInt(parts.length);
		for(String part : parts) { write(out, element, part, name); }
		return;
	}
	try {
		switch(type) {
			case "byte" -> out.writeByte(Byte.parseByte(value));
			case "short" -> out.writeShort(Short.parseShort(value));
			case "int" -> out.writeInt(Integer.parseInt(value));
			case "long" -> out.writeLong(Long.parseLong(value));
			case "float" -> out.writeFloat(Float.parseFloat(value));
			case "double" -> out.writeDouble(Double.parseDouble(value));
			case "boolean" -> out.writeBoolean(Boolean.parseBoolean(value));
			case "String" -> ACSED_Request.writeString(out, value);
			default -> throw new IllegalArgumentException("the language names a type " + type + ", which acsess cannot write");
		}
	} catch(NumberFormatException e) { throw new IllegalArgumentException(value + " is no " + type + " for " + name); }
}

/** The answer's returns as text: the result by its type, void as "done", a refusal's reason; an array an element a line. */
static String printed(ACSED_Request.Answer answer, String result) {
	DataInputStream in = new DataInputStream(new ByteArrayInputStream(answer.returns()));
	try {
		String text = answer.status() == ACSED_Request.DONE ? read(in, result) : ACSED_Request.readString(in);
		if(in.available() > 0) { text += "\n(" + in.available() + " bytes of the answer were left unread)"; }
		return text;
	} catch(IOException e) { return "(an answer acsess cannot read: " + e.getMessage() + ")"; }
}

static String read(DataInputStream in, String type) throws IOException {
	if(type.endsWith("[]")) {
		StringBuilder lines = new StringBuilder();
		for(int i = 0, count = in.readInt(); i < count; i++) {
			if(i > 0) { lines.append('\n'); }
			lines.append(read(in, type.substring(0, type.length() - 2)));
		}
		return lines.toString();
	}
	return switch(type) {
		case "void" -> "done";
		case "byte" -> Byte.toString(in.readByte());
		case "short" -> Short.toString(in.readShort());
		case "int" -> Integer.toString(in.readInt());
		case "long" -> Long.toString(in.readLong());
		case "float" -> Float.toString(in.readFloat());
		case "double" -> Double.toString(in.readDouble());
		case "boolean" -> Boolean.toString(in.readBoolean());
		case "String" -> ACSED_Request.readString(in);
		default -> throw new IOException("the language names a type " + type);
	};
}

static String usage(List<Line> lines, String prefix, String suffix) { return String.join(" | ", lines.stream().map(line -> prefix + line.text + suffix).toList()); }

static Line only(List<Line> lines, String name) {
	if(lines.size() != 1) { throw new IllegalArgumentException("acsed-language.txt has " + lines.size() + " " + name + " lines"); }
	return lines.get(0);
}

static int number(String digits) {
	try { return Integer.parseInt(digits); }
	catch(NumberFormatException e) { throw new IllegalArgumentException(digits + " is no index"); }
}


/** The sections of a language file: each a line {@code class <name> <count>}, then that many request lines. */
static List<Section> sections(Path file, Path socket) throws IOException {
	List<String> text;
	try { text = Files.readAllLines(file, StandardCharsets.UTF_8); }
	catch(NoSuchFileException e) {
		if(file.equals(ACSED_Request.language(socket))) { throw new NoDaemon(socket); }
		throw new IllegalArgumentException(file + " is missing; loadDB writes it");
	}
	ArrayList<Section> sections = new ArrayList<>();
	for(int at = 0; at < text.size(); ) {
		String[] head = text.get(at).split(" ");
		if(head.length != 3 || !head[0].equals("class")) { throw new IllegalArgumentException(file + ", line " + (at + 1) + ": no class line"); }
		int count = number(head[2]);
		if(at + 1 + count > text.size()) { throw new IllegalArgumentException(file + " ends inside " + head[1]); }
		ArrayList<Line> lines = new ArrayList<>();
		for(int code = 0; code < count; code++) { lines.add(Line.parse(code, text.get(at + 1 + code))); }
		sections.add(new Section(head[1], lines));
		at += 1 + count;
	}
	if(file.equals(ACSED_Request.language(socket)) && sections.size() != 2) { throw new IllegalArgumentException(file + " has " + sections.size() + " sections, not acsed's two"); }
	return sections;
}

/** The database a name stands for, by acsed.databases; an index is never given twice while acsed runs, so a stale list reaches no other database. */
static Database database(Path socket, String name) throws IOException {
	List<String> text;
	try { text = Files.readAllLines(ACSED_Request.databases(socket), StandardCharsets.UTF_8); }
	catch(NoSuchFileException e) { throw new NoDaemon(socket); }
	for(String line : text) {
		String[] fields = line.split("\t", 3);
		if(fields.length == 3 && fields[1].equals(name)) { return new Database(number(fields[0]), Path.of(fields[2])); }
	}
	throw new IllegalArgumentException("no database is called " + name);
}


/**
 * Splits a request as a shell would, without its expansions, or gives back null while a multi-line
 * quote is open, for the caller to add the next line. Blanks and line breaks separate arguments, and a
 * quote may sit inside an argument, as in {@code comment="a b"}. {@code ```...```} and
 * {@code """..."""} span lines and keep everything inside, every line break included; single
 * quotes and backquotes keep everything inside, on one line; double quotes the same, but for a
 * backslash before a double quote or a backslash; outside quotes a backslash keeps the next character.
 * A tilde is not expanded, so a path must be written absolute.
 * */
static List<String> split(String text) {
	ArrayList<String> arguments = new ArrayList<>();
	StringBuilder current = new StringBuilder();
	boolean inArgument = false;
	for(int i = 0; i < text.length(); i++) {
		char c = text.charAt(i);
		String fence = text.startsWith("```", i) ? "```" : text.startsWith("\"\"\"", i) ? "\"\"\"" : null;
		if(fence != null) {
			int end = text.indexOf(fence, i + 3);
			if(end < 0) { return null; }
			current.append(text, i + 3, end);
			i = end + 2;
			inArgument = true;
		} else if(c == '\'' || c == '`') {
			int end = text.indexOf(c, i + 1);
			if(end < 0 || text.substring(i + 1, end).indexOf('\n') >= 0) { throw new IllegalArgumentException("a " + (c == '`' ? "backquote" : "single quote") + " is not closed on its line"); }
			current.append(text, i + 1, end);
			i = end;
			inArgument = true;
		} else if(c == '"') {
			for(i++; i < text.length() && text.charAt(i) != '"' && text.charAt(i) != '\n'; i++) {
				if(text.charAt(i) == '\\' && i + 1 < text.length() && (text.charAt(i + 1) == '"' || text.charAt(i + 1) == '\\')) { i++; }
				current.append(text.charAt(i));
			}
			if(i == text.length() || text.charAt(i) == '\n') { throw new IllegalArgumentException("a double quote is not closed on its line"); }
			inArgument = true;
		} else if(c == '\\') {
			if(++i == text.length() || text.charAt(i) == '\n') { throw new IllegalArgumentException("a backslash ends the line"); }
			current.append(text.charAt(i));
			inArgument = true;
		} else if(Character.isWhitespace(c)) {
			if(inArgument) { arguments.add(current.toString()); }
			current.setLength(0);
			inArgument = false;
		} else {
			current.append(c);
			inArgument = true;
		}
	}
	if(inArgument) { arguments.add(current.toString()); }
	return arguments;
}

/** An argument as it would be typed: quoted when it is empty or holds a space, a quote or a backslash, with line breaks written as \n. */
static String quoted(String argument) {
	if(!argument.isEmpty() && argument.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\\')) { return argument; }
	return '"' + argument.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
}

/**
 * The same java and class path as this program, under setsid when the system has it, else nohup: either
 * way the daemon outlives the terminal. On Windows, neither: a process Java starts there gets a console of
 * its own, not the terminal's, so closing the terminal does not reach it.
 * */
static List<String> command(Path socket, List<String> timeouts) {
	String java = ProcessHandle.current().info().command().orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
	ArrayList<String> command = new ArrayList<>();
	if(!WINDOWS) { command.add(onPath("setsid") ? "setsid" : "nohup"); }
	command.addAll(List.of(java, "-cp", System.getProperty("java.class.path"), DAEMON, "socket=" + socket));
	command.addAll(timeouts);
	return command;
}

static boolean onPath(String program) {
	String path = System.getenv("PATH");
	if(path == null) { return false; }
	for(String folder : path.split(File.pathSeparator)) {
		if(!folder.isEmpty() && Files.isExecutable(Path.of(folder, program))) { return true; }
	}
	return false;
}

static void pause() {
	try { Thread.sleep(50); }
	catch(InterruptedException e) { Thread.currentThread().interrupt(); }
}

/** A done text on the standard output, anything else on the standard error, and the exit status to match; an empty text prints nothing. */
static void exit(int status, String text) {
	if(!text.isEmpty()) { (status == ACSED_Request.DONE ? System.out : System.err).println(text); }
	System.exit(status == ACSED_Request.DONE ? 0 : 1);
}


/** One line of a language: {@code name(type name, ...) result}, its code its place in its section. */
record Line(int code, String text, String name, List<String> types, List<String> names, String result) {
	static Line parse(int code, String text) {
		int open = text.indexOf('('), close = text.lastIndexOf(')');
		if(open <= 0 || close < open || close + 2 > text.length()) { throw new IllegalArgumentException("not a request line: " + text); }
		ArrayList<String> types = new ArrayList<>(), names = new ArrayList<>();
		String inside = text.substring(open + 1, close).trim();
		if(!inside.isEmpty()) {
			for(String parameter : inside.split(", ")) {
				String[] parts = parameter.split(" ");
				if(parts.length != 2) { throw new IllegalArgumentException("not a parameter: " + parameter + " in " + text); }
				types.add(parts[0]);
				names.add(parts[1]);
			}
		}
		return new Line(code, text, text.substring(0, open), types, names, text.substring(close + 2));
	}
}

/** A class's lines, in code order. */
record Section(String className, List<Line> lines) {
	List<Line> named(String name) { return lines.stream().filter(line -> line.name.equals(name)).toList(); }
}

record Database(int index, Path folder) {}

record Translated(ACSED_Request.Frame frame, Line line) {}

/** Nothing answers on the socket, or no acsed has written its files beside it. */
static final class NoDaemon extends IOException {
	NoDaemon(Path socket) { super("no daemon answers on " + socket + "; run acsess boot"); }
}

}

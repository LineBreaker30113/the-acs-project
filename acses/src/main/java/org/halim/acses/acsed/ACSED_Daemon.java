package org.halim.acses.acsed;

import org.halim.acses.acsess.ACSED_Request;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * The process that holds the databases, a pure server: its one input is the socket its boot names, as
 * {@code socket=<path>}, and what comes in is frames, not text; the boot may add {@code timeout=<ms>}, the
 * default timeout of a sensitive command, or {@code +forbidDefaultTimeout}. It serves one connection at a
 * time on one thread, which makes it the single writer: no core is ever reached by two requests at once.
 * Between connections it wakes when a timeout passes, and ends what that timeout was given to. Each frame
 * is logged on the standard output - its time, what it addresses and its code, its timeout, its comment
 * when its command takes one, and its outcome - and boot points the standard output at the log. A
 * waitInspectionReturn during an inspection is held open without a thread and answered after a later
 * connection or when a timeout passes; the frames of one connection run back to back.
 * <p>
 * The lock beside the socket is held for the daemon's whole life, and only its holder removes a socket
 * file nothing answers on and binds a new one, so two boots racing cannot take the socket from each
 * other. The lock file itself is never deleted: a daemon deleting it on the way out could let one
 * starting daemon lock the old file while another locks a new one.
 * */
public class ACSED_Daemon {

public static void main(String[] args) {
	Path socket = null;
	long timeout = 0;
	boolean forbid = false, usage = args.length == 0 || args.length > 2;
	for(String argument : args) {
		if(argument.startsWith("socket=") && argument.length() > 7 && socket == null) { socket = Path.of(argument.substring(7)).toAbsolutePath(); }
		else if(argument.startsWith("timeout=") && timeout == 0 && !forbid) { timeout = milliseconds(argument.substring(8)); }
		else if(argument.equals("+forbidDefaultTimeout") && timeout == 0 && !forbid) { forbid = true; }
		else { usage = true; }
	}
	if(socket == null || timeout < 0 || usage) {
		log(OffsetDateTime.now(), "stopped: usage: " + ACSED_Daemon.class.getName() + " socket=<path> [timeout=<ms> | +forbidDefaultTimeout]");
		System.exit(1);
	}
	try { serve(socket, timeout, forbid); }
	catch(IOException | RuntimeException e) {
		log(OffsetDateTime.now(), "stopped: " + ACSED_Commands.reason(e));
		System.exit(1);
	}
	log(OffsetDateTime.now(), "stopped");
}

/** A positive number of milliseconds, else -1. */
static long milliseconds(String digits) {
	try {
		long value = Long.parseLong(digits);
		return value > 0 ? value : -1;
	} catch(NumberFormatException e) { return -1; }
}

/**
 * Takes the lock, replaces a socket file nothing answers on, writes acsed's own language and an empty list
 * of databases beside the lock, and serves until a shutdown; the socket file goes with the daemon, the lock
 * file stays. It sleeps on a selector until a connection comes or the next timeout passes; a connection
 * accepted is read blocking, as before.
 * */
static void serve(Path socket, long timeout, boolean forbid) throws IOException {
	ACSED_Request.makeFolder(socket);
	Path lockFile = ACSED_Request.lock(socket);
	try(FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
		FileLock lock = lockChannel.tryLock();
		if(lock == null) { throw new IOException("another daemon holds " + lockFile); }
		if(answers(socket)) { throw new IOException("a daemon answers on " + socket + " without holding " + lockFile); }
		Files.deleteIfExists(socket);
		ACSED_Commands commands = new ACSED_Commands(socket, timeout, forbid);
		commands.writeLanguage();
		commands.writeDatabases();
		try(ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX); Selector selector = Selector.open()) {
			server.bind(UnixDomainSocketAddress.of(socket));
			server.configureBlocking(false);
			server.register(selector, SelectionKey.OP_ACCEPT);
			log(OffsetDateTime.now(), "listening on " + socket + ", pid " + ProcessHandle.current().pid() + ", " + commands.timeouts());
			while(!commands.stopping) {
				selector.select(commands.untilNext());
				selector.selectedKeys().clear();
				expire(commands);
				SocketChannel client = server.accept();
				if(client == null) { continue; }
				if(!answer(client, commands)) { client.close(); }
				answerHeld(commands);
			}
		} finally { Files.deleteIfExists(socket); }
	}
}

/** Whether something answers on the socket; the probe closes at once, which the other end logs as an unreadable connection. */
static boolean answers(Path socket) {
	try(SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socket))) { return true; }
	catch(IOException e) { return false; }
}

/**
 * Reads one connection's frames, runs them and answers each; a connection that cannot be read, or answers
 * that cannot be sent, are logged and dropped, so a broken peer never stops the daemon. Gives back whether
 * a wait holds the client, which then stays open until {@link #answerHeld} answers it.
 * */
static boolean answer(SocketChannel client, ACSED_Commands commands) {
	OffsetDateTime time = OffsetDateTime.now();
	ACSED_Request.Connection connection;
	try { connection = ACSED_Request.readConnection(new DataInputStream(new BufferedInputStream(Channels.newInputStream(client)))); }
	catch(IOException e) {
		log(time, "unreadable connection: " + ACSED_Commands.reason(e));
		return false;
	}
	ArrayList<String> notes = new ArrayList<>();
	List<ACSED_Request.Answer> answers = commands.run(connection, notes);
	if(answers == null) {
		commands.hold(client, time, notes.get(0));
		log(time, notes.get(0) + " -> held");
		return true;
	}
	send(client, time, notes, answers);
	return false;
}

/** Logs what passed its timeout and was ended, then sends the answers that came ready by it. */
static void expire(ACSED_Commands commands) throws IOException {
	OffsetDateTime time = OffsetDateTime.now();
	for(String line : commands.expire()) { log(time, line); }
	answerHeld(commands);
}

/** Sends the answers that came ready for held clients, each logged under the time of its connection, and closes them. */
static void answerHeld(ACSED_Commands commands) throws IOException {
	for(ACSED_Commands.Held held : commands.ready) {
		try(held.client) { send(held.client, held.time, List.of(held.note + " -> " + held.outcome), List.of(held.answer)); }
	}
	commands.ready.clear();
}

static void send(SocketChannel client, OffsetDateTime time, List<String> notes, List<ACSED_Request.Answer> answers) {
	String sent = "";
	try {
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(client)));
		for(ACSED_Request.Answer answer : answers) { ACSED_Request.writeAnswer(out, answer); }
		out.flush();
	} catch(IOException e) { sent = " (not sent: " + ACSED_Commands.reason(e) + ")"; }
	for(String note : notes) { log(time, note + sent); }
}

/** An argument as it would be typed: quoted when it is empty or holds a space, a quote or a backslash, with line breaks written as \n. */
static String quoted(String argument) {
	if(!argument.isEmpty() && argument.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\\')) { return argument; }
	return '"' + argument.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
}

static void log(OffsetDateTime time, String line) { System.out.println(time + " " + line); }

}

package org.halim.acses.acsess;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;


/**
 * The one format both sides speak, and the place where they meet. A connection carries a byte, 1 to run
 * past a refusal and 0 to stop at the first, a count, then that many frames; the answer is one per frame,
 * in order: a status - {@link #DONE}, {@link #REFUSED}, or {@link #NOT_RUN} for a frame after a stop -
 * then the returns, as their byte length and the bytes. A frame is its subject, an int: a database's index,
 * or {@link #ACSED} for acsed itself; for a database, the type and the index of what it addresses, and for
 * an actor the place of its class in the database's {@value #DATABASE_LANGUAGE}; the acquisition key, ""
 * without one; the timeout, a long of milliseconds, 0 without one; then the parameters, as their byte
 * length and the bytes, starting with the request's code.
 * A string is its UTF-8 byte length as an int, then the bytes, and an int is four bytes, high first, as
 * {@link DataOutputStream} writes it. Nothing ends a part but its own length, so a side that reads less or
 * more than a length says is talking to a broken peer, not to a short message.
 * <p>
 * The socket is {@code $ACSED_SOCKET} when it is set, for checks; else {@code acses/acsed.sock} under
 * {@code $XDG_RUNTIME_DIR}; else {@code acses-<user>/acsed.sock} under the JDK's temporary folder. acsess
 * gives that path to acsed at boot, as {@code socket=<path>}. The log, the lock, {@value #LANGUAGE} and
 * {@value #DATABASES} sit beside it, and its folder is made open to its owner only, which is all the access
 * control there is for now.
 * <p>
 * This side imports only the JDK, so it can move into a program of its own unchanged.
 * */
public class ACSED_Request {

public static final int DONE = 0, REFUSED = 1, NOT_RUN = 2;
/** What a frame addresses: acsed, as a subject or as a database's type; and the actors' types. */
public static final int ACSED = -1, DESCRIPTION = 0, CORE = 1, JOURNAL = 2, ARITY = 3, ROW = 4, STRUCTURE = 5, PROCEDURE = 7;
public static final String LOG = "acsed.log", LOCK = "acsed.lock", LANGUAGE = "acsed-language.txt", DATABASES = "acsed.databases",
	DATABASE_LANGUAGE = "acse-language.txt";


/**
 * Where acsed listens, read from the environment the same way by every acsess, so a later one finds the
 * acsed an earlier one booted. A relative {@code $XDG_RUNTIME_DIR} is ignored, as its specification says.
 * */
public static Path socket() {
	String override = System.getenv("ACSED_SOCKET");
	if(override != null && !override.isEmpty()) { return Path.of(override).toAbsolutePath(); }
	String runtime = System.getenv("XDG_RUNTIME_DIR");
	if(runtime != null && Path.of(runtime).isAbsolute()) { return Path.of(runtime, "acses", "acsed.sock"); }
	return Path.of(System.getProperty("java.io.tmpdir"), "acses-" + System.getProperty("user.name"), "acsed.sock");
}

public static Path log(Path socket) { return socket.resolveSibling(LOG); }

public static Path lock(Path socket) { return socket.resolveSibling(LOCK); }

/** acsed's own commands, a section per class as a database's {@value #DATABASE_LANGUAGE} has them: subject -1 first, then a database's acsed type. */
public static Path language(Path socket) { return socket.resolveSibling(LANGUAGE); }

/** A line per database acsed holds: its index, its name and its folder, tab-separated, the folder last. */
public static Path databases(Path socket) { return socket.resolveSibling(DATABASES); }

/**
 * Makes the socket's folder, open to its owner only, when it is not there yet: by its POSIX permissions, or
 * where there are none, as on Windows, by an ACL allowing the owner alone; a folder already there is taken
 * as it is.
 * */
public static void makeFolder(Path socket) throws IOException {
	Path folder = socket.getParent();
	if(Files.isDirectory(folder)) { return; }
	if(folder.getFileSystem().supportedFileAttributeViews().contains("posix")) { Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
	else { Files.createDirectories(folder, ownerOnly(folder)); }
}

/** An ACL of one entry: the user running this program, allowed everything on the folder, and inherited by every file and folder made in it. */
static FileAttribute<List<AclEntry>> ownerOnly(Path folder) throws IOException {
	UserPrincipal owner = folder.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
	AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(AclEntryPermission.values())
		.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build();
	return new FileAttribute<>() {
		public String name() { return "acl:acl"; }
		public List<AclEntry> value() { return List.of(entry); }
	};
}

/** Writes the text beside the file and renames it into place, so a reader sees the old text or the new, never a part. */
public static void replace(Path file, String text) throws IOException {
	Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
	Files.writeString(temporary, text, StandardCharsets.UTF_8);
	Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
}


/**
 * Sends the frames as one connection and gives back an answer per frame, or null when nothing answers on
 * the socket. acsed answering and then breaking off is an IOException, not a null: it was there.
 * */
public static List<Answer> send(Path socket, boolean continueOnRefusal, List<Frame> frames) throws IOException {
	SocketChannel channel;
	try { channel = SocketChannel.open(UnixDomainSocketAddress.of(socket)); }
	catch(IOException e) { return null; }
	try(channel) {
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
		writeConnection(out, new Connection(continueOnRefusal, frames));
		out.flush();
		DataInputStream in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
		ArrayList<Answer> answers = new ArrayList<>();
		for(int i = 0; i < frames.size(); i++) { answers.add(readAnswer(in)); }
		return answers;
	}
}

public static void writeConnection(DataOutputStream out, Connection connection) throws IOException {
	out.writeByte(connection.continueOnRefusal ? 1 : 0);
	out.writeInt(connection.frames.size());
	for(Frame frame : connection.frames) {
		out.writeInt(frame.subject);
		if(frame.subject != ACSED) {
			out.writeInt(frame.type);
			out.writeInt(frame.index);
			if(frame.type != ACSED) { out.writeInt(frame.place); }
		}
		writeString(out, frame.key);
		out.writeLong(frame.timeout);
		writeBytes(out, frame.parameters);
	}
}

/** Refuses a negative count; a count larger than what follows ends in an EOFException, so a garbled connection costs nothing but itself. */
public static Connection readConnection(DataInputStream in) throws IOException {
	byte continueOnRefusal = in.readByte();
	if(continueOnRefusal != 0 && continueOnRefusal != 1) { throw new IOException("continue byte " + continueOnRefusal); }
	int count = in.readInt();
	if(count < 0) { throw new IOException("frame count " + count); }
	ArrayList<Frame> frames = new ArrayList<>();
	for(int i = 0; i < count; i++) {
		int subject = in.readInt(), type = 0, index = 0, place = 0;
		if(subject != ACSED) {
			type = in.readInt();
			index = in.readInt();
			if(type != ACSED) { place = in.readInt(); }
		}
		String key = readString(in);
		long timeout = in.readLong();
		frames.add(new Frame(subject, type, index, place, key, timeout, readBytes(in)));
	}
	return new Connection(continueOnRefusal == 1, frames);
}

public static void writeAnswer(DataOutputStream out, Answer answer) throws IOException {
	out.writeInt(answer.status);
	writeBytes(out, answer.returns);
}

public static Answer readAnswer(DataInputStream in) throws IOException {
	int status = in.readInt();
	return new Answer(status, readBytes(in));
}

public static void writeString(DataOutputStream out, String string) throws IOException { writeBytes(out, string.getBytes(StandardCharsets.UTF_8)); }

public static String readString(DataInputStream in) throws IOException { return new String(readBytes(in), StandardCharsets.UTF_8); }

public static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
	out.writeInt(bytes.length);
	out.write(bytes);
}

/** Takes the bytes as they arrive rather than allocating the length first, so a garbled length ends in an EOFException, not in the whole heap. */
public static byte[] readBytes(DataInputStream in) throws IOException {
	int length = in.readInt();
	if(length < 0) { throw new IOException("length " + length); }
	byte[] bytes = in.readNBytes(length);
	if(bytes.length < length) { throw new EOFException(length + " bytes end after " + bytes.length); }
	return bytes;
}


/** The frames of one connection, and whether acsed runs past a refusal among them. */
public record Connection(boolean continueOnRefusal, List<Frame> frames) {}

/** One request as it travels; {@code type}, {@code index} and {@code place} are 0 where the subject or the type has none, {@code timeout} where the request gives none. */
public record Frame(int subject, int type, int index, int place, String key, long timeout, byte[] parameters) {
	public Frame(int subject, int type, int index, int place, String key, byte[] parameters) {
		this(subject, type, index, place, key, 0L, parameters);
	}
}

/** A status and the returns: the result as its type is written, or the reason of a refusal as a string. */
public record Answer(int status, byte[] returns) {}

}

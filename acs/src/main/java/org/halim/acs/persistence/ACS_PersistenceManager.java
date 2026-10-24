package org.halim.acs.persistence;

import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Keeps a core's stores under a folder and loads the current one back. The folder's {@value #POINTER}
 * starts with what every manager writes - "ACSE", the file's format, the manager's class name - and the
 * folder's {@value #MANAGER} holds that class, so {@link #open} builds the manager that made a folder from
 * the folder alone; the rest of the file is that manager's.
 * <p>
 * A stored folder is the authority on implementations: {@link #loadCore} builds the core and the journal
 * its indexer names from the folder's own jar, and every class the folder names through the core, so a
 * program loading a folder needs only the contracts - the engine's public interfaces - the JDK and the
 * libraries.
 * <p>
 * The static methods are what every {@code saveTo} and static {@code loadFrom} share. A part is written as
 * its class's number in the folder's indexer, then its own data, and rebuilt by that class's
 * {@code public static loadFrom(ACS_Core, ACS_Journal, DataInputStream)}, which reads before it
 * constructs, so the class keeps its fields final. Java cannot make an interface require a static method,
 * so it is looked up by name: a store leaves out a part whose class has none - it likely does not need to
 * persist - with a warning, and a load refuses such a class, naming it.
 * <p>
 * Everything here, the nested types included, stays on the program's side of a load, so nothing here
 * may refer to an implementation.
 * */
public interface ACS_PersistenceManager {

/** The file at the folder's root naming its manager, followed by what that manager keeps there. */
String POINTER = "acse-persistence.data";
/** The jar at the folder's root holding its manager's classes, placed there when the folder is created. */
String MANAGER = "acse-persistence.jar";
/** The file at a stored folder's root naming its core and every class its files use. */
String INDEXER = "indexer.bin";
/** The jar of the classes a stored folder needs, beside its data. */
String CLASSES = "classes.jar";
/** "ACSE", then the format, so a file another program made is refused rather than misread. */
int MAGIC = 0x41435345, FORMAT = 0;


/** Stores the core as the folder's new current store; gives back the store's warnings. */
List<String> store(ACS_Core core) throws IOException;

/** The folder holding the current store. */
Path current() throws IOException;

/** The core of the current store, built from that store's own classes. */
default ACS_Core load() throws IOException { return loadCore(current()); }


/**
 * The manager that made the folder, as its root file names it, built through its public {@code (Path)}
 * constructor from the folder's {@value #MANAGER} - from the program, for a folder without one.
 * */
static ACS_PersistenceManager open(Path folder) throws IOException {
	Path pointer = folder.resolve(POINTER);
	if(!Files.exists(pointer)) { throw new NoSuchFileException(pointer.toString(), null, "nothing was stored in this folder"); }
	String name;
	try(DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(pointer)))) { name = readHeader(dis, pointer); }
	Path jar = folder.resolve(MANAGER);
	ClassLoader program = ACS_PersistenceManager.class.getClassLoader();
	ClassLoader loader = Files.exists(jar) ? new StoredClassLoader(jar.toUri().toURL(), program) : program;
	try {
		Class<?> manager = Class.forName(name, true, loader);
		if(!ACS_PersistenceManager.class.isAssignableFrom(manager)) { throw new IOException(pointer + " names " + name + ", which is no persistence manager"); }
		return (ACS_PersistenceManager) manager.getConstructor(Path.class).newInstance(folder);
	} catch(ClassNotFoundException e) { throw new IOException(pointer + " names " + name + ", which is no persistence manager this program has", e); }
	catch(ReflectiveOperationException e) { throw new IOException("cannot build " + name + " through a public (Path) constructor", e); }
}

/**
 * Builds the core a stored folder's indexer names, with the journal it names beside it, then fills the
 * core from the folder. Both come from the folder's jar - the core through its public constructor
 * taking a {@code Function<ACS_Core, ACS_Journal>}, the journal through its public
 * {@code (ACS_Core)} constructor, which that function calls - and with them every class the folder
 * names, since the core's load finds them through the core's own class loader.
 * */
static ACS_Core loadCore(Path folder) throws IOException {
	Path indexer = folder.resolve(INDEXER);
	String name, journalName;
	try(DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(indexer)))) {
		name = readHeader(dis, indexer);
		journalName = readString(dis);
	}
	Path jar = folder.resolve(CLASSES);
	ClassLoader program = ACS_PersistenceManager.class.getClassLoader();
	ClassLoader loader = Files.exists(jar) ? new StoredClassLoader(jar.toUri().toURL(), program) : program;
	Constructor<?> journal = journalConstructor(journalName, loader, indexer);
	Function<ACS_Core, ACS_Journal> building = forCore -> {
		try { return (ACS_Journal) journal.newInstance(forCore); }
		catch(ReflectiveOperationException e) { throw new IllegalStateException("cannot build the journal " + journalName, e); }
	};
	Object core;
	try { core = Class.forName(name, true, loader).getConstructor(Function.class).newInstance(building); }
	catch(ReflectiveOperationException | LinkageError e) { throw new IOException("cannot build the core " + name + " through a public constructor taking its journal's factory", e); }
	if(!(core instanceof ACS_Core built)) { throw new IOException(indexer + " names " + name + ", which is no core"); }
	built.load(folder);
	return built;
}

/** The journal class's public {@code (ACS_Core)} constructor, refusing a class that is no journal. */
private static Constructor<?> journalConstructor(String name, ClassLoader loader, Path indexer) throws IOException {
	try {
		Class<?> journal = Class.forName(name, true, loader);
		if(!ACS_Journal.class.isAssignableFrom(journal)) { throw new IOException(indexer + " names " + name + ", which is no journal"); }
		return journal.getConstructor(ACS_Core.class);
	} catch(ReflectiveOperationException | LinkageError e) { throw new IOException("cannot build the journal " + name + " through a public (ACS_Core) constructor", e); }
}

/** What every root file and indexer starts with: "ACSE", the format, and the class that wrote it. */
static void writeHeader(DataOutputStream dos, Class<?> writer) throws IOException {
	dos.writeInt(MAGIC);
	dos.writeInt(FORMAT);
	writeString(dos, writer.getName());
}

/** Gives back the writer's class name, refusing a file another program or another format wrote. */
static String readHeader(DataInputStream dis, Path file) throws IOException {
	try {
		if(dis.readInt() != MAGIC) { throw new IOException(file + " is not an ACSE persistence file"); }
		int format = dis.readInt();
		if(format != FORMAT) { throw new IOException(file + " has format " + format + ", this program reads " + FORMAT); }
		return readString(dis);
	} catch(EOFException e) { throw (EOFException) new EOFException(file + " ends inside its header").initCause(e); }
}


/** A string that may be null, as {@link ACS_Actor#writeString} writes it, the one string form of ACS. */
static void writeString(DataOutputStream dos, String string) throws IOException { ACS_Actor.writeString(dos, string); }

static String readString(DataInputStream dis) throws IOException { return ACS_Actor.readString(dis); }

/**
 * Writes the part's class as its number in the indexer, and answers whether the part's own data follows,
 * from its {@code saveTo}. No part is -1, and so is a part left out because its class has no static
 * {@code loadFrom}; both load back as null.
 * */
static boolean writeClass(DataOutputStream dos, Object part) throws IOException {
	if(!(dos instanceof ClassNumbering numbering)) { throw new IllegalArgumentException("a part's class is written only inside a store"); }
	int number = numbering.numberOf(part);
	dos.writeInt(number);
	return number >= 0;
}

/** Reads what {@link #writeClass} and the part's {@code saveTo} wrote, through that class's static {@code loadFrom}; null for no part. */
static <T> T readPart(DataInputStream dis, Class<T> type, ACS_Core core, ACS_Journal journal) throws IOException {
	if(!(dis instanceof PartBuilding building)) { throw new IllegalArgumentException("a part is read only inside a load"); }
	int number = dis.readInt();
	return number == -1 ? null : building.build(number, type, core, journal);
}

/** The folder a store fills, for a part that keeps files of its own beside its data: a column in a file copies the file there. */
static Path folderOf(DataOutputStream dos) {
	if(!(dos instanceof InFolder in)) { throw new IllegalArgumentException("a part's folder is known only inside a store"); }
	return in.folder();
}

/** The folder a load reads, where a part finds the files its store copied. */
static Path folderOf(DataInputStream dis) {
	if(!(dis instanceof InFolder in)) { throw new IllegalArgumentException("a part's folder is known only inside a load"); }
	return in.folder();
}

/**
 * The root of the database a store writes from, as its core gives it, so a part keeping a file under that
 * root can store the file's path relative to it, and the file follows its database to a copy or a
 * migration; null for a core without one.
 * */
static Path rootOf(DataOutputStream dos) {
	if(!(dos instanceof InFolder in)) { throw new IllegalArgumentException("a part's root is known only inside a store"); }
	return in.root();
}

/** The stream of a store or a load: it knows the folder its file is in, and a store the root of its core's database. */
interface InFolder {
	Path folder();
	/** Null for a load, and for a core without a root. */
	default Path root() { return null; }
}

/** The stream a store writes each file through: it gives each part's class its number in the indexer. */
interface ClassNumbering {
	/** -1 for no part, and for one left out. */
	int numberOf(Object part) throws IOException;
}

/** The stream a load reads each file through: it turns a class number back into a part. */
interface PartBuilding {
	<T> T build(int number, Class<T> type, ACS_Core core, ACS_Journal journal) throws IOException;
}

/**
 * Takes every class its jar holds from the jar, before asking the program - the reverse of Java's usual
 * order - and the rest from the program: the contracts, the JDK, the libraries. A class it takes and a
 * program class of the same package are in different packages to Java, so neither reaches the other's
 * package-private members.
 * */
final class StoredClassLoader extends URLClassLoader {

	public StoredClassLoader(URL jar, ClassLoader program) {
		super(new URL[] { jar }, program);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized(getClassLoadingLock(name)) {
			Class<?> loaded = findLoadedClass(name);
			if(loaded == null && !name.startsWith("java.") && findResource(name.replace('.', '/') + ".class") != null) { loaded = findClass(name); }
			if(loaded == null) { return super.loadClass(name, resolve); }
			if(resolve) { resolveClass(loaded); }
			return loaded;
		}
	}

	/** The jar first for files too, so storing again copies the class file in use. */
	@Override
	public URL getResource(String name) {
		URL found = findResource(name);
		return found != null ? found : super.getResource(name);
	}
}

}

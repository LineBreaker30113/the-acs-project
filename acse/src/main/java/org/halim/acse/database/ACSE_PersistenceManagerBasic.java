package org.halim.acse.database;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_PersistenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores a core into a new numbered folder under {@link #folder} each time - 000001, 000002, ... - and
 * only then points {@value ACS_PersistenceManager#POINTER} at it, so a store that fails halfway leaves
 * the previous generation current; earlier generations are kept. The pointer holds the header every
 * manager writes, then the current generation's number. What goes inside a generation is the core's own
 * {@link ACS_Core#store}.
 * <p>
 * The static half is the machinery of a stored folder, which the core's store and load use: its files,
 * the class numbers behind {@link ACS_PersistenceManager#writeClass}, and the classes it carries - the
 * core's class, every class the store named, and what they reach from the same places, except the
 * contracts. Loading a folder runs the code it carries: load only folders trusted as much as a program.
 * */
public class ACSE_PersistenceManagerBasic implements ACS_PersistenceManager {
/** One date on every jar entry, so the same classes make the same jar. */
static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
/** An {@code L...;} in a descriptor or a signature, for the classes a class file names outside its class entries. */
static final Pattern DESCRIBED = Pattern.compile("L([\\p{L}\\p{N}_$]+(?:/[\\p{L}\\p{N}_$]+)+)[;<]");

/** Where the generations are. */
public final Path folder;

public ACSE_PersistenceManagerBasic(Path folder) {
	this.folder = folder;
}


/** Stores the core into a new generation, then makes it current. A failed store leaves a stray folder and the previous generation current. */
@Override
public List<String> store(ACS_Core core) throws IOException {
	Files.createDirectories(folder);
	int number = lastGeneration() + 1;
	List<String> warnings = core.store(Files.createDirectory(generation(number)));
	makeCurrent(number);
	return warnings;
}

/** The generation the pointer names, refusing a pointer another manager wrote. */
@Override
public Path current() throws IOException {
	Path pointer = folder.resolve(POINTER);
	if(!Files.exists(pointer)) { throw new NoSuchFileException(pointer.toString(), null, "nothing was stored in this folder"); }
	int number = read(pointer, null, dis -> {
		String manager = ACS_PersistenceManager.readHeader(dis, pointer);
		if(!manager.equals(getClass().getName())) { throw new IOException(pointer + " belongs to " + manager + ", not " + getClass().getName()); }
		return dis.readInt();
	});
	if(number < 1) { throw new IOException(pointer + " names generation " + number); }
	return generation(number);
}

Path generation(int number) { return folder.resolve(String.format("%06d", number)); }

/** The highest numbered generation, or 0 for a folder never stored into. */
int lastGeneration() throws IOException {
	int last = 0;
	try(DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
		for(Path entry : entries) {
			String name = entry.getFileName().toString();
			if(name.matches("\\d{1,9}") && Files.isDirectory(entry)) { last = Math.max(last, Integer.parseInt(name)); }
		}
	}
	return last;
}

/** Replaces the pointer by a rename, so a crash leaves the old pointer or the new one, never half of either. */
void makeCurrent(int number) throws IOException {
	Path temporary = folder.resolve(POINTER + ".tmp");
	write(temporary, null, dos -> { ACS_PersistenceManager.writeHeader(dos, getClass()); dos.writeInt(number); return true; });
	Files.move(temporary, folder.resolve(POINTER), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	syncDirectory(folder);
}


/** A part of a known class, through its static {@code loadFrom}; an IOException or a runtime one it throws comes out unwrapped. */
static <T> T build(Class<?> cls, Class<T> type, ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	if(!type.isAssignableFrom(cls)) { throw new IOException(cls.getName() + " is not a " + type.getSimpleName()); }
	Object part;
	try { part = loadFromOf(cls).invoke(null, core, journal, dis); }
	catch(IllegalAccessException e) { throw new IOException("cannot call " + cls.getName() + ".loadFrom", e); }
	catch(InvocationTargetException e) {
		if(e.getCause() instanceof IOException io) { throw io; }
		if(e.getCause() instanceof RuntimeException runtime) { throw runtime; }
		if(e.getCause() instanceof Error error) { throw error; }
		throw new IOException(cls.getName() + ".loadFrom failed", e.getCause());
	}
	if(part == null) { throw new IOException(cls.getName() + ".loadFrom gave back nothing"); }
	return type.cast(part);
}

/** The class's own static {@code loadFrom}; one inherited from a superclass would build the superclass. */
static Method loadFromOf(Class<?> cls) throws IOException {
	Method method = null;
	try { method = cls.getMethod("loadFrom", ACS_Core.class, ACS_Journal.class, DataInputStream.class); }
	catch(NoSuchMethodException e) { }
	if(method == null || method.getDeclaringClass() != cls || !Modifier.isStatic(method.getModifiers()) || !cls.isAssignableFrom(method.getReturnType())) {
		throw new IOException(cls.getName() + " declares no public static " + cls.getSimpleName() + " loadFrom(ACS_Core, ACS_Journal, DataInputStream)");
	}
	// A public method of a package-private class is still out of reach without this.
	method.trySetAccessible();
	return method;
}


/** The names, in number order. */
static void writeClasses(DataOutputStream dos, ClassTable table) throws IOException {
	dos.writeInt(table.classes.size());
	for(Class<?> cls : table.classes) { ACS_PersistenceManager.writeString(dos, cls.getName()); }
}

/** The classes by number, every one found and checked for a static {@code loadFrom} before any data is read, the failures named together. */
static Class<?>[] readClasses(DataInputStream dis, ClassLoader loader) throws IOException {
	Class<?>[] classes = new Class<?>[dis.readInt()];
	ArrayList<String> failures = new ArrayList<>();
	for(int number = 0; number < classes.length; number++) {
		String name = ACS_PersistenceManager.readString(dis);
		classes[number] = find(name, loader);
		if(classes[number] == null) { failures.add("cannot find " + name); continue; }
		try { loadFromOf(classes[number]); }
		catch(IOException e) { failures.add(e.getMessage()); }
	}
	if(!failures.isEmpty()) { throw new IOException(String.join("; ", failures)); }
	return classes;
}

/**
 * The classes one store named, numbered in the order met, one number per name; a folder keeps one copy
 * of each class, the first met.
 * */
static final class ClassTable {
	final ArrayList<Class<?>> classes = new ArrayList<>();
	final Object2IntOpenHashMap<String> numbers = new Object2IntOpenHashMap<>();
	/** What the store left out, each naming where. */
	final ArrayList<String> warnings = new ArrayList<>();
	/** Where the store is writing - "arity 2" - for the warnings. */
	String place;
	/** The root of the database the stored core belongs to, null for a core that has none: a file under it is stored relative to it, so the file follows a copy or a migration. */
	Path root;

	ClassTable() { numbers.defaultReturnValue(-1); }

	/** Whether the part is stored: not when there is none, nor, with a warning, when its class has no static {@code loadFrom}. */
	boolean keeps(Object part) {
		if(part == null) { return false; }
		try { loadFromOf(part.getClass()); }
		catch(IOException e) {
			warnings.add(place + ": left out a " + part.getClass().getName() + ", which declares no public static loadFrom");
			return false;
		}
		return true;
	}

	int numberOf(Class<?> cls) {
		int number = numbers.getInt(cls.getName());
		if(number < 0) {
			number = classes.size();
			classes.add(cls);
			numbers.put(cls.getName(), number);
		}
		return number;
	}
}

/** What a store writes each file through, numbering classes across the whole folder. */
static final class Output extends DataOutputStream implements ClassNumbering, InFolder {
	final ClassTable table;
	final Path folder;

	Output(OutputStream out, ClassTable table, Path folder) {
		super(out);
		this.table = table;
		this.folder = folder;
	}

	@Override
	public int numberOf(Object part) { return table.keeps(part) ? table.numberOf(part.getClass()) : -1; }

	@Override
	public Path folder() { return folder; }

	@Override
	public Path root() { return table.root; }
}

/** What a load reads each file through, turning a class number back into a part. */
static final class Input extends DataInputStream implements PartBuilding, InFolder {
	final Class<?>[] classes;
	final Path folder;

	Input(InputStream in, Class<?>[] classes, Path folder) {
		super(in);
		this.classes = classes;
		this.folder = folder;
	}

	@Override
	public Path folder() { return folder; }

	@Override
	public <T> T build(int number, Class<T> type, ACS_Core core, ACS_Journal journal) throws IOException {
		if(number < 0 || number >= classes.length) { throw new IOException("class " + number + ", the indexer names " + classes.length); }
		return ACSE_PersistenceManagerBasic.build(classes[number], type, core, journal, this);
	}
}


interface Writing { boolean to(DataOutputStream dos) throws IOException; }
interface Reading<T> { T from(DataInputStream dis) throws IOException; }

/** Writes the file through the store's table and forces it to the disk before answering what the writer answered. */
static boolean write(Path file, ClassTable table, Writing writing) throws IOException {
	try(FileOutputStream out = new FileOutputStream(file.toFile()); Output dos = new Output(new BufferedOutputStream(out), table, file.toAbsolutePath().getParent())) {
		boolean written = writing.to(dos);
		dos.flush();
		out.getFD().sync();
		return written;
	}
}

/** Reads the file, refusing one that ends early or has bytes left over: either way, what reads it does not match what wrote it. */
static <T> T read(Path file, Class<?>[] classes, Reading<T> reading) throws IOException {
	try(Input dis = new Input(new BufferedInputStream(Files.newInputStream(file)), classes, file.toAbsolutePath().getParent())) {
		T read;
		try { read = reading.from(dis); }
		catch(EOFException e) { throw (EOFException) new EOFException(file + " ends before its reader is done").initCause(e); }
		if(dis.read() != -1) { throw new IOException(file + " holds bytes its reader left unread"); }
		return read;
	}
}

/** Makes a folder's new entries durable. A platform that cannot open a folder at all, as Windows, cannot sync one either. */
static void syncDirectory(Path directory) throws IOException {
	FileChannel channel;
	try { channel = FileChannel.open(directory, StandardOpenOption.READ); }
	catch(IOException e) { return; }
	try(channel) { channel.force(true); }
}


/**
 * Writes the folder's jar: the roots - the core's class and its journal's - every class the table
 * names, and every class they reach from the same places - the engine, a plugin's folder or jar, an
 * earlier folder's jar - except the contracts, which the loading program supplies; the JDK and
 * libraries such as fastutil stay out too. The first copy met of a class is the one kept.
 * */
static void storeClasses(Path folder, ClassTable table, Class<?>... roots) throws IOException {
	ArrayDeque<Class<?>> pending = new ArrayDeque<>(table.classes);
	for(int i = roots.length - 1; i >= 0; i--) { pending.addFirst(roots[i]); }
	HashSet<String> origins = new HashSet<>();
	origins.add(originOf(ACS_Core.class));
	for(Class<?> cls : pending) { origins.add(originOf(cls)); }
	origins.remove(null);
	TreeMap<String, byte[]> entries = new TreeMap<>();
	HashSet<String> met = new HashSet<>();
	while(!pending.isEmpty()) {
		Class<?> cls = pending.poll();
		if(!met.add(cls.getName()) || isContract(cls)) { continue; }
		byte[] bytes = bytesOf(cls);
		entries.put(pathOf(cls.getName()), bytes);
		for(String name : referencedClasses(bytes)) {
			Class<?> reached = find(name, cls.getClassLoader());
			if(reached != null && reached.getClassLoader() != null && origins.contains(originOf(reached))) { pending.add(reached); }
		}
	}
	try(FileOutputStream out = new FileOutputStream(folder.resolve(CLASSES).toFile()); JarOutputStream jar = new JarOutputStream(new BufferedOutputStream(out))) {
		for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
			JarEntry jarEntry = new JarEntry(entry.getKey());
			jarEntry.setTimeLocal(ENTRY_TIME);
			jar.putNextEntry(jarEntry);
			jar.write(entry.getValue());
			jar.closeEntry();
		}
		jar.finish();
		jar.flush();
		out.getFD().sync();
	}
}

/** The engine's public interfaces, nested ones included: its contracts, which the loading program supplies. */
static boolean isContract(Class<?> cls) {
	String contracts = originOf(ACS_Core.class);
	return cls.isInterface() && Modifier.isPublic(cls.getModifiers()) && contracts != null && contracts.equals(originOf(cls));
}


/** The class as the loader resolves it, not initialised; null when it cannot. */
static Class<?> find(String name, ClassLoader loader) {
	if(loader == null) { return null; }
	try { return Class.forName(name, false, loader); }
	catch(ClassNotFoundException | LinkageError e) { return null; }
}

/** Where the class was loaded from - a folder or a jar - or null when its loader does not say. */
static String originOf(Class<?> cls) {
	CodeSource source = cls.getProtectionDomain().getCodeSource();
	return source == null || source.getLocation() == null ? null : source.getLocation().toExternalForm();
}

/** The class file of the copy in use, from the loader that defined it; a class made at run time has none and cannot be stored. */
static byte[] bytesOf(Class<?> cls) throws IOException {
	ClassLoader loader = cls.getClassLoader();
	try(InputStream in = loader == null ? null : loader.getResourceAsStream(pathOf(cls.getName()))) {
		if(in == null) { throw new IOException(cls.getName() + " has no class file to store"); }
		return in.readAllBytes();
	}
}

static String pathOf(String className) { return className.replace('.', '/') + ".class"; }

/**
 * The classes a class file names: its constant pool's class entries, and every {@code L...;} in its
 * texts, which covers descriptors and generic signatures. The texts can over-reach - any string shaped
 * like a descriptor - which costs a lookup that finds nothing.
 * */
static Set<String> referencedClasses(byte[] classFile) throws IOException {
	DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
	if(in.readInt() != 0xCAFEBABE) { throw new IOException("not a class file"); }
	in.readInt();
	int count = in.readUnsignedShort();
	String[] texts = new String[count];
	IntArrayList classEntries = new IntArrayList();
	for(int i = 1; i < count; i++) {
		int tag = in.readUnsignedByte();
		switch(tag) {
			case 1 -> texts[i] = in.readUTF();
			case 7 -> classEntries.add(in.readUnsignedShort());
			case 8, 16, 19, 20 -> in.readUnsignedShort();
			case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
			case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
			// A long or a double takes two entries.
			case 5, 6 -> { in.readLong(); i++; }
			default -> throw new IOException("constant pool tag " + tag);
		}
	}
	HashSet<String> names = new HashSet<>();
	for(int i = 0; i < classEntries.size(); i++) {
		String name = texts[classEntries.getInt(i)];
		if(name != null && !name.startsWith("[")) { names.add(name.replace('/', '.')); }
	}
	for(String text : texts) {
		if(text == null) { continue; }
		Matcher matcher = DESCRIBED.matcher(text);
		while(matcher.find()) { names.add(matcher.group(1).replace('/', '.')); }
	}
	return names;
}

}

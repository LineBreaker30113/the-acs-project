package org.halim.acsgui.acsess;

import org.halim.acsgui.mhexagon.ACSGUI_Service.Classes;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Refusal;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;


/**
 * The classes of a database's current store, read from its files as ACSE's persistence manager writes
 * them, since no request of acsess lists them: {@code acse-persistence.data} at the folder's root names the
 * current generation, a folder {@code 000001}, {@code 000002}, ...; its {@code indexer.bin} numbers the
 * classes its files name, in the order met, then gives each arity slot's and structure slot's class by
 * number; its {@code classes.jar} carries them. This is the one place the program reads ACSE's formats
 * rather than acsess's answers, so a folder another manager wrote is refused, naming that manager, and a
 * format this program does not know is refused rather than misread.
 * <p>
 * The class content's source comes from the same jar, through Vineflower: the class and its nested classes
 * are decompiled, and the jar is given as a library so the names they use resolve.
 * */
public class StoredClasses {

static final int MAGIC = 0x41435345, FORMAT = 0;
static final String POINTER = "acse-persistence.data", INDEXER = "indexer.bin", CLASSES = "classes.jar";
static final String MANAGER = "org.halim.acse.ACSE_PersistenceManagerBasic";

static Classes read(Path folder) {
	try {
		Path pointer = folder.resolve(POINTER), store;
		try(DataInputStream dis = open(pointer)) {
			String manager = header(dis, pointer);
			if(!manager.equals(MANAGER)) { throw new Refusal(pointer + " belongs to " + manager + ", whose files this program does not read"); }
			int generation = dis.readInt();
			if(generation < 1) { throw new Refusal(pointer + " names generation " + generation); }
			store = folder.resolve(String.format("%06d", generation));
		}
		Path indexer = store.resolve(INDEXER);
		String core, journal;
		ArrayList<String> numbered = new ArrayList<>();
		int[] arities, structures;
		try(DataInputStream dis = open(indexer)) {
			core = header(dis, indexer);
			journal = string(dis);
			for(int number = 0, count = count(dis, indexer); number < count; number++) { numbered.add(string(dis)); }
			arities = slots(dis, indexer);
			structures = slots(dis, indexer);
		}
		ArrayList<String> files = new ArrayList<>();
		Path jar = store.resolve(CLASSES);
		if(Files.exists(jar)) {
			try(JarFile classes = new JarFile(jar.toFile())) {
				for(JarEntry entry : Collections.list(classes.entries())) {
					String name = entry.getName();
					if(!entry.isDirectory() && name.endsWith(".class")) { files.add(name.substring(0, name.length() - 6).replace('/', '.')); }
				}
			}
			Collections.sort(files);
		}
		return new Classes(store, core, journal, List.copyOf(numbered), arities, structures, List.copyOf(files));
	} catch(IOException e) { throw new Refusal("cannot read the stored classes of " + folder + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())); }
}

static DataInputStream open(Path file) throws IOException { return new DataInputStream(new BufferedInputStream(Files.newInputStream(file))); }

/** "ACSE", the format, and the class that wrote the file. */
static String header(DataInputStream dis, Path file) throws IOException {
	if(dis.readInt() != MAGIC) { throw new Refusal(file + " is not an ACSE persistence file"); }
	int format = dis.readInt();
	if(format != FORMAT) { throw new Refusal(file + " has format " + format + ", this program reads " + FORMAT); }
	return string(dis);
}

/** A string as ACSE writes one: its UTF-8 bytes after their count, "[ACS-NULL!!]" for null. */
static String string(DataInputStream dis) throws IOException {
	int length = dis.readInt();
	if(length < 0) { throw new IOException("string length " + length); }
	byte[] bytes = dis.readNBytes(length);
	if(bytes.length < length) { throw new EOFException("a string of " + length + " bytes ends after " + bytes.length); }
	String string = new String(bytes, StandardCharsets.UTF_8);
	return string.equals("[ACS-NULL!!]") ? null : string;
}

static int count(DataInputStream dis, Path file) throws IOException {
	int count = dis.readInt();
	if(count < 0) { throw new IOException(file + " gives a count of " + count); }
	return count;
}

static int[] slots(DataInputStream dis, Path file) throws IOException {
	int[] slots = new int[count(dis, file)];
	for(int slot = 0; slot < slots.length; slot++) { slots[slot] = dis.readInt(); }
	return slots;
}

/** The class with its nested classes, decompiled; refused when the store's jar does not carry it. */
static String decompile(Path store, String className) {
	Path jar = store.resolve(CLASSES);
	String path = className.replace('.', '/');
	LinkedHashMap<String, byte[]> wanted = new LinkedHashMap<>();
	try(JarFile classes = new JarFile(jar.toFile())) {
		for(JarEntry entry : Collections.list(classes.entries())) {
			String name = entry.getName();
			if(entry.isDirectory() || !name.endsWith(".class")) { continue; }
			String base = name.substring(0, name.length() - 6);
			if(base.equals(path) || base.startsWith(path + "$")) {
				try(InputStream in = classes.getInputStream(entry)) { wanted.put(base, in.readAllBytes()); }
			}
		}
	} catch(IOException e) { throw new Refusal("cannot read " + jar + ": " + e.getMessage()); }
	if(!wanted.containsKey(path)) { throw new Refusal(jar + " carries no " + className); }
	StringBuilder text = new StringBuilder();
	Decompiler.builder().inputs(source(className, wanted, text)).libraries(jar.toFile()).output(NO_SAVER).build().decompile();
	if(text.isEmpty()) { throw new Refusal("Vineflower gave nothing back for " + className); }
	return text.toString();
}

/** The class files as Vineflower's input, the source it writes collected into {@code text}. */
static IContextSource source(String className, Map<String, byte[]> classes, StringBuilder text) {
	return new IContextSource() {
		@Override public String getName() { return className; }

		@Override public Entries getEntries() { return new Entries(classes.keySet().stream().map(Entry::atBase).toList(), List.of(), List.of()); }

		@Override
		public InputStream getInputStream(String resource) {
			String base = resource.endsWith(CLASS_SUFFIX) ? resource.substring(0, resource.length() - CLASS_SUFFIX.length()) : resource;
			byte[] bytes = classes.get(base);
			return bytes == null ? null : new ByteArrayInputStream(bytes);
		}

		@Override
		public IOutputSink createOutputSink(IResultSaver saver) {
			return new IOutputSink() {
				@Override public void begin() { }

				@Override
				public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
					synchronized(text) {
						if(!text.isEmpty()) { text.append('\n'); }
						text.append(content);
					}
				}

				@Override public void acceptDirectory(String directory) { }

				@Override public void acceptOther(String path) { }

				@Override public void close() { }
			};
		}
	};
}

/** Vineflower needs a saver; the source's own sink collects everything. */
static final IResultSaver NO_SAVER = new IResultSaver() {
	@Override public void saveFolder(String path) { }

	@Override public void copyFile(String source, String path, String entryName) { }

	@Override public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) { }

	@Override public void createArchive(String path, String archiveName, Manifest manifest) { }

	@Override public void saveDirEntry(String path, String archiveName, String entryName) { }

	@Override public void copyEntry(String source, String path, String archiveName, String entry) { }

	@Override public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }

	@Override public void closeArchive(String path, String archiveName) { }
};

}

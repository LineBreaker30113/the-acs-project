package org.halim.acses.acsed;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.database.ACS_Core;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Writes the jar a new folder's manager is built from: the manager's class and every class it reaches
 * from the same places, except the contracts, which the loading program supplies; the JDK and the
 * libraries stay out too. It is the walk of {@code ACSE_PersistenceManagerBasic.storeClasses}, repeated
 * here because ACSES, not ACSE, makes this jar. The two must keep the same rules - what a contract is,
 * which places count - or a folder's two kinds of jar would disagree about what the program supplies.
 * */
class ACSED_PersistenceJar {
/** One date on every entry, so the same classes make the same jar. */
static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);
/** An {@code L...;} in a descriptor or a signature, for the classes a class file names outside its class entries. */
static final Pattern DESCRIBED = Pattern.compile("L([\\p{L}\\p{N}_$]+(?:/[\\p{L}\\p{N}_$]+)+)[;<]");


static void write(Path jar, Class<?> root) throws IOException {
	ArrayDeque<Class<?>> pending = new ArrayDeque<>();
	pending.add(root);
	HashSet<String> origins = new HashSet<>();
	origins.add(originOf(ACS_Core.class));
	origins.add(originOf(root));
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
	try(FileOutputStream out = new FileOutputStream(jar.toFile()); JarOutputStream stream = new JarOutputStream(new BufferedOutputStream(out))) {
		for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
			JarEntry jarEntry = new JarEntry(entry.getKey());
			jarEntry.setTimeLocal(ENTRY_TIME);
			stream.putNextEntry(jarEntry);
			stream.write(entry.getValue());
			stream.closeEntry();
		}
		stream.finish();
		stream.flush();
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

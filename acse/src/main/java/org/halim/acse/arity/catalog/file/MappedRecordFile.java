package org.halim.acse.arity.catalog.file;

import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.LongChunkInt;
import org.halim.acse.arity.catalog.memory.ChunkedCatalogBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


/**
 * One file of fixed-width records, an identity's record at {@code identity * recordBytes}, mapped into
 * memory a chunk at a time - the storage under every catalog whose values sit in a file. A record is its
 * {@link #fields} side by side, so the same file holds one column or several; reading a value is a load
 * from the mapping, at memory speed where the page is resident and paged in by the system where it is not,
 * so a column can be larger than memory.
 * <p>
 * A chunk is {@link LongChunkInt#fullLength()} records and one mapping. The chunking splits an identity
 * as it does for the in-memory catalogs, and growth follows {@link ChunkedCatalogBase}: only the last
 * mapping is ever short, it grows geometrically, and every one below it covers its whole chunk. Growing a
 * mapping copies nothing - the values are in the file, and the larger mapping sees them - and what it adds
 * past the file's end reads as zero. The default is 2^20 records a mapping: a process gets about 65530
 * mappings on Linux, and at this length 2^31 identities take 2048 of them.
 * <p>
 * Every store copies the file into the store's folder, under {@value #FILES} at the file's own path - its
 * path relative to the database's root when it lies under that root - so each generation keeps the file as
 * it was. A load copies it back to the file's place - under the loaded database's root for a relative one,
 * so a file follows its database to a copy or a migration - and maps it: a read is a load from the mapping,
 * never a copy. The copy put back is a new file, so a core still mapping the
 * old one keeps mapping the old one, apart from the loaded core, and never reads past an end the load
 * moved. {@link #create} refuses a file that already holds data, so a new catalog never takes over a file
 * it did not make.
 * <p>
 * A file has one live instance in a program, which every catalog over it shares, so the rows of one
 * record grow, store and load one file together. The instances are kept by path, weakly, per class loader.
 * Values are little-endian, the order of the machines this runs on, so a read is one load.
 * <p>
 * Not multi thread safe, except finding the live instance.
 * */
public class MappedRecordFile {

/** Field types: the magnitude is the width in bytes, a negative type is floating. */
public static final byte BYTE = 1, SHORT = 2, INT = 4, LONG = 8, FLOAT = -4, DOUBLE = -8;
public static final LongChunkInt DEFAULT_CHUNKING = new LongChunkInt.LongChunkIntPowerOfTwo(20);
static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
static final byte[] ZEROS = new byte[1 << 13];
/** The folder of a store holding the copies, each at its file's own path. */
public static final String FILES = "files";
/** The live instances, by absolute path. */
static final HashMap<Path, WeakReference<MappedRecordFile>> OPEN = new HashMap<>();

/** Absolute and normalised, so one file has one key. */
public final Path file;
/** Each field's type, in record order. Not to be written. */
final byte[] fields;
final int[] offsets;
public final int recordBytes;
public final LongChunkInt chunking;
final FileChannel channel;
/** One mapping a chunk; only the last may be short. */
final ArrayList<MappedByteBuffer> chunks = new ArrayList<>();
/** The highest identity addressable, not a count. An empty file is -1. */
public long lastIndex = -1;
/** The store or the load that last copied this file, so the rows of one record copy it once. */
Object copiedBy;


/** Over the file as it is, or emptied first. */
MappedRecordFile(Path file, LongChunkInt chunking, byte[] fields, boolean empty) throws IOException {
	if(fields.length == 0) { throw new IllegalArgumentException("a record needs a field"); }
	this.fields = fields.clone();
	this.offsets = new int[fields.length];
	int bytes = 0;
	for(int field = 0; field < fields.length; field++) {
		offsets[field] = bytes;
		bytes += widthOf(fields[field]);
	}
	if((long) chunking.fullLength() * bytes > Integer.MAX_VALUE) {
		throw new IllegalArgumentException(chunking + " of " + bytes + "-byte records is past what one mapping holds");
	}
	this.file = file;
	this.recordBytes = bytes;
	this.chunking = chunking;
	if(file.getParent() != null) { Files.createDirectories(file.getParent()); }
	this.channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
	if(empty) { channel.truncate(0); }
}

/** For a new catalog: refuses a file this program already maps, and one that holds data. */
public static synchronized MappedRecordFile create(Path file, LongChunkInt chunking, byte... fields) throws IOException {
	Path key = file.toAbsolutePath().normalize();
	if(live(key) != null) { throw new FileAlreadyExistsException(key.toString(), null, "a catalog in this program already uses it"); }
	if(Files.exists(key) && Files.size(key) > 0) { throw new FileAlreadyExistsException(key.toString(), null, "it holds data no catalog here made"); }
	return opened(new MappedRecordFile(key, chunking, fields, true));
}

/**
 * For a load: puts the store's copy at the file's place and opens it, or gives back the instance an
 * earlier row of the same load opened, which must have the same layout. Another instance live over the
 * file stops being the live one, and keeps mapping the file it had.
 * */
static synchronized MappedRecordFile loadCopy(Path file, LongChunkInt chunking, byte[] fields, Path copy, Object load) throws IOException {
	Path key = file.toAbsolutePath().normalize();
	MappedRecordFile live = live(key);
	if(live != null && live.copiedBy == load) {
		if(!Arrays.equals(live.fields, fields) || live.chunking.fullLength() != chunking.fullLength()) {
			throw new IOException(key + " is open with another layout");
		}
		return live;
	}
	if(key.getParent() != null) { Files.createDirectories(key.getParent()); }
	Files.copy(copy, key, StandardCopyOption.REPLACE_EXISTING);
	MappedRecordFile loaded = opened(new MappedRecordFile(key, chunking, fields, false));
	loaded.copiedBy = load;
	return loaded;
}

static MappedRecordFile live(Path key) {
	WeakReference<MappedRecordFile> reference = OPEN.get(key);
	MappedRecordFile live = reference == null ? null : reference.get();
	if(reference != null && live == null) { OPEN.remove(key); }
	return live;
}

static MappedRecordFile opened(MappedRecordFile opened) {
	OPEN.put(opened.file, new WeakReference<>(opened));
	return opened;
}

/**
 * Where a store in {@code folder} keeps its copy of {@code file}: under {@value #FILES}, at the file's path
 * relative to its database's root when it is stored so, else at its absolute path less the file system's root.
 * */
public static Path copyOf(Path folder, Path file) {
	if(!file.isAbsolute()) { return folder.resolve(FILES).resolve(file.toString()); }
	Path absolute = file.toAbsolutePath().normalize();
	Path root = absolute.getRoot();
	return folder.resolve(FILES).resolve((root == null ? absolute : root.relativize(absolute)).toString());
}

/** Copies the file and forces the copy to the disk, as the store forces its own files. */
/**
 * The path a store writes for {@code file}: relative to the root of the database being stored when the file
 * lies under it, so the file follows its database to a copy or a migration; else the file's own, absolute.
 * */
static Path storedPathOf(DataOutputStream dos, Path file) {
	Path absolute = file.toAbsolutePath().normalize();
	Path root = ACS_PersistenceManager.rootOf(dos);
	return root != null && absolute.startsWith(root) ? root.relativize(absolute) : absolute;
}

/** Where a load puts a stored file: a relative path under the loaded database's root, the folder the stored folder sits in; an absolute one where it says. */
static Path loadedPathOf(DataInputStream dis, Path stored) {
	return stored.isAbsolute() ? stored : ACS_PersistenceManager.folderOf(dis).toAbsolutePath().getParent().resolve(stored).normalize();
}

public static void copyDurably(Path from, Path to) throws IOException {
	if(to.getParent() != null) { Files.createDirectories(to.getParent()); }
	try(FileChannel in = FileChannel.open(from, StandardOpenOption.READ);
		FileChannel out = FileChannel.open(to, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
		for(long at = 0, size = in.size(); at < size; ) { at += in.transferTo(at, size - at, out); }
		out.force(true);
	}
}

public static int widthOf(byte type) {
	return switch(type) {
		case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> Math.abs(type);
		default -> throw new IllegalArgumentException("no field type " + type);
	};
}


public int fieldCount() { return fields.length; }
public byte typeOf(int field) { return fields[field]; }
public int offsetOf(int field) { return offsets[field]; }

/** The mapping holding the identity's record. */
public MappedByteBuffer chunkOf(long identity) { return chunks.get(chunking.chunkOf(identity)); }

/** Where the identity's record starts inside its mapping. */
public int positionOf(long identity) { return chunking.offsetOf(identity) * recordBytes; }

/** How many records a mapping covers, which for the last one may be past the reach. */
public int recordsIn(ByteBuffer mapping) { return mapping.capacity() / recordBytes; }


/** Makes {@code capacity} addressable, growing the last mapping and adding chunks as needed. True when the reach moved; never lowers it. */
public boolean ensureCapacity(long capacity) {
	if(capacity <= lastIndex) { return false; }
	int lastChunk = chunking.chunkOf(capacity);
	if(!chunks.isEmpty()) {
		int at = chunks.size() - 1;
		int have = recordsIn(chunks.get(at));
		int want = chunking.lengthOf(at, capacity);
		if(have < want) { chunks.set(at, map(at, grown(have, want))); }
	}
	while(chunks.size() <= lastChunk) { chunks.add(map(chunks.size(), grown(0, chunking.lengthOf(chunks.size(), capacity)))); }
	lastIndex = capacity;
	return true;
}

/**
 * Lowers the reach to {@code capacity} and gives back what sat above it: the mappings past the new last
 * one, and the file past where the last one ends. The last mapping is shrunk only when it is more than
 * twice what is still needed, as {@link ChunkedCatalogBase#truncate} does, and what it still covers
 * above the reach is zeroed, so a later growth reads zero there. Never raises the reach.
 * */
public void truncate(long capacity) {
	if(capacity >= lastIndex) { return; }
	int keep = chunking.chunkCount(capacity);
	while(chunks.size() > keep) { chunks.removeLast(); }
	long end = 0;
	if(!chunks.isEmpty()) {
		int at = chunks.size() - 1;
		int want = chunking.lengthOf(at, capacity);
		if(recordsIn(chunks.get(at)) / 2 > want) { chunks.set(at, map(at, grown(want, want))); }
		MappedByteBuffer tail = chunks.get(at);
		zero(tail, want * recordBytes, tail.capacity());
		end = startOf(at) + tail.capacity();
	}
	try { channel.truncate(end); }
	catch(IOException e) { throw new UncheckedIOException(e); }
	lastIndex = Math.max(capacity, -1);
}

/** Sets the reach to exactly {@code capacity}, growing or truncating. */
public void reach(long capacity) {
	if(capacity < lastIndex) { truncate(capacity); }
	else { ensureCapacity(capacity); }
}

/** Geometric, as {@link ChunkedCatalogBase#grown}: half again over the request, capped at a full chunk. */
int grown(int current, int want) {
	return Math.min(chunking.fullLength(), Math.max(want, current + (current >> 1) + 8));
}

long startOf(int chunk) { return (long) chunk * chunking.fullLength() * recordBytes; }

/** Maps {@code records} records of the chunk, first lengthening the file if it ends inside them. */
MappedByteBuffer map(int chunk, int records) {
	long start = startOf(chunk), size = (long) records * recordBytes;
	try {
		if(channel.size() < start + size) { channel.write(ByteBuffer.wrap(new byte[1]), start + size - 1); }
		MappedByteBuffer mapping = channel.map(FileChannel.MapMode.READ_WRITE, start, size);
		mapping.order(ORDER);
		return mapping;
	} catch(IOException e) { throw new UncheckedIOException(e); }
}

static void zero(ByteBuffer mapping, int from, int to) {
	for(int at = from; at < to; at += ZEROS.length) { mapping.put(at, ZEROS, 0, Math.min(ZEROS.length, to - at)); }
}


/**
 * The file, the fields and the chunk length, which is enough for {@link #openLayout} to open the file
 * again, and the file copied into the store's folder - once per store, however many rows of a record
 * write their layout.
 * */
public void writeLayout(DataOutputStream dos) throws IOException {
	Path stored = storedPathOf(dos, file);
	ACS_PersistenceManager.writeString(dos, stored.toString());
	dos.writeInt(fields.length);
	dos.write(fields);
	dos.writeInt(chunking.fullLength());
	if(copiedBy != dos) {
		copyDurably(file, copyOf(ACS_PersistenceManager.folderOf(dos), stored));
		copiedBy = dos;
	}
}

/**
 * Reads what {@link #writeLayout} wrote and opens that file for a load, from the copy in the loaded folder.
 * Every chunking splits uniformly - each chunk below the last is full - so the length is the whole of it.
 * */
public static MappedRecordFile openLayout(DataInputStream dis) throws IOException {
	Path stored = Path.of(ACS_PersistenceManager.readString(dis));
	byte[] fields = new byte[dis.readInt()];
	dis.readFully(fields);
	int length = dis.readInt();
	LongChunkInt chunking = Integer.bitCount(length) == 1 && length > 1 ? new LongChunkInt.LongChunkIntPowerOfTwo(Integer.numberOfTrailingZeros(length)) : new LongChunkInt.LongChunkIntUniform(length);
	return loadCopy(loadedPathOf(dis, stored), chunking, fields, copyOf(ACS_PersistenceManager.folderOf(dis), stored), dis);
}

/** Refuses a layout other than the one a catalog class stores. */
public void requireFields(byte... expected) throws IOException {
	if(!Arrays.equals(fields, expected)) { throw new IOException(file + " has fields " + Arrays.toString(fields) + ", expected " + Arrays.toString(expected)); }
}


}

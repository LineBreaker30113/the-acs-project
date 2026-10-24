package org.halim.acse.arity.catalog.file;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.halim.acs.ACS_Core;
import org.halim.acs.ACS_Journal;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.StringValueCatalog;
import org.halim.acse.arity.catalog.memory.IntegerValueCatalogArraysInt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * A string catalog on disk, in pages of {@value #PAGE} bytes inside one file - page {@code n} at byte
 * {@code n * PAGE} - each holding up to {@link #entriesPerPage} strings, while memory holds only which
 * page each identity's string is in, an int an identity. Reading a string is one page read, one block of
 * the usual file system, and however many pages there are they make one file, so the size is bounded by
 * the disk rather than by how many files the file system can count.
 * <p>
 * A page is its entry count as an unsigned short, then each entry: the identity as a long and the string
 * in Java's modified UTF-8, as {@link DataOutputStream#writeUTF} writes it with its length in front - exact
 * for any string, and a byte a character for Latin text. A string may take at most {@value #LARGEST} bytes
 * encoded, a page less its count and one entry's identity and length; a longer one is refused with an
 * {@link ArithmeticException}, as a value too wide for its column is. "" takes no entry, so a string never
 * written and "" read the same.
 * <p>
 * A write rewrites the identity's page without its old entry, then puts the new one back into that page
 * when it still fits, else into the last page, else into a new last page. Space a string leaves in an
 * earlier page is not reused by another identity yet.
 * <p>
 * Every store copies the file into the store's folder, as {@link MappedRecordFile} does, and the page
 * index goes into the arity's file; a load puts the copy back at the file's place as a new file, so a
 * catalog still open over the old one keeps it. One catalog a file. Not multi thread safe.
 * */
public class StringValueCatalogFilePages implements StringValueCatalog {

/** One page: one block of the usual file system, and one page of the system's cache. */
public static final int PAGE = 4096;
/** The most a string may take encoded: a page less its entry count, one entry's identity and the string's length. */
public static final int LARGEST = PAGE - 2 - 8 - 2;
/** The most entries a page can hold at all, each at least an identity and a length. */
public static final int MOST_ENTRIES = (PAGE - 2) / (8 + 2);

/** Absolute and normalised. */
public final Path file;
public final int entriesPerPage;
/** Each identity's page plus one, so 0 is no page: the identity's string is "". */
final IntegerValueCatalogArraysInt pages;
final FileChannel channel;
/** How many pages the file holds. */
int pageCount;
/** One page's bytes, read into and written from. */
final ByteBuffer buffer = ByteBuffer.allocate(PAGE);


/** A new catalog over {@code file}, created empty; refuses a file that already holds data. */
public StringValueCatalogFilePages(Path file, int entriesPerPage) throws IOException {
	this(file.toAbsolutePath().normalize(), entriesPerPage, new IntegerValueCatalogArraysInt(), true);
}

StringValueCatalogFilePages(Path file, int entriesPerPage, IntegerValueCatalogArraysInt pages, boolean create) throws IOException {
	if(entriesPerPage < 1 || entriesPerPage > MOST_ENTRIES) { throw new IllegalArgumentException(entriesPerPage + " entries a page; a page holds 1 to " + MOST_ENTRIES); }
	if(create && Files.exists(file) && Files.size(file) > 0) { throw new FileAlreadyExistsException(file.toString(), null, "it holds data no catalog here made"); }
	this.file = file;
	this.entriesPerPage = entriesPerPage;
	this.pages = pages;
	if(file.getParent() != null) { Files.createDirectories(file.getParent()); }
	FileChannel opened = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
	long size = opened.size();
	if(size % PAGE != 0) {
		opened.close();
		throw new IOException(file + " is " + size + " bytes, not whole pages of " + PAGE);
	}
	this.channel = opened;
	this.pageCount = Math.toIntExact(size / PAGE);
}


/** The page index in memory; the strings are on disk. */
@Override
public double getEstimatedBytesPerIdentity() { return 4; }

@Override
public String getData(long identity) {
	int page = pages.getIntData(identity) - 1;
	if(page < 0) { return ""; }
	Page read = read(page);
	int at = read.find(identity);
	if(at < 0) { throw new IllegalStateException(file + ": page " + page + " holds no string for identity " + identity); }
	return read.string(at);
}

/** The contract's write, which leaves growth to the arity: the identity must already be addressable. */
@Override
public void setStringFor(long identity, String value) {
	byte[] entry = value.isEmpty() ? null : encode(value);
	int page = pages.getIntData(identity) - 1, target = -1;
	if(page >= 0) {
		Page current = read(page);
		current.remove(identity);
		if(entry != null && current.fits(entry, entriesPerPage)) {
			current.add(identity, entry);
			target = page;
		}
		write(page, current);
	}
	if(entry != null && target < 0) {
		int last = pageCount - 1;
		Page tail = last >= 0 && last != page ? read(last) : null;
		if(tail != null && tail.fits(entry, entriesPerPage)) { target = last; }
		else {
			target = pageCount;
			tail = new Page();
		}
		tail.add(identity, entry);
		write(target, tail);
		if(target == pageCount) { pageCount++; }
	}
	pages.setDataRaw(identity, target + 1);
}

/** The string as an entry holds it, its modified UTF-8 with the length in front; refused past {@value #LARGEST} bytes. */
static byte[] encode(String value) {
	// Modified UTF-8 spends at least a byte on every char, so a string with more chars than that is refused unencoded.
	if(value.length() > LARGEST) { throw tooLong(value.length() + " chars"); }
	ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length() + 2);
	try { new DataOutputStream(bytes).writeUTF(value); }
	catch(IOException e) { throw new UncheckedIOException(e); }
	byte[] entry = bytes.toByteArray();
	if(entry.length - 2 > LARGEST) { throw tooLong((entry.length - 2) + " bytes"); }
	return entry;
}

static ArithmeticException tooLong(String size) {
	return new ArithmeticException("a string of " + size + " does not fit a page; the most is " + LARGEST + " bytes encoded");
}


/** Grows the page index, or lowers the reach to the identity when it is already addressable, as the chunked catalogs do. */
@Override
public boolean ensureCapacity(long identity) {
	if(identity <= pages.lastIndex) { truncate(identity); return false; }
	return pages.ensureCapacity(identity);
}

/** Lowers the reach to {@code capacity}, first taking every string above it out of its page, so an identity minted again reads ""; never raises it. */
@Override
public void truncate(long capacity) {
	if(capacity >= pages.lastIndex) { return; }
	for(long identity = Math.max(capacity, -1) + 1; identity <= pages.lastIndex; identity++) {
		if(pages.getIntData(identity) != 0) { setStringFor(identity, ""); }
	}
	pages.truncate(capacity);
}


/** The page as it is in the file. */
Page read(int page) {
	buffer.clear();
	try {
		while(buffer.hasRemaining()) {
			if(channel.read(buffer, (long) page * PAGE + buffer.position()) < 0) { throw new EOFException(file + " ends inside page " + page); }
		}
		return Page.decode(buffer.array());
	} catch(IOException e) { throw new UncheckedIOException(e); }
}

/** Writes the whole page, zeros after its entries. */
void write(int page, Page content) {
	buffer.clear();
	content.encodeInto(buffer);
	Arrays.fill(buffer.array(), buffer.position(), PAGE, (byte) 0);
	buffer.position(0);
	try {
		while(buffer.hasRemaining()) { channel.write(buffer, (long) page * PAGE + buffer.position()); }
	} catch(IOException e) { throw new UncheckedIOException(e); }
}


/** The file, the entries a page, the page index, and the file copied into the store's folder. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	Path stored = MappedRecordFile.storedPathOf(dos, file);
	ACS_PersistenceManager.writeString(dos, stored.toString());
	dos.writeInt(entriesPerPage);
	pages.saveTo(dos);
	MappedRecordFile.copyDurably(file, MappedRecordFile.copyOf(ACS_PersistenceManager.folderOf(dos), stored));
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, the store's copy put back at the file's place. */
public static StringValueCatalogFilePages loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	Path stored = Path.of(ACS_PersistenceManager.readString(dis));
	Path file = MappedRecordFile.loadedPathOf(dis, stored).toAbsolutePath().normalize();
	int entriesPerPage = dis.readInt();
	if(entriesPerPage < 1 || entriesPerPage > MOST_ENTRIES) { throw new IOException(file + " was stored with " + entriesPerPage + " entries a page"); }
	IntegerValueCatalogArraysInt pages = IntegerValueCatalogArraysInt.loadFrom(core, journal, dis);
	Path copy = MappedRecordFile.copyOf(ACS_PersistenceManager.folderOf(dis), stored);
	if(file.getParent() != null) { Files.createDirectories(file.getParent()); }
	Files.copy(copy, file, StandardCopyOption.REPLACE_EXISTING);
	return new StringValueCatalogFilePages(file, entriesPerPage, pages, false);
}


/** A page in memory: its entries in the file's order, each an identity and its string as {@link #encode} gives it. */
static final class Page {
	final LongArrayList identities = new LongArrayList();
	final ArrayList<byte[]> entries = new ArrayList<>();
	/** What the page takes in the file: its count, then each entry's identity and encoded string. */
	int bytes = 2;

	static Page decode(byte[] page) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(page));
		Page decoded = new Page();
		for(int i = 0, count = in.readUnsignedShort(); i < count; i++) {
			long identity = in.readLong();
			int length = in.readUnsignedShort();
			byte[] entry = new byte[2 + length];
			entry[0] = (byte) (length >>> 8);
			entry[1] = (byte) length;
			in.readFully(entry, 2, length);
			decoded.add(identity, entry);
		}
		return decoded;
	}

	int find(long identity) { return identities.indexOf(identity); }

	String string(int at) {
		try { return new DataInputStream(new ByteArrayInputStream(entries.get(at))).readUTF(); }
		catch(IOException e) { throw new UncheckedIOException(e); }
	}

	boolean fits(byte[] entry, int most) { return identities.size() < most && bytes + 8 + entry.length <= PAGE; }

	void add(long identity, byte[] entry) {
		identities.add(identity);
		entries.add(entry);
		bytes += 8 + entry.length;
	}

	void remove(long identity) {
		int at = find(identity);
		if(at < 0) { return; }
		identities.removeLong(at);
		bytes -= 8 + entries.remove(at).length;
	}

	void encodeInto(ByteBuffer page) {
		page.putShort((short) identities.size());
		for(int i = 0; i < identities.size(); i++) {
			page.putLong(identities.getLong(i));
			page.put(entries.get(i));
		}
	}
}

}

package org.halim.acse.arity.catalog.memory;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.arity.catalog.BooleanValueCatalog;
import org.halim.acs.arity.catalog.LongChunkInt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * The chunked boolean catalog: one bit an identity, 64 to a long, where a boolean array spends a whole
 * byte on each - an eighth of the memory, and of the stored file.
 * <p>
 * Its chunk list holds words rather than identities, so it is a {@link ChunkedCatalogBase} of its own,
 * held rather than extended: the list's reach is counted in words and this catalog's in identities, and
 * growth and truncation keep the two in step. Truncating inside a word also clears the bits above the
 * new reach in it, so an identity minted again reads false, as a new slot does. The default chunk is
 * 2^16 words, the 2^22 identities a chunk of the other chunked catalogs.
 * */
public class BooleanValueCatalogBits implements BooleanValueCatalog {

/** Identity {@code i} is bit {@code i & 63} of word {@code i >>> 6}. */
final Words words;
/** The highest identity addressable, not a count. An empty catalog is -1. */
public long lastIndex = -1;

public BooleanValueCatalogBits() {
	this(new LongChunkInt.LongChunkIntPowerOfTwo(16));
}
public BooleanValueCatalogBits(LongChunkInt chunking) {
	this.words = new Words(chunking);
}


@Override
public double getEstimatedBytesPerIdentity() { return 0.125; }

@Override
public boolean getData(long identity) {
	return (wordOf(identity) >>> (identity & 63) & 1) != 0;
}

/** The contract's write, which leaves growth to the arity: the bit must already be addressable. */
@Override
public void setBooleanFor(long identity, boolean value) {
	long word = identity >>> 6;
	long[] chunk = words.data.get(words.chunking.chunkOf(word));
	int at = words.chunking.offsetOf(word);
	long bit = 1L << (identity & 63);
	chunk[at] = value ? chunk[at] | bit : chunk[at] & ~bit;
}

long wordOf(long identity) {
	long word = identity >>> 6;
	return words.data.get(words.chunking.chunkOf(word))[words.chunking.offsetOf(word)];
}


/** Grows the words to the identity's word, or lowers the reach to the identity when it is already addressable, as the chunked catalogs do. True when the reach rose. */
@Override
public boolean ensureCapacity(long identity) {
	if(identity <= lastIndex) { truncate(identity); return false; }
	words.ensureCapacity(identity >>> 6);
	lastIndex = identity;
	return true;
}

/** Lowers the reach to {@code capacity}, clearing the bits above it in its word; never raises it. */
@Override
public void truncate(long capacity) {
	if(capacity >= lastIndex) { return; }
	long reach = Math.max(capacity, -1);
	words.truncate(reach < 0 ? -1 : reach >>> 6);
	if(reach >= 0) {
		long word = reach >>> 6;
		long[] chunk = words.data.get(words.chunking.chunkOf(word));
		chunk[words.chunking.offsetOf(word)] &= -1L >>> (63 - (reach & 63));
	}
	lastIndex = reach;
}


/** The words as their chunk list stores them, then the reach in identities. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	words.saveTo(dos);
	dos.writeLong(lastIndex);
	return true;
}

/** Rebuilds what {@link #saveTo} wrote, refusing a reach the words do not match. */
public static BooleanValueCatalogBits loadFrom(ACS_Core core, ACS_Journal journal, DataInputStream dis) throws IOException {
	BooleanValueCatalogBits catalog = new BooleanValueCatalogBits(ChunkedCatalogBase.readChunking(dis));
	catalog.words.readValues(dis);
	catalog.lastIndex = dis.readLong();
	if(catalog.words.lastIndex != (catalog.lastIndex < 0 ? -1 : catalog.lastIndex >>> 6)) {
		throw new IOException("a reach of " + catalog.lastIndex + " identities over " + (catalog.words.lastIndex + 1) + " words");
	}
	return catalog;
}


/** The range a word at a time, so the chunk split is resolved once per 64 identities rather than once per identity. */
@Override
public void copyBooleansInto(long from, long to, boolean[] dest, int offset) {
	for(long identity = from; identity < to; ) {
		long word = wordOf(identity);
		do { dest[offset++] = (word >>> (identity & 63) & 1) != 0; }
		while(++identity < to && (identity & 63) != 0);
	}
}


/** The chunk list of words under the bits. */
static final class Words extends ChunkedCatalogBase<long[]> {
	Words(LongChunkInt chunking) { super(chunking); }

	@Override
	protected long[] allocate(int length) { return new long[length]; }
	@Override
	protected int lengthOf(long[] chunk) { return chunk.length; }
	@Override
	protected long[] copyOf(long[] chunk, int length) { return Arrays.copyOf(chunk, length); }
	@Override
	protected void clear(long[] chunk, int from, int to) { Arrays.fill(chunk, from, to, 0L); }
	@Override
	protected void write(DataOutputStream dos, long[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { dos.writeLong(chunk[i]); } }
	@Override
	protected void read(DataInputStream dis, long[] chunk, int length) throws IOException { for(int i = 0; i < length; i++) { chunk[i] = dis.readLong(); } }
}

}

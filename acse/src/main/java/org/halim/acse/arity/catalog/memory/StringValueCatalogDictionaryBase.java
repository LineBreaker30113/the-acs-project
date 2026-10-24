package org.halim.acse.arity.catalog.memory;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.LongChunkInt;
import org.halim.acs.arity.catalog.StringValueCatalog.CodedStringValueCatalog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;


/**
 * The chunk list of codes under each dictionary string catalog, and the dictionary itself: the distinct
 * strings written so far, a code each, code 0 being "". A write looks its string up, adding it when it
 * is new, and stores the code. The arity journals the code's {@code old ^ new}, so undo and redo set
 * codes and never touch the dictionary, and a string stays in it once added, as capacity stays after an
 * undo.
 * <p>
 * A code is as wide as the subclass's array - one, two or four bytes an identity - and read unsigned, so
 * the dictionary holds up to 256, 65536 or 2^31 - 1 strings; one more is refused with an
 * {@link ArithmeticException}, as a value too wide for its column is, which is the signal that the
 * catalog wanted was a wider one.
 * */
public abstract class StringValueCatalogDictionaryBase<A> extends ChunkedCatalogBase<A> implements CodedStringValueCatalog {

/** By code. */
final ArrayList<String> strings = new ArrayList<>();
/** The reverse; -1 for a string not in the dictionary. */
final Object2IntOpenHashMap<String> codes = new Object2IntOpenHashMap<>();

protected StringValueCatalogDictionaryBase(LongChunkInt chunking) {
	super(chunking);
	codes.defaultReturnValue(-1);
	add("");
}


/** How many strings the codes tell apart. */
protected abstract int capacity();

/** Stores a code the dictionary holds; the reach is the arity's to check. */
protected abstract void store(long identity, int code);


@Override
public String getData(long identity) { return strings.get(getCode(identity)); }

/** The contract's write, which leaves growth to the arity: the slot must already be addressable. */
@Override
public void setStringFor(long identity, String value) { store(identity, codeOf(value)); }

@Override
public void setCode(long identity, int code) {
	Objects.checkIndex(code, strings.size());
	store(identity, code);
}

/** The string's code, the string added to the dictionary when it is new. */
public int codeOf(String value) {
	int code = codes.getInt(Objects.requireNonNull(value, "a string column holds \"\", not null"));
	if(code >= 0) { return code; }
	if(strings.size() >= capacity()) { throw new ArithmeticException(getClass().getSimpleName() + " tells " + capacity() + " strings apart, and all are taken"); }
	return add(value);
}

/** How many distinct strings the dictionary holds, "" among them. */
public int dictionarySize() { return strings.size(); }

int add(String value) {
	int code = strings.size();
	strings.add(value);
	codes.put(value, code);
	return code;
}


/** The codes as the chunk list stores them, then the dictionary in code order. */
@Override
public boolean saveTo(DataOutputStream dos) throws IOException {
	super.saveTo(dos);
	dos.writeInt(strings.size());
	for(String string : strings) { ACS_PersistenceManager.writeString(dos, string); }
	return true;
}

/** The rest of what {@link #saveTo} wrote, after the codes: the dictionary, refusing one that does not start with "" or names a string twice. */
protected void readDictionary(DataInputStream dis) throws IOException {
	int count = dis.readInt();
	if(count < 1 || count > capacity()) { throw new IOException(count + " strings for a dictionary of 1 to " + capacity()); }
	strings.clear();
	codes.clear();
	for(int code = 0; code < count; code++) {
		String string = ACS_PersistenceManager.readString(dis);
		if(string == null || (code == 0) != string.isEmpty() || codes.containsKey(string)) {
			throw new IOException("dictionary entry " + code + " is " + (string == null ? "null" : "\"" + string + "\"") + ", which a dictionary does not hold there");
		}
		add(string);
	}
}

}

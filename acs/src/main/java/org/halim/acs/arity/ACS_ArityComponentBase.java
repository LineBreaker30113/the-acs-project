package org.halim.acs.arity;

import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acs.arity.sets.IdentitiesSet;

import java.io.DataOutputStream;
import java.io.IOException;


/**
 * An arity seen as one object: the identities that exist, and every column hanging off them. A cell
 * is addressed by {@code (catalog, identity)} - the catalog is the column, the identity is the row -
 * and nothing here says whether those columns are separate objects or fields of one class.
 * <p>
 * That silence is the reason this interface exists beside {@link ACS_ArityCatalogRowBase}. A column is a
 * natural unit when it is genuinely its own storage - a chunked width, a value computed from the
 * identity - and dead weight when it is not: a client with three int columns should be able to write
 * one class over three flat arrays and be done, without minting a row object and a slot table to
 * hold what a field would have held. {@link ACS_ArityCatalogRowBase} is the other side of that trade,
 * for arities whose columns already exist as objects.
 * <p>
 * The six accessors are widened views over one stored value, not six stored values, and every one
 * is a default that throws. That is right for the case this interface is for - a hand-written table
 * is usually one type throughout, so "this holds longs" is a fact about the whole object - and a
 * table that genuinely mixes types overrides the accessors it can answer and dispatches on
 * {@code catalog} itself.
 * <p>
 * Two failures live here and they are not the same failure:
 * <ul>
 *   <li>{@link UnsupportedOperationException} - this catalog cannot answer that type at all. The
 *       question was wrong, and no identity would have answered it.</li>
 *   <li>{@link ArithmeticException} - the catalog can answer that type, but this value does not
 *       survive the conversion. The question was fine; the value did not fit.</li>
 * </ul>
 * The engine narrows nothing silently - a wrapped identity or a rounded-away digit is
 * indistinguishable from a real one - so conversion is exact or it throws. {@link #getDoubleFor} on
 * an integral catalog is the case worth remembering: it is exact well past 2^53 for some values and
 * not for others, so the check is per value rather than per catalog.
 * <p>
 * Lifecycle is arity-wide rather than per column, and that is what holding the columns together
 * buys: an arity mints an identity once and every column follows in one call, with no chance of two
 * of them disagreeing about how far they reach.
 * */
public interface ACS_ArityComponentBase extends ACS_Actor {

/**
 * Which identities exist. A catalog is total - it answers for every identity, and a stored slot
 * never written reads as zero rather than as absent - so existence is this set's question and only
 * this set's. Zero is a value here, not a sentinel.
 * */
IdentitiesSet occupied();

/** Called by the core only; everyone else removes through {@link ACS_Core#delegateRemoval}, which also runs forget. */
void removeIf(int identity, String comment);
/** Called by the core only; everyone else captures through {@link ACS_Core#captureIdentity}. */
void registerIf(int identity, String comment);

/** How many columns, so a slot is checkable and a walk over all of them is writable. */
int catalogCount();

double getEstimatedBytesPerIdentity(int catalog);
int getPreferredType(int catalog);


/**
 * Makes {@code identity} addressable in every column, growing storage where it is not already. True
 * when anything had to grow.
 * <p>
 * Arity-wide on purpose. The identity is minted once and each column has to follow, and doing that
 * here rather than column by column is what keeps the columns from drifting apart - a table cannot
 * end up half-grown after a partial loop the caller wrote itself. Nothing in this pair touches a
 * value, so nothing in it boxes one.
 * */
boolean ensureCapacity(long identity);

/** Lowers the ceiling to {@code capacity} in every column and gives back what sat above it. */
void truncate(long capacity);

/**
 * Writes everything this arity holds - which identities exist, and every column - so that the class's
 * {@code public static loadFrom(ACS_Core, ACS_Journal, DataInputStream)} rebuilds the same arity,
 * outside the journal. Java cannot require that static method here, so {@link ACS_PersistenceManager}
 * looks it up. One file per arity; its columns go inside it. False when it cannot be stored, and the
 * whole store fails.
 * */
boolean saveTo(DataOutputStream dos) throws IOException;

/** Can use integers carefreely. */
public static final int PREFERRED_INTEGER = -1;
/** Cannot use integers carefreely. */
public static final int PREFERRED_LONGI = -2;

public static final int PREFERRED_FLOATING = 0;
public static final int PREFERRED_BOOL = 1;
public static final int PREFERRED_OBJECT = 2;
/** A string, which is an Object too: a string catalog answers {@link #getObjectFor} with the same value. */
public static final int PREFERRED_STRING = 3;

default int getIntFor(int catalog, long identity) { throw cannotAnswer(catalog, "int"); }
default long getLongFor(int catalog, long identity) { throw cannotAnswer(catalog, "long"); }
default double getDoubleFor(int catalog, long identity) { throw cannotAnswer(catalog, "double"); }
default boolean getBooleanFor(int catalog, long identity) { throw cannotAnswer(catalog, "boolean"); }
default Object getObjectFor(int catalog, long identity) { throw cannotAnswer(catalog, "Object"); }
default String getStringFor(int catalog, long identity) { throw cannotAnswer(catalog, "String"); }

/**
 * The writes, journaled: the value lands in {@code catalog} at {@code identity}, exact or refused like
 * a read, and only within the identities the arity already reaches - a write never grows a column,
 * since growth is the arity's and happens for every column at once.
 * <p>
 * The journal keeps the difference, {@code old ^ new} over the stored bits, rather than the two
 * values: one long whatever the width, exact for a double where {@code new - old} is not, and the
 * same step both undoes and redoes. A string changes length, so its difference is the two strings'
 * chars XORed - the shorter read as zeros past its end - and the change in length; a string column of
 * dictionary codes keeps its code's {@code old ^ new} instead. A table that takes no writes keeps these
 * defaults, which refuse.
 * */
default void setLongFor(int catalog, long identity, long value, String comment) { throw cannotAnswer(catalog, "a written long"); }
default void setDoubleFor(int catalog, long identity, double value, String comment) { throw cannotAnswer(catalog, "a written double"); }
default void setBooleanFor(int catalog, long identity, boolean value, String comment) { throw cannotAnswer(catalog, "a written boolean"); }
default void setStringFor(int catalog, long identity, String value, String comment) { throw cannotAnswer(catalog, "a written String"); }

/**
 * The column as an object, for the table that has one. A table storing its columns as fields does
 * not, and refuses - which is the honest answer rather than a wrapper minted per call, since the
 * whole point of that table is that no such object exists.
 * */
default ACS_ArityCatalogRowBase getCatalogRow(int catalog) { throw cannotAnswer(catalog, "CatalogRow"); }


/**
 * The range views: identities {@code [from, to)} of one column, copied into {@code dest} from
 * {@code offset}. Present so a loop over identities can cost one dispatch rather than one per
 * element, and defaulted so nothing has to supply them before it works.
 * <p>
 * The default bodies buy nothing by themselves - they are the per-element loop, written once. What
 * they buy is the override point: inside an implementation's own copy the loop runs on a concrete
 * array the compiler can see through, so bounds checks hoist and the dispatch on {@code catalog}
 * happens once for the range rather than once per element. That second saving is the one specific to
 * this interface - addressing the column by parameter rather than by receiver puts a switch inside
 * the callee, and a switch inside a call that did not inline is paid on every element.
 * <p>
 * Copying into the caller's array rather than handing values to a consumer is deliberate: a callback
 * puts a call back per element at the exact site the bulk form exists to empty. Only the primitive
 * views have a range form, since a bulk {@code Object} accessor is an {@code Object[]} fill, which is
 * the thing this engine is arranged to avoid.
 * */
default void copyIntsInto(int catalog, long from, long to, int[] dest, int offset) {
	for(long identity = from; identity < to; identity++) { dest[offset++] = getIntFor(catalog, identity); }
}

default void copyLongsInto(int catalog, long from, long to, long[] dest, int offset) {
	for(long identity = from; identity < to; identity++) { dest[offset++] = getLongFor(catalog, identity); }
}

default void copyDoublesInto(int catalog, long from, long to, double[] dest, int offset) {
	for(long identity = from; identity < to; identity++) { dest[offset++] = getDoubleFor(catalog, identity); }
}

default void copyBooleansInto(int catalog, long from, long to, boolean[] dest, int offset) {
	for(long identity = from; identity < to; identity++) { dest[offset++] = getBooleanFor(catalog, identity); }
}


	/** The refusal every unimplemented accessor raises, naming what this catalog does hold. */
default UnsupportedOperationException cannotAnswer(int catalog, String asked) {
	return new UnsupportedOperationException(
		getClass().getSimpleName() + " holds " + typeName(getPreferredType(catalog)) + ", asked for " + asked);
}

static String typeName(int preferredType) {
	return switch(preferredType) {
		case PREFERRED_INTEGER -> "int";
		case PREFERRED_LONGI -> "long";
		case PREFERRED_FLOATING -> "floating";
		case PREFERRED_BOOL -> "boolean";
		case PREFERRED_OBJECT -> "Object";
		case PREFERRED_STRING -> "String";
		default -> "type " + preferredType;
	};
}


/**
 * The exact conversions, kept here so every catalog fails the same way on the same value.
 * <p>
 * Each is a round trip: convert, convert back, and refuse if the value moved. The magnitude guards
 * are there because a narrowing cast saturates rather than wrapping, which can make a round trip
 * agree on a value that never fitted - {@code (long)(double) Long.MAX_VALUE} is the case.
 * */
static int exactInt(long value) {
	return Math.toIntExact(value);
}

static short exactShort(long value) {
	short result = (short) value;
	if(result != value) { throw new ArithmeticException(value + " is not an exact short"); }
	return result;
}

static byte exactByte(long value) {
	byte result = (byte) value;
	if(result != value) { throw new ArithmeticException(value + " is not an exact byte"); }
	return result;
}

static int exactInt(double value) {
	int result = (int) value;
	if(result != value) { throw new ArithmeticException(value + " is not an exact int"); }
	return result;
}

static long exactLong(double value) {
	long result = (long) value;
	if(result != value || value >= 9.223372036854776E18) {
		throw new ArithmeticException(value + " is not an exact long");
	}
	return result;
}

static double exactDouble(long value) {
	double result = value;
	if((long) result != value || result >= 9.223372036854776E18) {
		throw new ArithmeticException(value + " has no exact double");
	}
	return result;
}


}

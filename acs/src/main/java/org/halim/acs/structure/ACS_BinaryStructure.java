package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Requestable;

/**
 * The most formal binary structure, the connector or elements-clusters: an identity of the first arity is
 * connected to an identity of the second, or not. That is the whole relation - no weight - so one indexing
 * logic has many representations, each fast for a different case, and which one an arity pair gets is the
 * client's choice, not this contract's. Every binary structure is it, each kind a restriction of it.
 * <p>
 * The two sides are the <em>primary</em>, the arity at index 0, and the <em>complement</em>, the arity
 * at index 1. They are not symmetric in any implementation: each keeps one direction cheaper than the
 * other, and says which. They are two different arity slots, refused at build otherwise: a relation within
 * one arity is a unary graph.
 * <p>
 * Read as elements and clusters, the primaries are the elements and a complement a cluster of the
 * elements connected to it. A binary structure holds no graph: graph rules over the primaries are
 * {@link ACS_BinaryStructure_U}'s, which is both a binary and a unary structure.
 * <p>
 * The contract is the most formal case, a primary's complements unordered and with repeats, any number
 * either side, and a storage optimizes for the restrictions it keeps, refusing a request that would break
 * one.
 * <p>
 * A connection is made only between identities their arities hold, and checked there; breaking one,
 * and every read, take the identities as given. Every change is journaled as the connection it makes or
 * breaks, so undo and redo replay single connections. Identities are ints, as in every structure: a
 * connection costs an int on both ends.
 * */
public interface ACS_BinaryStructure extends ACS_StructureBase {


@Override
default int getArityCount() { return 2; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1); }

default int getPrimaryArity() { return getArityIdentity(0); }
default int getComplementArity() { return getArityIdentity(1); }



/**
 * Connects the two, once more when they already were; where the storage keeps a pair once, false when
 * they already were. A refusal when either is not an occupied identity of its arity, or a restriction forbids it.
 * */
@ACS_Requestable(0)
boolean relateToBinary(int primary, int complement, String comment);

/** Breaks every connection between the two. False when they were not connected; refuses a break a restriction forbids. */
@ACS_Requestable(1)
boolean unrelateFromBinary(int primary, int complement, String comment);

@ACS_Requestable(2)
boolean isRelatedToBinary(int primary, int complement);

/**
 * Moves {@code primary} to {@code complement}: every complement it has is broken and {@code complement}
 * becomes its one. False when nothing changes. Refuses what {@link #relateToBinary} and
 * {@link #unrelateFromBinary} refuse, but never for a second complement or for the primary left unconnected
 * midway, since the move is one change.
 * */
@ACS_Requestable(3)
boolean setComplement(int primary, int complement, String comment);

/** As {@link #setComplement} from the other side: every primary of {@code complement} is broken and {@code primary} becomes its one. */
@ACS_Requestable(4)
boolean setPrimary(int complement, int primary, String comment);

/**
 * The complements of {@code primary} become {@code complements}, a repeat counting once, as one change:
 * refused for what the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
@ACS_Requestable(5)
boolean setComplementsOf(int primary, int[] complements, String comment);

/** The primaries of {@code complement} become {@code primaries}, as {@link #setComplementsOf} sets them. */
@ACS_Requestable(6)
boolean setPrimariesOf(int complement, int[] primaries, String comment);

/**
 * Every connection of {@code source} goes to {@code target}, both of the arity at {@code arityIndex}, 0 the
 * primary and 1 the complement, and {@code source} is left with none. Two changes in one request, so refused
 * where the merged list breaks a restriction, or where {@code source} may not be left unconnected. False
 * when nothing changes.
 * */
@ACS_Requestable(7)
default boolean moveBinary(int source, int target, int arityIndex, String comment) {
	boolean changed = mergeBinary(source, target, arityIndex, comment);
	return (arityIndex == 0 ? setComplementsOf(source, new int[0], comment) : setPrimariesOf(source, new int[0], comment)) || changed;
}

/** As {@link #moveBinary}, but {@code source} keeps its connections. */
@ACS_Requestable(8)
default boolean mergeBinary(int source, int target, int arityIndex, String comment) {
	if(arityIndex == 0) {
		IntOpenHashSet union = new IntOpenHashSet(getComplementsOf(target));
		union.addAll(IntArrayList.wrap(getComplementsOf(source)));
		return setComplementsOf(target, union.toIntArray(), comment);
	}
	if(arityIndex != 1) { throw new IllegalArgumentException("a binary structure has arity indices 0 and 1, not " + arityIndex); }
	IntOpenHashSet union = new IntOpenHashSet(getPrimariesOf(target));
	union.addAll(IntArrayList.wrap(getPrimariesOf(source)));
	return setPrimariesOf(target, union.toIntArray(), comment);
}


/** How many connections {@code primary} has, a repeated complement counting each time. */
@ACS_Requestable(9)
int complementCount(int primary);

/** How many primaries {@code complement} is connected to, each once. */
@ACS_Requestable(10)
int primaryCount(int complement);

/**
 * The complements of {@code primary}, {@link #complementCount} of them, a repeated one each time, in the
 * order the implementation states.
 * */
void copyComplementsInto(int primary, int[] dest, int offset);

/** The primaries of {@code complement}, {@link #primaryCount} of them, in the order the implementation states. */
void copyPrimariesInto(int complement, int[] dest, int offset);

/** The complements of {@code primary}, as {@link #copyComplementsInto} gives them. */
@ACS_Requestable(11)
default int[] getComplementsOf(int primary) {
	int[] complements = new int[complementCount(primary)];
	copyComplementsInto(primary, complements, 0);
	return complements;
}

/** The primaries of {@code complement}, as {@link #copyPrimariesInto} gives them. */
@ACS_Requestable(12)
default int[] getPrimariesOf(int complement) {
	int[] primaries = new int[primaryCount(complement)];
	copyPrimariesInto(complement, primaries, 0);
	return primaries;
}


}

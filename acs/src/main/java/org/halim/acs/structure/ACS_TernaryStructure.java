package org.halim.acs.structure;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.halim.acs.ACS_Requestable;

/**
 * The most formal ternary structure, primary-relation-complement: each identity of the <em>relation</em>
 * arity, at index 1, relates the primaries it takes, of the arity at index 0, to the complements it gives,
 * of the arity at index 2 - a few related to a few, either side any number, so a request edits one side of
 * one relation, or both at once. Every ternary kind is a restriction of it, which its storage keeps. A
 * request is a link without direction; on a directed reading the primaries point to the complements, as a
 * link's first to its last. The three are different arity slots, refused at build otherwise.
 * <p>
 * A relation is any identity its arity holds, captured elsewhere. A relation leaving its arity loses both
 * sides; a primary or a complement leaving its arity is dropped from every relation holding it, and the
 * relations stay. Every change is journaled as the take or the give it makes or drops. Identities are ints,
 * as in every structure.
 * */
public interface ACS_TernaryStructure extends ACS_StructureBase {


@Override
default int getArityCount() { return 3; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1) || identity == getArityIdentity(2); }

default int getPrimaryArity() { return getArityIdentity(0); }
default int getRelationArity() { return getArityIdentity(1); }
default int getComplementArity() { return getArityIdentity(2); }


/** The relation takes the primary and gives the complement, as one change, along with what it holds already. False when it did both. */
@ACS_Requestable(5)
boolean relateToTernary(int primary, int relation, int complement, String comment);

/** The relation drops both the primary and the complement, as one change; false when it held neither. */
@ACS_Requestable(6)
boolean unrelateFromTernary(int primary, int relation, int complement, String comment);

/** Whether the relation takes the primary and gives the complement. */
@ACS_Requestable(7)
default boolean isRelatedToTernary(int primary, int relation, int complement) {
	boolean taken = false, given = false;
	for(int at : takenOf(relation)) { taken |= at == primary; }
	for(int at : givenOf(relation)) { given |= at == complement; }
	return taken && given;
}

/** The relation takes the primary. False when it already does; refuses an identity its arity does not hold. */
@ACS_Requestable(8)
boolean take(int primary, int relation, String comment);

/** The relation no longer takes the primary; false when it did not. */
@ACS_Requestable(9)
boolean untake(int primary, int relation, String comment);

/** The relation gives the complement, as {@link #take} takes. */
@ACS_Requestable(10)
boolean give(int relation, int complement, String comment);

/** The relation no longer gives the complement; false when it did not. */
@ACS_Requestable(11)
boolean ungive(int relation, int complement, String comment);

/** What the relation takes becomes {@code primaries}, a repeat counting once, as one change. False when nothing changes. */
@ACS_Requestable(12)
boolean setTaken(int relation, int[] primaries, String comment);

/** What the relation gives becomes {@code complements}, as {@link #setTaken} sets what it takes. */
@ACS_Requestable(13)
boolean setGiven(int relation, int[] complements, String comment);

/**
 * What {@code source} takes and gives goes to {@code target}, and {@code source} is left with nothing.
 * Two changes in one request, so refused where the merged lists break a restriction. False when nothing
 * changes.
 * */
@ACS_Requestable(14)
default boolean moveTernary(int source, int target, String comment) {
	boolean changed = mergeTernary(source, target, comment);
	changed |= setTaken(source, new int[0], comment);
	return setGiven(source, new int[0], comment) || changed;
}

/** As {@link #moveTernary}, but {@code source} keeps what it holds. */
@ACS_Requestable(15)
default boolean mergeTernary(int source, int target, String comment) {
	IntOpenHashSet taken = new IntOpenHashSet(takenOf(target)), given = new IntOpenHashSet(givenOf(target));
	for(int at : takenOf(source)) { taken.add(at); }
	for(int at : givenOf(source)) { given.add(at); }
	boolean changed = setTaken(target, taken.toIntArray(), comment);
	return setGiven(target, given.toIntArray(), comment) || changed;
}


/** The primaries the relation takes, in the order the implementation states. */
@ACS_Requestable(16)
int[] takenOf(int relation);

/** The complements the relation gives, in the order the implementation states. */
@ACS_Requestable(17)
int[] givenOf(int relation);

/** The relations taking the primary, ascending. */
@ACS_Requestable(18)
int[] takersOf(int primary);

/** The relations giving the complement, ascending. */
@ACS_Requestable(19)
int[] giversOf(int complement);

default int takenCount(int relation) { return takenOf(relation).length; }

default int givenCount(int relation) { return givenOf(relation).length; }

default int takerCount(int primary) { return takersOf(primary).length; }

default int giverCount(int complement) { return giversOf(complement).length; }


}

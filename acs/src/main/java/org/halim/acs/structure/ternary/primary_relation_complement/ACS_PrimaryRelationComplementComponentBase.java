package org.halim.acs.structure.ternary.primary_relation_complement;

import org.halim.acs.structure.ACS_StructureComponentBase;

/**
 * The most formal ternary structure: each identity of the <em>relation</em> arity, at index 1, relates the
 * primaries it takes, of the arity at index 0, to the complements it gives, of the arity at index 2 - a few
 * related to a few, either side any number, so a request edits one side of one relation, or both at once.
 * Of-by-to is a restriction of it, pairing each primary with one complement. A request is a link without
 * direction; on a directed reading the primaries point to the complements, as a link's first to its last.
 * The three are different arity slots, refused at build otherwise.
 * <p>
 * A relation is any identity its arity holds, captured elsewhere. A relation leaving its arity loses both
 * sides; a primary or a complement leaving its arity is dropped from every relation holding it, and the
 * relations stay. Every change is journaled as the take or the give it makes or drops. Identities are ints,
 * as in every structure.
 * */
public interface ACS_PrimaryRelationComplementComponentBase extends ACS_StructureComponentBase {

@Override
default int getArityCount() { return 3; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1) || identity == getArityIdentity(2); }

default int getPrimaryArity() { return getArityIdentity(0); }
default int getRelationArity() { return getArityIdentity(1); }
default int getComplementArity() { return getArityIdentity(2); }


/** The relation takes the primary. False when it already does; refuses an identity its arity does not hold. */
boolean take(int primary, int relation, String comment);

/** The relation no longer takes the primary; false when it did not. */
boolean untake(int primary, int relation, String comment);

/** The relation gives the complement, as {@link #take} takes. */
boolean give(int relation, int complement, String comment);

/** The relation no longer gives the complement; false when it did not. */
boolean ungive(int relation, int complement, String comment);

/** The relation takes the primary and gives the complement, as one change, along with what it holds already. False when it did both. */
boolean relate(int primary, int relation, int complement, String comment);

/** The relation drops both the primary and the complement, as one change; false when it held neither. */
boolean unrelate(int primary, int relation, int complement, String comment);

/** What the relation takes becomes {@code primaries}, a repeat counting once, as one change. False when nothing changes. */
boolean setTaken(int relation, int[] primaries, String comment);

/** What the relation gives becomes {@code complements}, as {@link #setTaken} sets what it takes. */
boolean setGiven(int relation, int[] complements, String comment);


/** The primaries the relation takes, in the order the implementation states. */
int[] takenOf(int relation);

/** The complements the relation gives, in the order the implementation states. */
int[] givenOf(int relation);

/** The relations taking the primary, ascending. */
int[] takersOf(int primary);

/** The relations giving the complement, ascending. */
int[] giversOf(int complement);

/** Whether the relation takes the primary and gives the complement. */
default boolean isRelated(int primary, int relation, int complement) {
	boolean taken = false, given = false;
	for(int held : takenOf(relation)) { taken |= held == primary; }
	for(int held : givenOf(relation)) { given |= held == complement; }
	return taken && given;
}

default int takenCount(int relation) { return takenOf(relation).length; }

default int givenCount(int relation) { return givenOf(relation).length; }

default int takerCount(int primary) { return takersOf(primary).length; }

default int giverCount(int complement) { return giversOf(complement).length; }


}

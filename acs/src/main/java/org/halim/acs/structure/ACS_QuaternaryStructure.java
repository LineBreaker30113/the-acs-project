package org.halim.acs.structure;

import org.halim.acs.database.ACS_Requestable;

/**
 * The most formal quaternary structure: each identity of the <em>unit</em> arity, at index 3, relates
 * triples of the arities at indices 0, 1 and 2 - a context, a time or a provenance over the relations of
 * the three. A unit keeps its triples whole, as a via keeps its pairs, so which elements go together is
 * kept, not only which elements a unit holds. The four are different arity slots, refused at build
 * otherwise; no storage takes it yet.
 * <p>
 * A unit is any identity its arity holds, captured elsewhere. A unit leaving its arity loses its triples;
 * an element leaving its arity is dropped with every triple holding it, and the units stay. Every change
 * is journaled as the triple it adds or removes.
 * */
public interface ACS_QuaternaryStructure extends ACS_StructureBase {


@Override
default int getArityCount() { return 4; }

@Override
default boolean isRelatedArity(int identity) {
	return identity == getArityIdentity(0) || identity == getArityIdentity(1) || identity == getArityIdentity(2) || identity == getArityIdentity(3);
}

default int getUnitArity() { return getArityIdentity(3); }


/** Adds the triple to what {@code unit} relates. False when the storage keeps a triple once and it already was; refuses an identity its arity does not hold. */
@ACS_Requestable(5)
boolean relateToQuaternary(int first, int second, int third, int unit, String comment);

/** Removes the triple from what {@code unit} relates, every time it is there. False when it was not. */
@ACS_Requestable(6)
boolean unrelateFromQuaternary(int first, int second, int third, int unit, String comment);

@ACS_Requestable(7)
boolean isRelatedToQuaternary(int first, int second, int third, int unit);

/** Every triple of {@code source} goes to {@code target}, and {@code source} is left with none. False when nothing changes. */
@ACS_Requestable(8)
default boolean moveQuaternary(int source, int target, String comment) {
	boolean changed = mergeQuaternary(source, target, comment);
	int[] triples = triplesOf(source);
	for(int at = 0; at < triples.length; at += 3) { changed |= unrelateFromQuaternary(triples[at], triples[at + 1], triples[at + 2], source, comment); }
	return changed;
}

/** As {@link #moveQuaternary}, but {@code source} keeps its triples; a triple {@code target} holds already counts once. */
@ACS_Requestable(9)
default boolean mergeQuaternary(int source, int target, String comment) {
	boolean changed = false;
	int[] triples = triplesOf(source);
	for(int at = 0; at < triples.length; at += 3) {
		if(!isRelatedToQuaternary(triples[at], triples[at + 1], triples[at + 2], target)) { changed |= relateToQuaternary(triples[at], triples[at + 1], triples[at + 2], target, comment); }
	}
	return changed;
}


/** The unit's triples, as first, second, third, first, ..., in the order the implementation states. */
@ACS_Requestable(10)
int[] triplesOf(int unit);

/** The units relating the triple, ascending. */
@ACS_Requestable(11)
int[] unitsOf(int first, int second, int third);

/** The units with a triple holding {@code element} of the arity at {@code arityIndex}, 0 to 2, ascending. */
@ACS_Requestable(12)
int[] unitsHolding(int element, int arityIndex);


}

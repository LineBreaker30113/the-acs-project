package org.halim.acs.structure.ternary.of_by_to;

import org.halim.acs.structure.ternary.primary_relation_complement.ACS_PrimaryRelationComplementComponentBase;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A structure over three arities: each identity of the <em>by</em> arity, at index 1, matches a list of
 * pairs, each pairing an identity of the <em>of</em> arity, at index 0, with one of the <em>to</em> arity, at
 * index 2 - a transition taking each of its ofs to its to, whose own data lives in its arity. A by matching
 * one pair transforms one element to another; one matching many is a single transition over many, as a
 * letter of an automaton takes each state to its next. In meaning a by is a connector of its own between
 * the of and the to arities, an order higher than a connector as node-edge is than the unary graph: the
 * connections matter, so each is an identity. The three are different arity slots, refused at build
 * otherwise.
 * <p>
 * The contract is the most formal case, and a storage optimizes for the kinds it takes: 0 is unordered lists
 * with repeats, any number of bys on any pairs, and each restriction narrows that - the bits of one int,
 * given when it is built and fixed from then on. A storage takes the sets it can keep and refuses the
 * others when built, and a request that would break a restriction is refused. The at-most bits are
 * {@code AT_MOST_ONE_X_PER_Y}, X and Y two of the of, the by and the to: at most one X with each Y among the
 * pairs. <em>Transitions</em>, a by for each transition and nothing else, are {@link #AT_MOST_ONE_OF_PER_BY},
 * {@link #AT_MOST_ONE_TO_PER_BY}, {@link #NO_REPEATS} and {@link #EVERY_BY_MATCHES}: one pair a by. The
 * cartesian product of the of and the to arities adds {@link #NO_PARALLEL_BYS}, a by for each pair there is;
 * {@link #FULL} adds every pair.
 * <p>
 * A by is any identity its arity holds: the requests work on bys captured elsewhere, and {@link #ensureBy}
 * captures a new one through the core, as a request nested in its own, so undoing it removes the by. A by
 * leaving its arity is forgotten here; an of or a to leaving its arity is dropped from every pair holding
 * it, and the by stays - but on transitions, where a by losing its one pair is no transition any more and
 * is removed through the core, so the by arity holds only transitions that exist. Every change is journaled
 * as the pair it adds or removes. Identities are ints, as in every structure.
 * */
public interface ACS_OfByToComponentBase extends ACS_PrimaryRelationComplementComponentBase {

/** A pair is in a by's list once at most. Without it, a by may match the same pair twice, each time a pair of its own. */
int NO_REPEATS = 1 << 1;

/** No two bys match the same pairs: the same list. */
int NO_PARALLEL_BYS = 1 << 2;

/** A by's pairs share one of: it takes one element to its tos. */
int AT_MOST_ONE_OF_PER_BY = 1 << 3;

/** A by's pairs share one to: it takes its ofs to one element. */
int AT_MOST_ONE_TO_PER_BY = 1 << 4;

/** An of is in the pairs of one by at most. */
int AT_MOST_ONE_BY_PER_OF = 1 << 5;

/** An of is paired with one to at most, whichever bys pair it: the bys together are a function on the ofs. */
int AT_MOST_ONE_TO_PER_OF = 1 << 6;

/** A to is in the pairs of one by at most. */
int AT_MOST_ONE_BY_PER_TO = 1 << 7;

/** A to is paired with one of at most, whichever bys pair it. */
int AT_MOST_ONE_OF_PER_TO = 1 << 8;

/**
 * Every by matches a pair. A by counts from its first pair, since a new identity has none, and forget,
 * which no structure refuses, can still break it, but on transitions, which remove such a by.
 * */
int EVERY_BY_MATCHES = 1 << 9;

/**
 * Every (of, to) pair has its by. Bys added one at a time cannot keep it, since a new of needs a by for
 * every to at once: it holds where the by arity is strictly caused, so its bys are calculated.
 * */
int FULL = 1 << 10;

/** The kind every transitions storage is: one pair a by. */
int TRANSITIONS = AT_MOST_ONE_OF_PER_BY | AT_MOST_ONE_TO_PER_BY | NO_REPEATS | EVERY_BY_MATCHES;

/** The restrictions it was built with, an OR of the constants above. */
int getRestrictions();

default boolean isTransitions() { return (getRestrictions() & TRANSITIONS) == TRANSITIONS; }

@Override
default int getArityCount() { return 3; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1) || identity == getArityIdentity(2); }

default int getOfArity() { return getArityIdentity(0); }
default int getByArity() { return getArityIdentity(1); }
default int getToArity() { return getArityIdentity(2); }


/**
 * A by matching {@code pairs}, given as of, to, of, to, in their order, given back: a new one captured on the
 * by arity, or, with {@link #NO_PARALLEL_BYS}, the one already there. Refuses an odd length, an element its
 * arity does not hold, and pairs a restriction forbids.
 * */
int ensureBy(int[] pairs, String comment);

/**
 * Adds the pair ({@code of}, {@code to}) to what {@code by} matches.
 * False when, with {@link #NO_REPEATS}, it already was; refuses an identity its arity does not hold, and a
 * pair a restriction forbids.
 * */
boolean addPair(int by, int of, int to, String comment);

/** Removes the pair from what {@code by} matches, every time it is there, closing the gaps. False when it was not; refuses a removal a restriction forbids. */
boolean removePair(int by, int of, int to, String comment);

/**
 * Replaces what {@code by} matches with {@code pairs}, given as {@link #ensureBy} takes them, as one change:
 * refused for what the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
boolean setPairs(int by, int[] pairs, String comment);


/** How many pairs {@code by} matches, a repeated one counting each time. */
int pairCount(int by);

/** What {@code by} matches, {@link #pairCount} pairs as of, to, of, to, in the order the implementation states. */
void copyPairsInto(int by, int[] dest, int offset);

/** How many bys pair {@code of}, each once. */
int takerCount(int of);

/** The bys pairing {@code of}, {@link #takerCount} of them, ascending. */
void copyTakersInto(int of, int[] dest, int offset);

/** How many bys pair {@code to}, each once. */
int giverCount(int to);

/** The bys pairing {@code to}, {@link #giverCount} of them, ascending. */
void copyGiversInto(int to, int[] dest, int offset);


/** The tos {@code of} is paired with, by any by, each once and ascending, written as an int[] is: its length, then its elements. */
void writeTosOf(int of, DataOutputStream dos) throws IOException;

/** The ofs {@code to} is paired with, by any by, each once and ascending, written as {@link #writeTosOf} writes. */
void writeOfsOf(int to, DataOutputStream dos) throws IOException;


/** What {@link #writeTosOf} writes, as an int[]: requests take and give no stream. */
int[] tosOf(int of);

/** What {@link #writeOfsOf} writes, as an int[]. */
int[] ofsOf(int to);

/** {@link #addPair}, as primary-relation-complement names it: the primary is the of, the relation the by, the complement the to. */
default boolean relate(int primary, int relation, int complement, String comment) { return addPair(relation, primary, complement, comment); }

/** {@link #removePair}, as primary-relation-complement names it; on transitions the by leaves with its one pair. */
default boolean unrelate(int primary, int relation, int complement, String comment) { return removePair(relation, primary, complement, comment); }

/** Whether the relation holds the pair. */
default boolean isRelated(int primary, int relation, int complement) {
	int[] pairs = pairsOf(relation);
	for(int at = 0; at < pairs.length; at += 2) { if(pairs[at] == primary && pairs[at + 1] == complement) { return true; } }
	return false;
}

/** The relation's pairs as primary, complement, primary, complement, as {@link #copyPairsInto} gives them. */
default int[] pairsOf(int relation) {
	int[] pairs = new int[2 * pairCount(relation)];
	copyPairsInto(relation, pairs, 0);
	return pairs;
}

/** The ofs of the by's pairs, each once, in the order of its pairs. */
@Override
default int[] takenOf(int relation) { return side(relation, 0); }

/** The tos of the by's pairs, each once, in the order of its pairs. */
@Override
default int[] givenOf(int relation) { return side(relation, 1); }

/** False when the by pairs the primary already; refused otherwise, since a pair takes its primary and its complement at once: {@link #relate} adds one. */
@Override
default boolean take(int primary, int relation, String comment) { return refusedUnlessHeld(takenOf(relation), primary, "a pair takes its primary with its complement; relate them instead"); }

/** False when the by does not pair the primary; refused otherwise, since its complements would be left without it: {@link #unrelate} removes a pair. */
@Override
default boolean untake(int primary, int relation, String comment) { return refusedIfHeld(takenOf(relation), primary, "a pair loses its primary with its complement; unrelate them instead"); }

/** As {@link #take}, for the complement. */
@Override
default boolean give(int relation, int complement, String comment) { return refusedUnlessHeld(givenOf(relation), complement, "a pair gives its complement with its primary; relate them instead"); }

/** As {@link #untake}, for the complement. */
@Override
default boolean ungive(int relation, int complement, String comment) { return refusedIfHeld(givenOf(relation), complement, "a pair loses its complement with its primary; unrelate them instead"); }

/** False when the by takes those already; refused otherwise, since one side does not change alone: {@link #setPairs} changes both. */
@Override
default boolean setTaken(int relation, int[] primaries, String comment) { return sameSide(takenOf(relation), primaries, "set the pairs instead"); }

/** As {@link #setTaken}, for the complements. */
@Override
default boolean setGiven(int relation, int[] complements, String comment) { return sameSide(givenOf(relation), complements, "set the pairs instead"); }

/** One side of the by's pairs, 0 the ofs and 1 the tos, each once, in the order of its pairs. */
private int[] side(int relation, int side) {
	int[] pairs = pairsOf(relation);
	int[] found = new int[pairs.length / 2];
	int kept = 0;
	for(int at = side; at < pairs.length; at += 2) {
		boolean met = false;
		for(int i = 0; i < kept; i++) { met |= found[i] == pairs[at]; }
		if(!met) { found[kept++] = pairs[at]; }
	}
	return java.util.Arrays.copyOf(found, kept);
}

/** False when {@code held} holds {@code identity}; a refusal otherwise. */
private static boolean refusedUnlessHeld(int[] held, int identity, String instead) {
	for(int at : held) { if(at == identity) { return false; } }
	throw new IllegalArgumentException("of-by-to keeps pairs: " + instead);
}

/** False when {@code held} lacks {@code identity}; a refusal otherwise. */
private static boolean refusedIfHeld(int[] held, int identity, String instead) {
	for(int at : held) { if(at == identity) { throw new IllegalArgumentException("of-by-to keeps pairs: " + instead); } }
	return false;
}

/** False when {@code wanted} is {@code held} as a set; a refusal otherwise. */
private static boolean sameSide(int[] held, int[] wanted, String instead) {
	java.util.Set<Integer> a = new java.util.HashSet<>(), b = new java.util.HashSet<>();
	for(int at : held) { a.add(at); }
	for(int at : wanted) { b.add(at); }
	if(a.equals(b)) { return false; }
	throw new IllegalArgumentException("of-by-to keeps pairs, so one side does not change alone: " + instead);
}

/** The relations pairing the primary, as {@link #copyTakersInto} gives them. */
default int[] takersOf(int primary) {
	int[] takers = new int[takerCount(primary)];
	copyTakersInto(primary, takers, 0);
	return takers;
}

/** The relations pairing the complement, as {@link #copyGiversInto} gives them. */
default int[] giversOf(int complement) {
	int[] givers = new int[giverCount(complement)];
	copyGiversInto(complement, givers, 0);
	return givers;
}


}

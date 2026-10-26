package org.halim.acse.structure.ternary.of_by_to;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.structure.ACS_TernaryStructure;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.halim.acse.structure.TernaryRestrictions.*;

/**
 * The ternary kind whose relations keep pairs: each identity of the <em>via</em> arity, at index 1, matches
 * a list of pairs, each pairing an identity of the <em>from</em> arity, at index 0, with one of the
 * <em>to</em> arity, at index 2 - a transition taking each of its froms to its to, whose own data lives in
 * its arity. A via matching one pair transforms one element to another; one matching many is a single
 * transition over many, as a letter of an automaton takes each state to its next. In meaning a via is a
 * connector of its own between the from and the to arities, an order higher than a connector as node-edge
 * is than the unary graph: the connections matter, so each is an identity.
 * <p>
 * The ternary structure's take and give read here as the froms and the tos of a via's pairs; a side does
 * not change alone, so those requests are refused but where they change nothing, and a pair is added and
 * removed whole. 0 is unordered lists with repeats, any number of vias on any pairs. <em>Transitions</em>,
 * a via for each transition and nothing else, are {@link #TRANSITIONS}: one pair a via. The cartesian
 * product of the from and the to arities adds {@link #NO_PARALLEL_VIAS}, a via for each pair there is;
 * {@link #FULL} adds every pair.
 * <p>
 * A via is any identity its arity holds: the requests work on vias captured elsewhere, and {@link #ensureVia}
 * captures a new one through the core, as a request nested in its own, so undoing it removes the via. A via
 * leaving its arity is forgotten here; a from or a to leaving its arity is dropped from every pair holding
 * it, and the via stays - but on transitions, where a via losing its one pair is no transition any more and
 * is removed through the core, so the via arity holds only transitions that exist. Every change is
 * journaled as the pair it adds or removes.
 * <p>
 * No contract names this kind, since pairs are no enforced structure; its requests are marked here, after
 * the ones of {@link ACS_TernaryStructure}, for every of-by-to storage to share.
 * */
public abstract class OfByToBase implements ACS_TernaryStructure {

/** A pair is in a via's list once at most. Without it, a via may match the same pair twice, each time a pair of its own. */
public static final int NO_REPEATS = 1 << 1;

/** No two vias match the same pairs: the same list. */
public static final int NO_PARALLEL_VIAS = 1 << 2;

/** A from is paired with one to at most, whichever vias pair it: the vias together are a function on the froms. */
public static final int AT_MOST_ONE_TO_PER_FROM = 1 << 6;

/** A to is paired with one from at most, whichever vias pair it. */
public static final int AT_MOST_ONE_FROM_PER_TO = 1 << 8;

/**
 * Every via matches a pair. A via counts from its first pair, since a new identity has none, and forget,
 * which no structure refuses, can still break it, but on transitions, which remove such a via.
 * */
public static final int EVERY_VIA_MATCHES = 1 << 9;

/**
 * Every (from, to) pair has its via. Vias added one at a time cannot keep it, since a new from needs a via
 * for every to at once: it holds where the via arity is strictly caused, so its vias are calculated.
 * */
public static final int FULL = 1 << 10;

/** The kind every transitions storage is: one pair a via. */
public static final int TRANSITIONS = AT_MOST_ONE_PRIMARY_PER_RELATION | AT_MOST_ONE_COMPLEMENT_PER_RELATION | NO_REPEATS | EVERY_VIA_MATCHES;

/** The restrictions it was built with, an OR of the bits of {@link org.halim.acse.structure.TernaryRestrictions} and its own. */
public abstract int getRestrictions();

public boolean isTransitions() { return (getRestrictions() & TRANSITIONS) == TRANSITIONS; }

public int getFromArity() { return getArityIdentity(0); }
public int getViaArity() { return getArityIdentity(1); }
public int getToArity() { return getArityIdentity(2); }


/**
 * A via matching {@code pairs}, given as from, to, from, to, in their order, given back: a new one captured
 * on the via arity, or, with {@link #NO_PARALLEL_VIAS}, the one already there. Refuses an odd length, an
 * element its arity does not hold, and pairs a restriction forbids.
 * */
@ACS_Requestable(20)
public abstract int ensureVia(int[] pairs, String comment);

/**
 * Adds the pair ({@code from}, {@code to}) to what {@code via} matches.
 * False when, with {@link #NO_REPEATS}, it already was; refuses an identity its arity does not hold, and a
 * pair a restriction forbids.
 * */
@ACS_Requestable(21)
public abstract boolean addPair(int via, int from, int to, String comment);

/** Removes the pair from what {@code via} matches, every time it is there, closing the gaps. False when it was not; refuses a removal a restriction forbids. */
@ACS_Requestable(22)
public abstract boolean removePair(int via, int from, int to, String comment);

/**
 * Replaces what {@code via} matches with {@code pairs}, given as {@link #ensureVia} takes them, as one change:
 * refused for what the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
@ACS_Requestable(23)
public abstract boolean setPairs(int via, int[] pairs, String comment);


/** How many pairs {@code via} matches, a repeated one counting each time. */
public abstract int pairCount(int via);

/** What {@code via} matches, {@link #pairCount} pairs as from, to, from, to, in the order the implementation states. */
public abstract void copyPairsInto(int via, int[] dest, int offset);

/** How many vias pair {@code from}, each once. */
@Override
public abstract int takerCount(int from);

/** The vias pairing {@code from}, {@link #takerCount} of them, ascending. */
public abstract void copyTakersInto(int from, int[] dest, int offset);

/** How many vias pair {@code to}, each once. */
@Override
public abstract int giverCount(int to);

/** The vias pairing {@code to}, {@link #giverCount} of them, ascending. */
public abstract void copyGiversInto(int to, int[] dest, int offset);

/** The tos {@code from} is paired with, by any via, each once and ascending, written as an int[] is: its length, then its elements. */
public abstract void writeTosOf(int from, DataOutputStream dos) throws IOException;

/** The froms {@code to} is paired with, by any via, each once and ascending, written as {@link #writeTosOf} writes. */
public abstract void writeFromsOf(int to, DataOutputStream dos) throws IOException;

/** The via's pairs as from, to, from, to, as {@link #copyPairsInto} gives them. */
@ACS_Requestable(24)
public int[] pairsOf(int via) {
	int[] pairs = new int[2 * pairCount(via)];
	copyPairsInto(via, pairs, 0);
	return pairs;
}

/** What {@link #writeTosOf} writes, as an int[]: requests take and give no stream. */
@ACS_Requestable(25)
public abstract int[] tosOf(int from);

/** What {@link #writeFromsOf} writes, as an int[]. */
@ACS_Requestable(26)
public abstract int[] fromsOf(int to);


/** {@link #addPair}, as primary-relation-complement names it: the primary is the from, the relation the via, the complement the to. */
@Override
public boolean relateToTernary(int primary, int relation, int complement, String comment) { return addPair(relation, primary, complement, comment); }

/** {@link #removePair}, as primary-relation-complement names it; on transitions the via leaves with its one pair. */
@Override
public boolean unrelateFromTernary(int primary, int relation, int complement, String comment) { return removePair(relation, primary, complement, comment); }

/** Whether the via holds the pair. */
@Override
public boolean isRelatedToTernary(int primary, int relation, int complement) {
	int[] pairs = pairsOf(relation);
	for(int at = 0; at < pairs.length; at += 2) { if(pairs[at] == primary && pairs[at + 1] == complement) { return true; } }
	return false;
}

/** The froms of the via's pairs, each once, in the order of its pairs. */
@Override
public int[] takenOf(int relation) { return side(relation, 0); }

/** The tos of the via's pairs, each once, in the order of its pairs. */
@Override
public int[] givenOf(int relation) { return side(relation, 1); }

/** False when the via pairs the primary already; refused otherwise, since a pair takes its from and its to at once: {@link #relateToTernary} adds one. */
@Override
public boolean take(int primary, int relation, String comment) { return refusedUnlessHeld(takenOf(relation), primary, "a pair takes its from with its to; relate them instead"); }

/** False when the via does not pair the primary; refused otherwise, since its tos would be left without it: {@link #unrelateFromTernary} removes a pair. */
@Override
public boolean untake(int primary, int relation, String comment) { return refusedIfHeld(takenOf(relation), primary, "a pair loses its from with its to; unrelate them instead"); }

/** As {@link #take}, for the to. */
@Override
public boolean give(int relation, int complement, String comment) { return refusedUnlessHeld(givenOf(relation), complement, "a pair gives its to with its from; relate them instead"); }

/** As {@link #untake}, for the to. */
@Override
public boolean ungive(int relation, int complement, String comment) { return refusedIfHeld(givenOf(relation), complement, "a pair loses its to with its from; unrelate them instead"); }

/** False when the via takes those already; refused otherwise, since one side does not change alone: {@link #setPairs} changes both. */
@Override
public boolean setTaken(int relation, int[] primaries, String comment) { return sameSide(takenOf(relation), primaries, "set the pairs instead"); }

/** As {@link #setTaken}, for the tos. */
@Override
public boolean setGiven(int relation, int[] complements, String comment) { return sameSide(givenOf(relation), complements, "set the pairs instead"); }

/** The pairs of {@code source} go to {@code target}, as {@link #mergeTernary} puts them, and {@code source} is left with none. */
@Override
public boolean moveTernary(int source, int target, String comment) {
	boolean changed = mergeTernary(source, target, comment);
	return setPairs(source, new int[0], comment) || changed;
}

/** The pairs of {@code source} are added to those of {@code target}, a pair {@code target} already matches counting once. */
@Override
public boolean mergeTernary(int source, int target, String comment) {
	IntArrayList pairs = IntArrayList.wrap(pairsOf(target));
	int[] added = pairsOf(source);
	for(int at = 0; at < added.length; at += 2) { if(!isRelatedToTernary(added[at], target, added[at + 1])) { pairs.add(added[at]); pairs.add(added[at + 1]); } }
	return setPairs(target, pairs.toIntArray(), comment);
}

/** The vias pairing the from, as {@link #copyTakersInto} gives them. */
@Override
public int[] takersOf(int primary) {
	int[] takers = new int[takerCount(primary)];
	copyTakersInto(primary, takers, 0);
	return takers;
}

/** The vias pairing the to, as {@link #copyGiversInto} gives them. */
@Override
public int[] giversOf(int complement) {
	int[] givers = new int[giverCount(complement)];
	copyGiversInto(complement, givers, 0);
	return givers;
}


/** One side of the via's pairs, 0 the froms and 1 the tos, each once, in the order of its pairs. */
int[] side(int relation, int side) {
	int[] pairs = pairsOf(relation);
	int[] found = new int[pairs.length / 2];
	int kept = 0;
	for(int at = side; at < pairs.length; at += 2) {
		boolean met = false;
		for(int i = 0; i < kept; i++) { met |= found[i] == pairs[at]; }
		if(!met) { found[kept++] = pairs[at]; }
	}
	return Arrays.copyOf(found, kept);
}

/** False when {@code held} holds {@code identity}; a refusal otherwise. */
static boolean refusedUnlessHeld(int[] held, int identity, String instead) {
	for(int at : held) { if(at == identity) { return false; } }
	throw new IllegalArgumentException("from-via-to keeps pairs: " + instead);
}

/** False when {@code held} lacks {@code identity}; a refusal otherwise. */
static boolean refusedIfHeld(int[] held, int identity, String instead) {
	for(int at : held) { if(at == identity) { throw new IllegalArgumentException("from-via-to keeps pairs: " + instead); } }
	return false;
}

/** False when {@code wanted} is {@code held} as a set; a refusal otherwise. */
static boolean sameSide(int[] held, int[] wanted, String instead) {
	int[] a = Arrays.stream(held).distinct().sorted().toArray(), b = Arrays.stream(wanted).distinct().sorted().toArray();
	if(Arrays.equals(a, b)) { return false; }
	throw new IllegalArgumentException("from-via-to keeps pairs, so one side does not change alone: " + instead);
}


}

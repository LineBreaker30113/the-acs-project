package org.halim.acs.structure.binary.connector;

import org.halim.acs.structure.ACS_StructureComponentBase;

/**
 * The indexer: a structure over two arities, where an identity of the first is connected to an identity
 * of the second, or not. That is the whole relation - no weight - so one indexing logic has many
 * representations, each fast for a different case, and which one an arity pair gets is the client's
 * choice, not this contract's.
 * <p>
 * Every binary structure is a connector, each kind a restriction of it. Node-cluster is one: its nodes are
 * the primaries, and a cluster joins the nodes connected to it; node-edge is the node-cluster whose every
 * cluster, an edge, joins exactly two nodes.
 * <p>
 * The two sides are the <em>primary</em>, the arity at index 0, and the <em>complement</em>, the arity
 * at index 1. They are not symmetric in any implementation: each keeps one direction cheaper than the
 * other, and says which. They are two different arity slots, refused at build otherwise: a relation within
 * one arity is a unary graph.
 * <p>
 * The contract is the most formal case, and a storage optimizes for the kinds it takes: 0 is a primary's
 * complements unordered and with repeats, any number either side, and each restriction narrows that - the
 * bits of one int, given when it is built and fixed from then on. A storage takes the sets it can keep and
 * refuses the others when built, and a request that would break a restriction is refused.
 * <p>
 * A connection is made only between identities their arities hold, and checked there; breaking one,
 * and every read, take the identities as given. Every change is journaled as the connection it makes or
 * breaks, so undo and redo replay single connections. Identities are ints,
 * as in every structure: a connection costs an int on both ends.
 * */
public interface ACS_ConnectorComponentBase extends ACS_StructureComponentBase {

/** A primary is connected to one complement at most: many primaries to one complement. */
int AT_MOST_ONE_COMPLEMENT = 1;

/** A complement is connected to one primary at most; with {@link #AT_MOST_ONE_COMPLEMENT}, one to one. */
int AT_MOST_ONE_PRIMARY = 1 << 1;

/**
 * Every primary is connected. A primary counts from its first connection, since a new identity has none,
 * and forget, which no structure refuses, can still break it.
 * */
int EVERY_PRIMARY_CONNECTED = 1 << 2;

/** Every complement is connected, counted as {@link #EVERY_PRIMARY_CONNECTED} counts. */
int EVERY_COMPLEMENT_CONNECTED = 1 << 3;

/**
 * A primary is connected to a complement once at most. Without it, each connection to the same complement
 * is one of its own, holding a place of its own; from the complement's side, a primary counts once.
 * */
int NO_REPEATS = 1 << 5;

/** The restrictions it was built with, an OR of the constants above. */
int getRestrictions();

@Override
default int getArityCount() { return 2; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0) || identity == getArityIdentity(1); }

default int getPrimaryArity() { return getArityIdentity(0); }
default int getComplementArity() { return getArityIdentity(1); }


boolean isConnected(int primary, int complement);

/**
 * Connects the two, once more when they already were; with {@link #NO_REPEATS}, false when they already
 * were. A refusal when either is not an occupied identity of its arity, or a restriction forbids it.
 * */
boolean ensureConnection(int primary, int complement, String comment);

/** Breaks every connection between the two. False when they were not connected; refuses a break a restriction forbids. */
boolean breakConnection(int primary, int complement, String comment);

/**
 * Moves {@code primary} to {@code complement}: every complement it has is broken and {@code complement}
 * becomes its one. False when nothing changes. Refuses what {@link #ensureConnection} and
 * {@link #breakConnection} refuse, but never for a second complement or for the primary left unconnected
 * midway, since the move is one change.
 * */
boolean setComplement(int primary, int complement, String comment);

/** As {@link #setComplement} from the other side: every primary of {@code complement} is broken and {@code primary} becomes its one. */
boolean setPrimary(int complement, int primary, String comment);


/** How many connections {@code primary} has, a repeated complement counting each time. */
int complementCount(int primary);

/** How many primaries {@code complement} is connected to, each once. */
int primaryCount(int complement);

/**
 * The complements of {@code primary}, {@link #complementCount} of them, a repeated one each time, in the
 * order the implementation states.
 * */
void copyComplementsInto(int primary, int[] dest, int offset);

/** The primaries of {@code complement}, {@link #primaryCount} of them, in the order the implementation states. */
void copyPrimariesInto(int complement, int[] dest, int offset);


/** {@link #ensureConnection}, as elements-clusters names the connector: the element is the primary, the cluster the complement. */
default boolean cluster(int element, int cluster, String comment) { return ensureConnection(element, cluster, comment); }

/** {@link #breakConnection}, as elements-clusters names it. */
default boolean uncluster(int element, int cluster, String comment) { return breakConnection(element, cluster, comment); }

/** {@link #isConnected}, as elements-clusters names it. */
default boolean isClustered(int element, int cluster) { return isConnected(element, cluster); }

/** The element's clusters, as {@link #copyComplementsInto} gives them. */
default int[] clustersOf(int element) {
	int[] clusters = new int[complementCount(element)];
	copyComplementsInto(element, clusters, 0);
	return clusters;
}

/** The cluster's elements, as {@link #copyPrimariesInto} gives them. */
default int[] elementsOf(int cluster) {
	int[] elements = new int[primaryCount(cluster)];
	copyPrimariesInto(cluster, elements, 0);
	return elements;
}

default int clusterCount(int element) { return complementCount(element); }

default int elementCount(int cluster) { return primaryCount(cluster); }

/**
 * The element's clusters become {@code clusters}, a repeat counting once, as one change: refused for what
 * the new list breaks, never for what breaks only midway. False when nothing changes.
 * */
boolean setClusters(int element, int[] clusters, String comment);

/** The cluster's elements become {@code elements}, as {@link #setClusters} sets them. */
boolean setElements(int cluster, int[] elements, String comment);


}

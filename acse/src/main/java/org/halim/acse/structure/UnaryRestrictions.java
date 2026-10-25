package org.halim.acse.structure;

/**
 * The restrictions a graph storage may take: the bits of one int, given when the storage is built and
 * fixed from then on, 0 being the most formal case. A storage takes the sets it can keep and refuses the
 * others when built, and a request that would break one is refused. The bits sit from {@code 1 << 8} up, above a
 * connector's, so a storage enforcing a graph on a binary one keeps both in one int; with no bit, a link
 * joins its two elements both ways and the same two may be linked more than once.
 * */
public final class UnaryRestrictions {

private UnaryRestrictions() { }


/** No link from an element to itself. */
public static final int NO_LOOPS = 1 << 8;

/** No path returns to where it started, so no loops either; on a directionless graph, no cycle but a link and its reverse, which makes it a forest. */
public static final int ACYCLIC = 1 << 9;

/** At most one parent an element, its one incoming; with {@link #ACYCLIC}, a tree or a forest. */
public static final int AT_MOST_ONE_INCOMING = 1 << 10;

/** At most one child an element, its one outgoing; with {@link #AT_MOST_ONE_INCOMING}, a sequence. */
public static final int AT_MOST_ONE_OUTGOING = 1 << 11;

/**
 * Element 0 is the root: it takes no parent, an element listing none hangs from it, and so the root
 * reaches every element. The root is a fallback rather than a link, so it counts against no
 * restriction and is never added or removed as a parent; an element losing its last parent falls back
 * to it.
 * */
public static final int ROOTED = 1 << 12;

/**
 * No link another path already implies - the transitive reduction, unique on an acyclic graph. A link
 * implied by the ones there is not added; what becomes of the ones a new link implies is the kind's to say.
 * */
public static final int NO_IMPLIED_LINKS = 1 << 13;

/**
 * Every element reaches every other along the arrows. An element counts from its first link, since a
 * new identity has none, and forget, which no structure refuses, can still break it. Asked by
 * {@link #reachesAll}.
 * */
public static final int FULLY_REACHABLE = 1 << 14;

/** Every two elements are joined by a path, direction ignored: no islands. Counted as {@link #FULLY_REACHABLE} counts; asked by {@link #connectsAll}. */
public static final int ALL_CONNECTED = 1 << 15;

/**
 * A link goes one way, from the parent to the child. Without it a link goes both ways, so an element's
 * parents are its children.
 * */
public static final int DIRECTED = 1 << 17;

/** No more than one link between the same two elements, the same way round on a directed graph. */
public static final int NO_PARALLEL_LINKS = 1 << 18;


}

package org.halim.acs.structure;

/** Graph restrictions as bit flags. */
public final class UnaryRestrictions {
	private UnaryRestrictions() { }

	/** No link from an element to itself. */
	public static final int NO_LOOPS = 1;
	/** No path returns to where it started. */
	public static final int ACYCLIC = 1 << 1;
	/** At most one predecessor per element. */
	public static final int AT_MOST_ONE_INCOMING = 1 << 2;
	/** At most one successor per element. */
	public static final int AT_MOST_ONE_OUTGOING = 1 << 3;
	/** Element 0 is the root: it takes no predecessor, unlinked elements hang from it. */
	public static final int ROOTED = 1 << 4;
	/** No link another path already implies (transitive reduction). */
	public static final int NO_IMPLIED_LINKS = 1 << 5;
	/** Every element reaches every other along the arrows. */
	public static final int FULLY_REACHABLE = 1 << 6;
	/** Every two elements are joined by an undirected path. */
	public static final int ALL_CONNECTED = 1 << 7;
	/** Links are directed (from predecessor to successor). */
	public static final int DIRECTED = 1 << 8;
	/** No more than one link between the same two elements. */
	public static final int NO_PARALLEL_LINKS = 1 << 9;
}

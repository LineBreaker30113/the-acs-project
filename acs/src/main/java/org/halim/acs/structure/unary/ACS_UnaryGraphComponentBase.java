package org.halim.acs.structure.unary;

import org.halim.acs.structure.ACS_GraphComponentBase;

/**
 * A graph over one arity: the edges alone, between that arity's identities. An edge carrying data is an
 * entity, an identity of an arity of its own, which makes it a node-edge structure over two arities. A
 * repeat is no such data but a count, so at 0 the same edge may join two elements more than once, and
 * {@link #NO_PARALLEL_EDGES} forbids it. {@link #addParent} joins two elements only when no edge does and
 * {@link #removeParent} removes every edge between them, as on node-edge, while {@link #addEdge} and
 * {@link #removeEdge} add and remove one.
 * <p>
 * Symmetric at 0, as every graph kind is: an edge joins its two elements both ways, and {@link #DIRECTED}
 * makes it go from the parent to the child. {@link #NO_IMPLIED_EDGES} is kept rather than refused: the
 * edges a new one implies are dropped, which loses nothing, since an edge here is only the relation.
 * */
public interface ACS_UnaryGraphComponentBase extends ACS_GraphComponentBase {

/** No more than one edge between the same two elements, the same way round on a directed graph. */
int NO_PARALLEL_EDGES = 1 << 18;

@Override
default int getArityCount() { return 1; }

@Override
default boolean isRelatedArity(int identity) { return identity == getArityIdentity(0); }

default boolean allowsParallelEdges() { return (getRestrictions() & NO_PARALLEL_EDGES) == 0; }


/**
 * One more edge from {@code parent} to {@code element}, beside the ones there. False when, with
 * {@link #NO_PARALLEL_EDGES}, one already was, or, with {@link #NO_IMPLIED_EDGES}, a path implies it, the
 * edge already there among them; refuses what {@link #addParent} refuses.
 * */
boolean addEdge(int element, int parent, String comment);

/** One edge fewer from {@code parent} to {@code element}, the last one parting them. False when there was none; refuses a removal a restriction forbids. */
boolean removeEdge(int element, int parent, String comment);

/** How many edges go from {@code parent} to {@code element}, either way round on a symmetric graph. */
int edgeCount(int element, int parent);


}

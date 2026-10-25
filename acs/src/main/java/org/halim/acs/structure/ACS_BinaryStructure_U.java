package org.halim.acs.structure;

import org.halim.acs.ACS_Requestable;

/**
 * A binary structure with graph rules on its primary arity: the nodes, at index 0, are the primaries, and
 * the clusters, at index 1, the complements, each joining the nodes connected to it. The nodes form a graph
 * through the clusters, so it is both: the connector's reads tell a node's clusters and a cluster's nodes,
 * and the reads, edits and analyses of {@link ACS_UnaryStructure} hold over the nodes. A cluster is an
 * identity of its arity, so it carries data of its own. A storage keeps the connector's restrictions and the
 * graph's.
 * <p>
 * Clusters are symmetric by nature: any two nodes of a cluster are each other's parent and child, since a
 * cluster without direction is the easier one to represent. Direction is a restriction,
 * {@link #isDirected}: each node is then joined to a cluster as a from or as a to, and a node's children are
 * the tos of the clusters it is a from of; a storage of two nodes a cluster keeps it as its edges' first and
 * second nodes.
 * {@link #relateToUnary} and {@link #addParent} join two nodes through a cluster, and
 * {@link #unrelateFromUnary} and {@link #removeParent} part them; which cluster is the kind's to say.
 * <p>
 * Both contracts number their requests from 0, so the graph's are marked again here, from 13 on, after the
 * connector's; a kind of it numbers its own after them.
 * */
public interface ACS_BinaryStructure_U extends ACS_BinaryStructure, ACS_UnaryStructure {

@Override
default int getArityCount() { return ACS_BinaryStructure.super.getArityCount(); }

@Override
default boolean isRelatedArity(int identity) { return ACS_BinaryStructure.super.isRelatedArity(identity); }

default int getNodeArity() { return getArityIdentity(0); }
default int getClusterArity() { return getArityIdentity(1); }


@ACS_Requestable(13)
@Override
boolean relateToUnary(int first, int last, String comment);

@ACS_Requestable(14)
@Override
boolean unrelateFromUnary(int first, int last, String comment);

@ACS_Requestable(15)
@Override
boolean isRelatedToUnary(int first, int last);

@ACS_Requestable(16)
@Override
boolean addParent(int element, int parent, String comment);

@ACS_Requestable(17)
@Override
boolean removeParent(int element, int parent, String comment);

@ACS_Requestable(18)
@Override
boolean setParent(int element, int parent, String comment);

@ACS_Requestable(19)
@Override
boolean setLasts(int first, int[] lasts, String comment);

@ACS_Requestable(20)
@Override
boolean setFirsts(int last, int[] firsts, String comment);

@ACS_Requestable(21)
@Override
default boolean moveUnary(int source, int target, String comment) { return ACS_UnaryStructure.super.moveUnary(source, target, comment); }

@ACS_Requestable(22)
@Override
default boolean mergeUnary(int source, int target, String comment) { return ACS_UnaryStructure.super.mergeUnary(source, target, comment); }

@ACS_Requestable(23)
@Override
default int[] lastsOf(int first) { return ACS_UnaryStructure.super.lastsOf(first); }

@ACS_Requestable(24)
@Override
default int[] firstsOf(int last) { return ACS_UnaryStructure.super.firstsOf(last); }

@ACS_Requestable(25)
@Override
default boolean reaches(int from, int to) { return ACS_UnaryStructure.super.reaches(from, to); }

@ACS_Requestable(26)
@Override
default int[] ancestorsOf(int element) { return ACS_UnaryStructure.super.ancestorsOf(element); }

@ACS_Requestable(27)
@Override
default int[] descendantsOf(int element) { return ACS_UnaryStructure.super.descendantsOf(element); }

@ACS_Requestable(28)
@Override
default int depth(int element) { return ACS_UnaryStructure.super.depth(element); }

@ACS_Requestable(29)
@Override
default int[] nearestCommonAncestorsOf(int element, int other) { return ACS_UnaryStructure.super.nearestCommonAncestorsOf(element, other); }

@ACS_Requestable(30)
@Override
default int[] nearestCommonDescendantsOf(int element, int other) { return ACS_UnaryStructure.super.nearestCommonDescendantsOf(element, other); }

@ACS_Requestable(31)
@Override
default int distance(int element, int other) { return ACS_UnaryStructure.super.distance(element, other); }

@ACS_Requestable(32)
@Override
default int pathLength(int from, int to) { return ACS_UnaryStructure.super.pathLength(from, to); }

@ACS_Requestable(33)
@Override
default int[] topologicalOrderAmong(int[] elements) { return ACS_UnaryStructure.super.topologicalOrderAmong(elements); }

@ACS_Requestable(34)
@Override
default boolean hasCycleAmong(int[] elements) { return ACS_UnaryStructure.super.hasCycleAmong(elements); }

@ACS_Requestable(35)
@Override
default int[] rootsAmong(int[] elements) { return ACS_UnaryStructure.super.rootsAmong(elements); }

@ACS_Requestable(36)
@Override
default int[] leavesAmong(int[] elements) { return ACS_UnaryStructure.super.leavesAmong(elements); }

@ACS_Requestable(37)
@Override
default int[] partsAmong(int[] elements) { return ACS_UnaryStructure.super.partsAmong(elements); }

@ACS_Requestable(38)
@Override
default double connectivityAmong(int[] elements) { return ACS_UnaryStructure.super.connectivityAmong(elements); }

@ACS_Requestable(39)
@Override
default boolean reachesAllAmong(int[] elements) { return ACS_UnaryStructure.super.reachesAllAmong(elements); }

@ACS_Requestable(40)
@Override
default boolean connectsAllAmong(int[] elements) { return ACS_UnaryStructure.super.connectsAllAmong(elements); }


}

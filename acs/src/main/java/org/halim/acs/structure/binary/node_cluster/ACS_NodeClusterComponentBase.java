package org.halim.acs.structure.binary.node_cluster;

import org.halim.acs.structure.ACS_GraphComponentBase;
import org.halim.acs.structure.binary.connector.ACS_ConnectorComponentBase;

/**
 * A connector with graph rules on its primary arity: the nodes, at index 0, are the primaries, and the
 * clusters, at index 1, the complements, each joining the nodes connected to it. The nodes form a graph
 * through the clusters, so the reads, edits and analyses of {@link ACS_GraphComponentBase} hold over them,
 * while the connector's own reads tell a node's clusters and a cluster's nodes. A cluster is an identity of
 * its arity, so it carries data of its own.
 * <p>
 * Clusters are symmetric by nature: any two nodes of a cluster are each other's parent and child, since a
 * cluster without direction is the easier one to represent. Direction is a restriction, {@link #DIRECTED}:
 * each node is then joined to a cluster as a from or as a to, and a node's children are the tos of the
 * clusters it is a from of; node-edge keeps it as its edges' first and second nodes. The restrictions are
 * the connector's and the graph's in one int, their bits disjoint. {@link #addParent} and
 * {@link #removeParent} join and part two nodes through a cluster; which cluster is the kind's to say.
 * */
public interface ACS_NodeClusterComponentBase extends ACS_ConnectorComponentBase, ACS_GraphComponentBase {

default int getNodeArity() { return getArityIdentity(0); }
default int getClusterArity() { return getArityIdentity(1); }


}

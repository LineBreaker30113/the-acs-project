/**
 * Node-edge structures: a graph whose nodes are one arity and whose edges are another, so an edge carries
 * data of its own. What kind of graph it is - directed, acyclic, with loops, with parallel edges - is a set
 * of rules fixed at construction; how it is stored is the type's. Every type here:
 * <ul>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.NodeEdgeBase} -
 *       the contract: the graph's kind, connections made and broken, an edge's nodes, a node's edges.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.NodeEdgeGraphBase} - what the
 *       storages share: the rules, the edge → nodes side, and the core's part; a storage adds the
 *       node → edges side.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.InlineSlotsNodeEdgeComponent} -
 *       k slots a node in one flat array, k changeable at any time, an overflow for the nodes past it; for
 *       nodes with about k edges each.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.ArrayListsNodeEdgeComponent} -
 *       an int list a node; for edge counts that vary widely.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.SingleRoaringNodeEdgeComponent} -
 *       the node arity connected to itself in one bitmap, an edge found by its pair's rank; one edge a pair
 *       of nodes, nodes below 65535.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.PairListNodeEdgeComponent} -
 *       the edge → nodes side alone, so every node lookup walks every edge; directionless only, on its own
 *       rather than on the base.</li>
 *   <li>{@link org.halim.acse.structure.binary.node_edge.ForestNodeEdgeComponent} - a
 *       forest in a layout of its own: each node points to its edge, an int list of the node, its parent (0
 *       for none) and its children's edges; a node keeps its edge when it loses its parent, so it has methods
 *       of its own rather than the contract's.</li>
 * </ul>
 * */
package org.halim.acse.structure.binary.node_edge;

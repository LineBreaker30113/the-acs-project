/**
 * Structures: the relations between identities, the second type of component after the arities. A
 * structure knows nothing of the data on the identities it relates; it ensures its own rules, and it is
 * the relation itself, holding no data of its own. Subpackages are by how many arities a structure spans,
 * then, where one arity count has several kinds, by kind.
 * <ul>
 *   <li>{@link org.halim.acs.structure.ACS_StructureComponentBase} - the contract every
 *       structure answers to: the arities it spans, forgetting an identity, storing itself.</li>
 *   <li>{@link org.halim.acs.structure.ACS_GraphComponentBase} - what every graph shares, unary or
 *       node-edge: the restrictions, parents and children, the edits, the analyses.</li>
 *   <li>{@code unary} - structures over one arity, which are always some kind of graph.</li>
 *   <li>{@code binary.connector} - an identity of one arity connected to an identity of another, or not.</li>
 *   <li>{@code binary.node_cluster} - a connector with graph rules on its primary arity: nodes joined by
 *       clusters, symmetric unless directed.</li>
 *   <li>{@code binary.node_edge} - a graph whose nodes are one arity and whose edges are another.</li>
 *   <li>{@code binary.category} - empty so far.</li>
 *   <li>{@code ternary.primary_relation_complement} - the most formal ternary: an identity of one arity
 *       relating a few identities of a second to a few of a third.</li>
 *   <li>{@code ternary.of_by_to} - an identity of one arity matching a list of pairs, each an identity of a
 *       second with one of a third.</li>
 * </ul>
 * */
package org.halim.acs.structure;

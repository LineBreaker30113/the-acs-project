/**
 * Structures: the relations between identities, the second type of component after the arities. A
 * structure knows nothing of the data on the identities it relates; it ensures its own rules, and it is
 * the relation itself, holding no data of its own. One most formal form per arity count, every kind a
 * restriction of it; the mathematics behind them is in {@code theorems.md}. The restrictions a storage keeps are acse's. A contract is named
 * {@code ACS_<N-ary>Structure<_enforcedStructure>*}, each suffix a lower structure it enforces on some of
 * its arities, {@code U} unary, {@code B} binary, {@code T} ternary, and {@code BeU} a binary enforcing a
 * unary; a kind that cannot be named so has no contract, and its storages share an abstract class in acse.
 * <ul>
 *   <li>{@link org.halim.acs.structure.ACS_StructureBase} - the contract every structure answers to: the
 *       arities it spans and causes, forgetting an identity, storing itself.</li>
 *   <li>{@link org.halim.acs.structure.ACS_UnaryStructure} - the graph over one arity, with its reads,
 *       edits and analyses.</li>
 *   <li>{@link org.halim.acs.structure.ACS_BinaryStructure} - the connector, elements-clusters.</li>
 *   <li>{@link org.halim.acs.structure.ACS_BinaryStructure_U} - a binary structure enforcing a unary one
 *       on its primary arity: nodes joined by clusters.</li>
 *   <li>{@link org.halim.acs.structure.ACS_TernaryStructure} - primary-relation-complement: an identity of
 *       one arity relating a few identities of a second to a few of a third.</li>
 *   <li>{@link org.halim.acs.structure.ACS_QuaternaryStructure} - an identity of one arity relating triples
 *       of three others.</li>
 * </ul>
 * */
package org.halim.acs.structure;

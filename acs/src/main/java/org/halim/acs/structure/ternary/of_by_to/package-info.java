/**
 * Of-by-to structures: each identity of a by arity matches a list of pairs, an identity of an of arity with
 * one of a to arity, so a transition carries data of its own. What kind it is is a set of restrictions fixed
 * at construction; how it is stored is the type's.
 * <ul>
 *   <li>{@link org.halim.acs.structure.ternary.of_by_to.ACS_OfByToComponentBase} - the contract: the
 *       restrictions, the bys and their pairs, both sides read, and the tos an of is paired with.</li>
 * </ul>
 * */
package org.halim.acs.structure.ternary.of_by_to;

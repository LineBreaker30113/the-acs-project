/**
 * Of-by-to storages: each identity of a by arity matches a list of pairs, an identity of an of arity with one
 * of a to arity. One relation, so each type here is one way of storing it, for the kinds it takes.
 * <ul>
 *   <li>{@link org.halim.acse.structure.ternary.of_by_to.OfByToBase} - the contract: the
 *       restrictions, the bys and their pairs, both sides read.</li>
 *   <li>{@link org.halim.acse.structure.ternary.of_by_to.ArrayListsOfByToComponent} - an int list of pairs a
 *       by, and a list of bys an of and a to; every kind but the full one.</li>
 *   <li>{@link org.halim.acse.structure.ternary.of_by_to.TransitionsOfByToComponent} - one pair a by as two
 *       ints, for transitions, which leave with their of or their to.</li>
 * </ul>
 * */
package org.halim.acse.structure.ternary.of_by_to;

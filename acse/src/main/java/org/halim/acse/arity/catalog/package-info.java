/**
 * Data hanging off identities. A catalog is one value per identity - a column - and this package is
 * the contract for that plus the storage forms that satisfy it.
 * <p>
 * {@link org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase} is what every
 * catalog answers to whatever it stores: the lifecycle, the type it prefers, what one identity costs,
 * six widened accessors and their range forms. It pairs with
 * {@link ACS_ArityComponentBase}: the two are one contract
 * under two addressing schemes - a column addressed by its receiver here, a cell addressed by
 * {@code (catalog, identity)} there.
 * Four families sit under it -
 * {@link org.halim.acs.arity.catalog.IntegerValueCatalog},
 * {@link org.halim.acs.arity.catalog.FloatingValueCatalog},
 * {@link org.halim.acs.arity.catalog.BooleanValueCatalog} and
 * {@link org.halim.acs.arity.catalog.StringValueCatalog} - because a
 * {@code long}, a {@code double} and a {@code boolean getData} cannot be one generic method without
 * boxing the return, and boxing per element is the thing this engine exists to avoid; a string is an
 * object already, and its family is for its accessor and for the difference its writes are journaled as.
 * <p>
 * <b>The accessors are views, not stored values.</b> A catalog implements the ones it can answer
 * exactly and inherits the rest, and every inherited one throws. That is why they are defaults: a
 * new catalog supplies what it has and is refused on the rest for free. The two refusals are
 * different failures and are kept apart deliberately - {@code UnsupportedOperationException} means
 * this catalog cannot answer that type at all and no identity would have, while
 * {@code ArithmeticException} means it can, but this particular value does not survive the
 * conversion. Conversion is exact or it throws, and the exactness helpers live in one place so every
 * catalog fails the same way on the same value. Each is a round trip with a magnitude guard, because
 * a narrowing cast saturates rather than wrapping and a plain round trip would wave through a value
 * that never fitted.
 * <p>
 * <b>Width is a promise about contents.</b> Integral narrowing wraps, so
 * {@code IntSizedIntegerValueCatalog} exists to carry "these values fit an int" in the type rather
 * than in a comment: on it neither the int view nor the double view can refuse a value, where on the
 * wide form both are conversions that may. Floating narrowing rounds instead of wrapping, so a float
 * catalog accepts a double and rounds it, keeping about seven decimal digits of a double's sixteen -
 * scores, weights and coordinates are its case; exact sums are not.
 * <p>
 * <b>Lifecycle is the only type-free part of the contract</b>, and that is exactly why it is on the
 * base. An arity mints an identity once and every catalog attached to it has to follow, without the
 * arity learning any of their widths. Neither {@code ensureCapacity} nor {@code truncate} touches a
 * value, so neither boxes one - and a catalog that computes its values from the identity rather than
 * storing them honestly reports nothing grew and no bytes spent.
 * <p>
 * <b>Storage comes in two shapes.</b> A <em>lister</em> keeps one flat list and is the small case. An
 * <em>arrays</em> catalog keeps a list of blocks and is the large one; both live in the
 * {@code memory} subpackage, with
 * {@link org.halim.acse.arity.catalog.memory.ChunkedCatalogBase} holding the growth
 * policy once for every width and
 * {@link org.halim.acs.arity.catalog.LongChunkInt} owning where an identity
 * lands.
 * <p>
 * <b>Reading in bulk is a range copied into the caller's array</b>, not a value handed to a consumer:
 * a callback would put a call back per element at the very site the bulk form exists to empty. The
 * defaults are the per-element loop and buy nothing on their own - what they buy is the override
 * point, and every storage form here takes it. A lister copies the range in one run, and an arrays
 * catalog resolves the chunk split once per block rather than once per identity, so a caller reading
 * a column pays for chunking once over the whole run instead of on every read. Only the primitive
 * views have a range form; a bulk {@code Object} accessor is an {@code Object[]} fill, which is the
 * thing this package is arranged to avoid.
 * <p>
 * A catalog is <b>total</b>: it answers for every identity, and a stored slot never written reads as
 * zero - "" for a string - rather than as absent. Zero is a value here, not a sentinel. Which identities exist is the
 * occupied set's question and not a catalog's.
 * <p>
 * None of these is multi thread safe.
 * */
package org.halim.acse.arity.catalog;

import org.halim.acs.arity.ACS_ArityComponentBase;


/**
 * Data hanging off identities: a catalog is one value per identity - a column - and this package is the
 * contract for it. {@link ACS_ArityCatalogRowBase} and {@link ACS_ArityComponentBase} are one contract
 * under two addressing schemes - a column addressed by its receiver here, a cell addressed by
 * {@code (catalog, identity)} there.
 * <p>
 * Four families sit under it - {@link IntegerValueCatalog}, {@link FloatingValueCatalog},
 * {@link BooleanValueCatalog} and {@link StringValueCatalog} - because a {@code long}, a {@code double}
 * and a {@code boolean getData} cannot be one generic method without boxing the return, and boxing per
 * element is the thing this engine exists to avoid; a string is an object already, and its family is for
 * its accessor and for the difference its writes are journaled as. The storage forms that satisfy them
 * are acse's ({@code org.halim.acse.arity.catalog}).
 * */
package org.halim.acs.arity.catalog;

import org.halim.acs.arity.ACS_ArityComponentBase;

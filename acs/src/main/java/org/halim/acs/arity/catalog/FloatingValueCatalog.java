package org.halim.acs.arity.catalog;

import org.halim.acs.arity.ACS_ArityComponentBase;


/**
 * A catalog of floating values. {@link #getData} is the one thing an implementation supplies; the
 * views onto it follow from that and are exact or they throw.
 * <p>
 * The stored width is the implementation's business - the float variants answer the same
 * {@code getData} as the double ones, widening on the way out, and differ only in what
 * {@link #getEstimatedBytesPerIdentity} reports.
 * */
public interface FloatingValueCatalog extends ACS_ArityCatalogRowBase {

double getData(long identity);


@Override
default int getPreferredType() { return PREFERRED_FLOATING; }

@Override
default double getDoubleFor(long identity) {
	return getData(identity);
}

@Override
default long getLongFor(long identity) {
	return ACS_ArityComponentBase.exactLong(getData(identity));
}

@Override
default int getIntFor(long identity) {
	return ACS_ArityComponentBase.exactInt(getData(identity));
}

/** The reverse of {@link #getLongFor}: a long is written only when it has an exact double. */
@Override
default void setLongFor(long identity, long value) {
	setDoubleFor(identity, ACS_ArityComponentBase.exactDouble(value));
}


}

package org.halim.acse.structure;

/**
 * The restrictions a primary-relation-complement storage may take: the bits of one int, given when the storage is built and
 * fixed from then on, 0 being the most formal case. A storage takes the sets it can keep and refuses the
 * others when built, and a request that would break one is refused. The at-most bits are
 * {@code AT_MOST_ONE_X_PER_Y}, X and Y two of the primary, the relation and the complement.
 * */
public final class TernaryRestrictions {

private TernaryRestrictions() { }


/** A relation takes one primary at most. */
public static final int AT_MOST_ONE_PRIMARY_PER_RELATION = 1 << 3;

/** A relation gives one complement at most. */
public static final int AT_MOST_ONE_COMPLEMENT_PER_RELATION = 1 << 4;

/** A primary is taken by one relation at most. */
public static final int AT_MOST_ONE_RELATION_PER_PRIMARY = 1 << 5;

/** A complement is given by one relation at most. */
public static final int AT_MOST_ONE_RELATION_PER_COMPLEMENT = 1 << 7;


}

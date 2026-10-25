package org.halim.acse.structure;

/**
 * The restrictions a connector storage may take: the bits of one int, given when the storage is built and
 * fixed from then on, 0 being the most formal case. A storage takes the sets it can keep and refuses the
 * others when built, and a request that would break one is refused. With no bit, a primary's complements are unordered
 * and with repeats, any number either side.
 * */
public final class BinaryRestrictions {

private BinaryRestrictions() { }


/** A primary is connected to one complement at most: many primaries to one complement. */
public static final int AT_MOST_ONE_COMPLEMENT = 1;

/** A complement is connected to one primary at most; with {@link #AT_MOST_ONE_COMPLEMENT}, one to one. */
public static final int AT_MOST_ONE_PRIMARY = 1 << 1;

/**
 * Every primary is connected. A primary counts from its first connection, since a new identity has none,
 * and forget, which no structure refuses, can still break it.
 * */
public static final int EVERY_PRIMARY_CONNECTED = 1 << 2;

/** Every complement is connected, counted as {@link #EVERY_PRIMARY_CONNECTED} counts. */
public static final int EVERY_COMPLEMENT_CONNECTED = 1 << 3;

/**
 * A primary is connected to a complement once at most. Without it, each connection to the same complement
 * is one of its own; from the complement's side, a primary counts once.
 * */
public static final int NO_REPEATS = 1 << 5;


}

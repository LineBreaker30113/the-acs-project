package org.halim.acs.structure;

/** Connector restrictions as bit flags. */
public final class BinaryRestrictions {
	private BinaryRestrictions() { }

	public static final int AT_MOST_ONE_COMPLEMENT = 1;
	public static final int AT_MOST_ONE_PRIMARY = 1 << 1;
	public static final int EVERY_PRIMARY_CONNECTED = 1 << 2;
	public static final int EVERY_COMPLEMENT_CONNECTED = 1 << 3;
	public static final int NO_REPEATS = 1 << 5;
}

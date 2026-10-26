package org.halim.acs.structure;

import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Requestable;

import java.io.DataOutputStream;
import java.io.IOException;

/** Base contract for structures. */
public interface ACS_StructureBase extends ACS_Actor {

	@ACS_Requestable(0)
	int getArityCount();

	@ACS_Requestable(1)
	int getArityIdentity(int index);

	@ACS_Requestable(2)
	boolean isRelatedArity(int identity);

	@ACS_Requestable(3)
	boolean isCaused(int index);

	@ACS_Requestable(4)
	int getRestrictions();

	default boolean hasRestriction(int bit) { return (getRestrictions() & bit) != 0; }

	boolean saveTo(DataOutputStream dos) throws IOException;

	boolean forget(int arity, int identity);
}

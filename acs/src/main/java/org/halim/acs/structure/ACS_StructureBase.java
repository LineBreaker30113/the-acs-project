package org.halim.acs.structure;


import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What every structure answers to: the arities it spans, the arities it causes, forgetting an identity,
 * storing itself. A structure is the connections alone, and a connection that
 * matters is an identity of an arity, the structure's units, as a cluster joining nodes or a
 * relation pairing primaries with complements.
 * <p>
 * A structure may cause the arity of its units. One the client made before the structure is caused but not
 * strictly: the client captures there too, beside the structure, so its identities cannot be calculated.
 * One the structure made at its creation, which configures it, is strictly caused: the structure is
 * responsible for it, and every identity there is calculated or managed by the structure. Making and
 * managing an arity by hand is often the better practice.
 * <p>
 * The requests are marked on the contracts, so every implementation of one shares its codes; a kind's
 * codes run on from the ones of the contract it extends.
 * */
public interface ACS_StructureBase extends ACS_Actor {

int getArityCount();
int getArityIdentity(int index);
boolean isRelatedArity(int identity);

/**
 * Whether the spanned arity at {@code index} is strictly caused by this structure, so the core refuses a
 * client's capture there. An arity caused but not strictly answers false.
 * */
boolean isCaused(int index);

/**
 * Writes the arity slots it spans and the relation, so that the class's {@code public static
 * loadFrom(ACS_Core, ACS_Journal, DataInputStream)} rebuilds the same structure, attached to the core,
 * outside the journal. One file per structure.
 * */
boolean saveTo(DataOutputStream dos) throws IOException;

/**
 * Records its steps, so it undoes with the request that called it. To remove an identity of another
 * arity, call {@link ACS_Core#delegateRemoval} from here: that is the recursive step.
 * */
boolean forget(int arity, int identity);


}

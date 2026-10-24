package org.halim.acs.structure;


import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What every structure answers to: the arities it spans, forgetting an identity, storing itself. A
 * structure is the connections alone, and a connection that matters is an identity of an arity, the
 * structure's units, as node-edge's edges or of-by-to's bys.
 * <p>
 * A structure may cause the arity of its units. One the client made before the structure is caused but not
 * strictly: the client captures there too, beside the structure, so its identities cannot be calculated.
 * One the structure made at its creation, which configures it, is strictly caused: the structure is
 * responsible for it, and every identity there is calculated or managed by the structure. Making and
 * managing an arity by hand is often the better practice.
 * */
public interface ACS_StructureComponentBase extends ACS_Actor {

int getArityCount();
int getArityIdentity(int index);
boolean isRelatedArity(int identity);

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

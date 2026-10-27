package org.halim.acs.procedure;


import org.halim.acs.database.ACS_Actor;
import org.halim.acs.database.ACS_Requestable;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A behavior that reads some data and changes some data, held by the core beside the arities and the
 * structures. Its hyperparameters are fixed at build: {@link #saveTo} writes them, and the class's
 * {@code public static loadFrom(ACS_Core, ACS_Journal, DataInputStream)} reads them, since an interface
 * names no constructor. An instance is its hyperparameters; a run takes nothing more, as what it works on
 * is already in the arities.
 * <p>
 * It describes itself so it can be reasoned about without running it: the requests it would send, reads and
 * effects asked apart, at three depths. The range follows from the hyperparameters alone, so it is fixed per
 * instance; the possible and the certain ones follow from the data as it stands when asked, and hold until
 * the next write, so asked and run inside one request they agree. Certain within possible within range. A
 * procedure that cannot tell still answers: no certain ones, its range as the possible ones, and whole
 * arities as its range.
 * <p>
 * Each answer is a count, then that many entries. An entry is the actor's type and index as a request's
 * frame addresses it (an arity row is 9, a structure 2), the request's code, then the imv it touches as an
 * array, in the type the request takes its identities in. The imv, not the iui, since it is stable.
 * */
public interface ACS_ProcedureComponentBase extends ACS_Actor {

/**
 * Writes the hyperparameters, so that the class's {@code public static loadFrom(ACS_Core, ACS_Journal,
 * DataInputStream)} rebuilds the same procedure, attached to the core, outside the journal. One file per
 * procedure.
 * */
boolean saveTo(DataOutputStream dos) throws IOException;

/** Runs once on the data as it stands. Records its steps, so it undoes with the request that called it. */
@ACS_Requestable(0)
void execute(String comment);

/** The reads any run may send, from the hyperparameters alone. */
void writeRangeReads(DataOutputStream dos) throws IOException;

/** The changes any run may send, from the hyperparameters alone. */
void writeRangeEffects(DataOutputStream dos) throws IOException;

/** The reads a run now may send, within the range: some are decided only while it runs. */
void writePossibleReads(DataOutputStream dos) throws IOException;

/** The changes a run now may send, within the range. */
void writePossibleEffects(DataOutputStream dos) throws IOException;

/** The reads a run now sends whatever it decides while running, within the possible ones. */
void writeCertainReads(DataOutputStream dos) throws IOException;

/** The changes a run now sends whatever it decides while running, within the possible ones. */
void writeCertainEffects(DataOutputStream dos) throws IOException;


}

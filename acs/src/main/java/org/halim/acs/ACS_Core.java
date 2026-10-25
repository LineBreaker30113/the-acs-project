package org.halim.acs;

import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.structure.ACS_StructureBase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public interface ACS_Core extends ACS_Actor {

int getArityCount();

ACS_ArityComponentBase getArity(int arity);

/** Adds a new "Composable Catalog Arity."*/
void addArity(String description, String comment);

void addArity(ACS_ArityComponentBase acseArityComponent, String description, String comment);

/** Delegated to the arity, so only an {@link org.halim.acse.arity.ACSE_ComposableArityCatalog} takes one; returns the row's slot. */
int addCatalogRow(int arity, ACS_ArityCatalogRowBase row, String description, String comment);

int getStructureCount();
ACS_StructureBase getStructure(int structure);

void addStructure(ACS_StructureBase acseArityComponent, String description, String comment);

/** The slot stays empty; later slots do not shift. */
void removeArity(int arity, String comment);
/** The slot stays empty; later slots do not shift. */
void removeStructure(int structure, String comment);

/** Removes the identity from the arity, then every related structure forgets it. Does nothing if the identity is absent, which ends the recursion. */
void delegateRemoval(int arity, int instance, String comment);

/** Takes the arity's lowest free identity, after the structures forget anything left on it. */
long captureIdentity(int arity, String comment);

/** Takes {@code identity} itself, after the structures forget anything left on it: for a structure whose identities are calculated, not counted. Refuses one the arity already holds. */
long captureIdentity(int arity, long identity, String comment);

/** The journal recording this core's mutations, so a client can undo, redo, inspect a former state and ask where it stands. */
ACS_Journal getJournal();
/**
 * Writes every component, every description, the journal's log and the classes they need into
 * {@code folder}, between requests. A component or catalog row whose class has no static loadFrom
 * likely does not need to persist: it is left out, loads back empty, and is named in the warnings this
 * gives back.
 * */
List<String> store(Path folder) throws IOException;

/**
 * Fills this core, which must hold no component, from what {@link #store} wrote into {@code folder},
 * outside the journal, so undo history begins here. The folder's classes are found through this core's
 * own class loader, so a folder carrying its classes is loaded through
 * {@link ACS_PersistenceManager#loadCore}, which builds the core, and the journal the folder names, from
 * them. A failed load leaves the core as it was.
 * */
void load(Path folder) throws IOException;


}

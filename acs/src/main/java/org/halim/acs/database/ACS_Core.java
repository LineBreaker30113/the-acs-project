package org.halim.acs.database;

import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acs.structure.ACS_StructureBase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public interface ACS_Core extends ACS_Actor {

int getArityCount();

ACS_ArityComponentBase getArity(int arity);

/** Adds a new "Composable Catalog Arity."*/
@ACS_Requestable(0)
void addArity(String description, String comment);

void addArity(ACS_ArityComponentBase acseArityComponent, String description, String comment);

/** Delegated to the arity, so only an {@code ACSE_ComposableArityCatalog} takes one; returns the row's slot. */
int addCatalogRow(int arity, ACS_ArityCatalogRowBase row, String description, String comment);

/** Adds a catalog row by its full class name, built by reflection through the core's class loader; returns the row's slot. */
@ACS_Requestable(1)
int addCatalogRow(int arity, String className, String description, String comment);

int getStructureCount();
ACS_StructureBase getStructure(int structure);

void addStructure(ACS_StructureBase acseArityComponent, String description, String comment);

/** Adds a structure whose class and settings are read from the request's stream; returns the structure's slot. */
@ACS_Requestable(7)
int addStructure(String description, String comment);

/** The slot stays empty; later slots do not shift. */
@ACS_Requestable(2)
void removeArity(int arity, String comment);
/** The slot stays empty; later slots do not shift. */
@ACS_Requestable(3)
void removeStructure(int structure, String comment);

/** Removes the identity from the arity, then every related structure forgets it. Does nothing if the identity is absent, which ends the recursion. */
@ACS_Requestable(4)
void delegateRemoval(int arity, int instance, String comment);

/** Takes the arity's lowest free identity, after the structures forget anything left on it. */
@ACS_Requestable(5)
long captureIdentity(int arity, String comment);

/** Takes {@code identity} itself, after the structures forget anything left on it: for a structure whose identities are calculated, not counted. Refuses one the arity already holds. */
@ACS_Requestable(6)
long captureIdentity(int arity, long identity, String comment);

/** The description manager holding component and class descriptions. */
ACS_DescriptionManager getDescriptionManager();

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

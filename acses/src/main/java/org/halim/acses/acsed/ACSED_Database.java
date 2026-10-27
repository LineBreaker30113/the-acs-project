package org.halim.acses.acsed;

import org.halim.acs.database.ACS_Core;
import org.halim.acs.database.ACS_DescriptionManager;
import org.halim.acs.database.ACS_Journal;
import org.halim.acs.database.ACS_PersistenceManager;
import org.halim.acs.database.ACS_Requestable;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acs.structure.ACS_StructureBase;
import org.halim.acses.acsess.ACSED_Request;
import org.halim.acses.acsess.ACSED_Request.Answer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;


/**
 * One database acsed holds: its index, which acsess addresses it by, its name, its folder, the manager that
 * stores it there, and its core. Its commands, under the database's acsed type, are the methods marked here,
 * a switch on the code like an actor's request but outside ACS_Actor, since acsed is not journaled.
 * */
class ACSED_Database {

final ACSED_Commands commands;
final int index;
final String name;
final Path folder;
final ACS_PersistenceManager manager;
final ACS_Core core;
/** The timeout of the inspection standing, in milliseconds, 0 for none or no inspection, and the System.nanoTime it ends at. */
long inspectionTimeout, inspectionUntil;

ACSED_Database(ACSED_Commands commands, int index, String name, Path folder, ACS_PersistenceManager manager, ACS_Core core) {
	this.commands = commands;
	this.index = index;
	this.name = name;
	this.folder = folder;
	this.manager = manager;
	this.core = core;
}


/** The command the parameters name, by code in the order of the marks; null for a wait the daemon now holds. acquire, code 4, and waitInspectionReturn, code 8, take a timeout. */
Answer run(DataInputStream parameters) throws IOException {
	int code = parameters.readInt();
	if(code != 4 && code != 8) { commands.untimed(); }
	return switch(code) {
		case 0 -> {
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, true, false);
			yield commands.done(components(comment));
		}
		case 1 -> {
			String toPath = ACSED_Request.readString(parameters);
			boolean ignoreInspection = parameters.readBoolean();
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, false, false);
			yield commands.done(storeDB(toPath, ignoreInspection, comment));
		}
		case 2 -> {
			String path = ACSED_Request.readString(parameters), newName = ACSED_Request.readString(parameters);
			boolean withFormerVersions = parameters.readBoolean(), withOtherFiles = parameters.readBoolean(), deleteOld = parameters.readBoolean(), ignoreInspection = parameters.readBoolean();
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, false, false);
			yield commands.done(migrate(path, newName, withFormerVersions, withOtherFiles, deleteOld, ignoreInspection, comment));
		}
		case 3 -> {
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, false, false);
			yield commands.done(unloadDB(comment));
		}
		case 4 -> {
			boolean changesOnly = parameters.readBoolean();
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, false, false);
			yield commands.done(acquire(changesOnly, comment));
		}
		case 5 -> {
			boolean force = parameters.readBoolean();
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, false, force);
			yield commands.done(release(force, comment));
		}
		case 6 -> {
			int type = parameters.readInt(), at = parameters.readInt();
			commands.ready(parameters, this, true, false);
			int place = probe(type, at);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			new DataOutputStream(bytes).writeInt(place);
			commands.outcome = "done: place " + place;
			yield new Answer(ACSED_Request.DONE, bytes.toByteArray());
		}
		case 7 -> {
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, true, false);
			yield commands.done(inspectionState(comment));
		}
		case 8 -> {
			String comment = commands.noted(ACSED_Request.readString(parameters));
			commands.ready(parameters, this, true, false);
			String answer = waitInspectionReturn(comment);
			yield answer == null ? null : commands.done(answer);
		}
		default -> throw new IllegalArgumentException("a database's acsed type has no command " + code);
	};
}


/**
 * One line per arity - slot, class, description, identity count - with its catalog rows indented under
 * it - slot, class, description - then one line per structure - slot, class, description, the arities
 * it spans. The fields are tab-separated, "-" stands for no description, and an arity that keeps no row
 * objects shows its column's type where a row's class would be.
 * */
@ACS_Requestable(0)
public String components(String comment) {
	ACS_DescriptionManager descriptions = core.getDescriptionManager();
	StringBuilder lines = new StringBuilder();
	for(int slot = 0; slot < core.getArityCount(); slot++) {
		ACS_ArityComponentBase arity = core.getArity(slot);
		if(arity == null) {
			ACSED_Commands.line(lines, "arity " + slot, "(removed)");
			continue;
		}
		long identities = arity.occupied().size();
		ACSED_Commands.line(lines, "arity " + slot, arity.getClass().getName(), descriptions == null ? null : descriptions.getArityDescription(slot), identities + (identities == 1 ? " identity" : " identities"));
		for(int row = 0; row < arity.catalogCount(); row++) {
			String kind = rowKind(arity, row);
			if(kind == null) { ACSED_Commands.line(lines, "\tcatalog " + row, "(removed)"); }
			else { ACSED_Commands.line(lines, "\tcatalog " + row, kind, descriptions == null ? null : descriptions.getArityCatalogRowDescription(slot, row)); }
		}
	}
	for(int slot = 0; slot < core.getStructureCount(); slot++) {
		ACS_StructureBase structure = core.getStructure(slot);
		if(structure == null) {
			ACSED_Commands.line(lines, "structure " + slot, "(removed)");
			continue;
		}
		StringBuilder spans = new StringBuilder("arities");
		for(int i = 0; i < structure.getArityCount(); i++) { spans.append(' ').append(structure.getArityIdentity(i)); }
		ACSED_Commands.line(lines, "structure " + slot, structure.getClass().getName(), descriptions == null ? null : descriptions.getStructureDescription(slot), spans.toString());
	}
	return lines.isEmpty() ? "no components" : lines.toString();
}

/** The row's class; the column's type for an arity that keeps no row objects; null for a removed row. */
static String rowKind(ACS_ArityComponentBase arity, int row) {
	ACS_ArityCatalogRowBase catalog;
	try { catalog = arity.getCatalogRow(row); }
	catch(UnsupportedOperationException e) { return ACS_ArityComponentBase.typeName(arity.getPreferredType(row)); }
	return catalog == null ? null : catalog.getClass().getName();
}

/**
 * Stores into the database's folder; during an inspection, only with {@code +ignoreInspection}, since
 * the store would be of the inspected state. {@code toPath=} stores a copy into another folder, absent or
 * empty, made as createDB makes one, and is never refused for an inspection: the database stays on its own
 * folder. A copy carries no acse-language.txt, which its first load writes.
 * */
@ACS_Requestable(1)
public String storeDB(String toPath, boolean ignoreInspection, String comment) throws IOException {
	if(!toPath.isEmpty()) {
		Path target = ACSED_Commands.absolute(toPath);
		ACSED_Commands.makeEmptyFolder(target);
		ACS_PersistenceManager copy = ACSED_Commands.newManager(target);
		try {
			List<String> warnings = copy.store(core);
			return ACSED_Commands.withLines("stored " + name + " as " + copy.current() + ", a copy", warnings);
		} finally { close(copy); }
	}
	if(core.getJournal().isInspecting() && !ignoreInspection) { throw new IllegalStateException(ACSED_Commands.underInspection(name, "storeDB") + "; storeDB +ignoreInspection stores it, toPath= stores it elsewhere"); }
	List<String> warnings = manager.store(core);
	return ACSED_Commands.withLines("stored " + name + " as " + manager.current(), warnings);
}

/**
 * Moves the database to another folder, absent or empty and outside its own. Without
 * {@code +WithFormerVersions} the new folder is made as {@code storeDB toPath=} makes one; with it, the
 * pointer, the manager's jar and the stored versions are copied first, then a store is made through the
 * copied manager. {@code +WithOtherFiles} also copies every other entry at the root, folders whole; the
 * acse-language.txt is never copied, since the load back writes the new folder's. The new folder is then
 * loaded back, and that core is the database from here on, at the same index, under {@code newName=} if
 * given: its undo history begins there. {@code +deleteOld} deletes the old folder only after the load
 * back, and is refused while another name holds it. A failure before the load back leaves the database as
 * it was and what was written into the new folder.
 * */
@ACS_Requestable(2)
public String migrate(String path, String newName, boolean withFormerVersions, boolean withOtherFiles, boolean deleteOld, boolean ignoreInspection, String comment) throws IOException {
	Path target = ACSED_Commands.absolute(path);
	String renamed = newName.isEmpty() || newName.equals(name) ? name : commands.nameFor(newName, target);
	if(core.getJournal().isInspecting() && !ignoreInspection) { throw new IllegalStateException(ACSED_Commands.underInspection(name, "migrate") + "; migrate +ignoreInspection moves it"); }
	String other = commands.holderOf(folder, this);
	if(other != null && deleteOld) { throw new IllegalStateException(other + " holds " + folder + " too, so +deleteOld would delete its folder"); }
	if(target.startsWith(folder)) { throw new IllegalArgumentException(target + " is inside " + folder); }
	ACSED_Commands.makeEmptyFolder(target);
	try(Stream<Path> entries = Files.list(folder)) {
		for(Path entry : (Iterable<Path>) entries::iterator) {
			if(ACSED_Commands.isLanguage(entry)) { continue; }
			if(ACSED_Commands.isRecognized(entry) ? withFormerVersions : withOtherFiles) { ACSED_Commands.copyTree(entry, target.resolve(entry.getFileName())); }
		}
	}
	ACS_PersistenceManager moved = withFormerVersions ? ACS_PersistenceManager.open(target) : ACSED_Commands.newManager(target);
	List<String> warnings;
	ACSED_Database replacement;
	ACSED_Commands.Language language;
	try {
		warnings = moved.store(core);
		replacement = new ACSED_Database(commands, index, renamed, target, moved, moved.load());
		language = ACSED_Commands.Language.of(target, replacement.core);
	} catch(IOException | RuntimeException | LinkageError e) {
		close(moved);
		throw e;
	}
	commands.databases.remove(name);
	commands.databases.put(renamed, replacement);
	commands.indexed.set(index, replacement);
	if(other == null) { commands.languages.remove(folder); }
	commands.languages.put(target, language);
	commands.writeDatabases();
	commands.answerWaits(this, commands.refused(name + " was migrated to " + target + " before an endInspection"));
	String old;
	try {
		close();
		if(deleteOld) {
			ACSED_Commands.deleteTree(folder);
			old = "deleted the old folder " + folder;
		} else { old = "the old folder " + folder + " is kept"; }
	} catch(IOException e) { old = "cannot let go of or delete the old folder " + folder + ": " + ACSED_Commands.reason(e); }
	return ACSED_Commands.withLines("migrated " + name + " to " + moved.current() + " as " + renamed + "; " + old, warnings);
}

/** Forgets the database without storing it, lets go of its jars, and refuses the waits held for it; its index is not given again. */
@ACS_Requestable(3)
public String unloadDB(String comment) throws IOException {
	commands.remove(this);
	commands.acquisitions.remove(index);
	commands.answerWaits(this, commands.refused(name + " was unloaded before an endInspection"));
	close();
	return "unloaded " + name + ", not stored";
}

/** Acquires the database, as acquire under acsed acquires the daemon: from here on every frame addressing it must carry the key it answers, until it is released or its timeout passes. */
@ACS_Requestable(4)
public String acquire(boolean changesOnly, String comment) {
	if(commands.daemonAcquisition != null) { throw new IllegalStateException("the daemon is acquired already"); }
	if(commands.acquisitions.containsKey(index)) { throw new IllegalStateException(name + " is acquired already"); }
	ACSED_Commands.Acquisition acquisition = ACSED_Commands.Acquisition.of(commands.newKey(), changesOnly, commands.timeoutFor("acquire"));
	commands.acquisitions.put(index, acquisition);
	return acquisition.key();
}

/** Ends the database's acquisition, with its key or +force. */
@ACS_Requestable(5)
public String release(boolean force, String comment) {
	if(commands.acquisitions.remove(index) == null) { throw new IllegalStateException(name + " is not acquired"); }
	return "released " + name + (force ? ", without its key" : "");
}

/** The place of the actor's class in the folder's acse-language.txt, its section appended first when the class is new there. */
@ACS_Requestable(6)
public int probe(int type, int index) throws IOException {
	return commands.languages.get(folder).place(ACSED_Commands.actorOf(core, type, index));
}

/** Whether the database is under inspection, its current activity index and its pending count, at once. */
@ACS_Requestable(7)
public String inspectionState(String comment) {
	return name + (core.getJournal().isInspecting() ? " is under inspection: " : " is not under inspection: ") + ACSED_Commands.position(core);
}

/**
 * During an inspection, gives back null, and the daemon holds the client until a journal request or the
 * inspection's timeout ends the inspection, or the wait's own timeout, unloadDB, migrate or shutdown
 * refuses it; holding takes no thread, so other connections go on meanwhile. Without an inspection, the
 * return has happened, and it answers at once. In a connection of several frames it cannot hold, so it is
 * refused there during an inspection.
 * */
@ACS_Requestable(8)
public String waitInspectionReturn(String comment) {
	long timeout = commands.timeoutFor("waitInspectionReturn");
	ACS_Journal journal = core.getJournal();
	if(!journal.isInspecting()) { return name + " is not under inspection: " + ACSED_Commands.position(core); }
	if(commands.several) { throw new IllegalStateException(name + " is under inspection, and a wait among several frames cannot hold"); }
	commands.holding = this;
	commands.holdingTimeout = timeout;
	return null;
}


/**
 * Closes the loaders the folder's jars were opened through, the manager's and the core's, so the jars
 * are let go; the program's own loader, which a created database's core comes from, is left alone.
 * Nothing may use the database afterwards.
 * */
void close() throws IOException {
	try { close(manager); }
	finally { close(core); }
}

static void close(Object built) throws IOException {
	if(built.getClass().getClassLoader() instanceof ACS_PersistenceManager.StoredClassLoader loader) { loader.close(); }
}

}

package org.halim.acse;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.halim.acs.ACS_Actor;
import org.halim.acs.ACS_Core;
import org.halim.acs.persistence.ACS_DescriptionManager;
import org.halim.acs.ACS_Journal;
import org.halim.acs.persistence.ACS_PersistenceManager;
import org.halim.acs.ACS_Requestable;
import org.halim.acs.arity.ACS_ArityComponentBase;
import org.halim.acse.arity.ACSE_ComposableArityCatalog;
import org.halim.acs.arity.catalog.ACS_ArityCatalogRowBase;
import org.halim.acse.arity.sets.IdentitiesSetNegativeInt;
import org.halim.acs.structure.ACS_StructureBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;


/** Components by slot, with their addition and removal journaled. Nothing here is multi thread safe. */
public class ACSE_CoreBasic implements ACS_Core {

/** Built for this core by the factory a constructor is given, so its class is the caller's choice; a load's is the one the folder's indexer names. */
public final ACS_Journal journal;
final ArrayList<ACS_ArityComponentBase> arities = new ArrayList<>();
final ArrayList<ACS_StructureBase> structures = new ArrayList<>();
final int[] params = new int[2];
/**
 * The database's root, the folder this core's stores go into: the parent of the first folder it stores
 * into or loads from, so a copy stored elsewhere does not move it. A row made by name keeps its file here,
 * and a store keeps a file under it relative to it. Null until then.
 * */
Path root;

/** The descriptions, held as their own actor per acs 95. */
final ACSE_DescriptionManagerBasic descriptions;

static final byte ARITY = 1, STRUCTURE = 2;

/** The descriptions, and the journal's log, which is written for reading and never loaded; the indexer is the contract's. */
static final String INDEXER = ACS_PersistenceManager.INDEXER, DESCRIPTIONS = "core.bin", JOURNAL_LOG = "journal.jsonl";


/** A core recording into the basic journal. */
public ACSE_CoreBasic() { this(ACSE_JournalBasic::new); }

/**
 * A core recording into the journal {@code journal} builds for it. The journal comes as a factory
 * rather than finished, because it holds the core it records for; {@link ACS_PersistenceManager#loadCore}
 * passes one that builds the class a folder's indexer names.
 * */
public ACSE_CoreBasic(Function<ACS_Core, ACS_Journal> journal) {
	this.journal = journal.apply(this);
	this.descriptions = new ACSE_DescriptionManagerBasic(this);
}


@Override
public int getArityCount() { return arities.size(); }

@Override
public ACS_ArityComponentBase getArity(int arity) { return arities.get(arity); }

@Override
public ACS_Journal getJournal() { return journal; }

@Override
public ACS_DescriptionManager getDescriptionManager() { return descriptions; }

@Override
public int getStructureCount() { return structures.size(); }

@Override
public ACS_StructureBase getStructure(int structure) { return structures.get(structure); }


@ACS_Requestable(0)
@Override
public void addArity(String description, String comment) { addArity(new ACSE_ComposableArityCatalog(new IdentitiesSetNegativeInt(-1), journal), description, comment); }

@Override
public void addArity(ACS_ArityComponentBase acseArityComponent, String description, String comment) { addComponent(arities, acseArityComponent, descriptions.arityDescriptions, description, ARITY, "addArity", comment); }

@Override
public int addCatalogRow(int arity, ACS_ArityCatalogRowBase row, String description, String comment) {
	int catalog = composable(arity).addCatalogRow(row, comment);
	descriptions.catalogRowDescriptions.put(descriptions.rowKey(arity, catalog), description);
	return catalog;
}

/**
 * Builds the row by its full class name, through this core's own class loader, so a loaded database finds
 * the row classes its folder carries, by the first public constructor it has of three: one taking
 * {@code (ACS_Core core, int arity, int row)}, the arity and the slot the row will sit at, for a row that
 * finds what it needs in the arity itself, as a file content row finds its paths; one taking a {@link Path},
 * which gets {@code arity-<k>-row-<j>.bin} at the database's root, which every store copies into its folder
 * and a load copies back; one taking nothing. A row needing anything else is refused, and so is a file row before the core has a
 * root, and any row during an inspection, all before anything is built, so a refusal leaves no file behind.
 * */
@ACS_Requestable(1)
public int addCatalogRow(int arity, String className, String description, String comment) {
	ACS_ArityComponentBase target = composable(arity);
	if(journal.isInspecting()) { throw new IllegalStateException("an inspection stands; call endInspection before recording"); }
	Class<?> rowClass;
	try { rowClass = Class.forName(className, false, getClass().getClassLoader()); }
	catch(ClassNotFoundException e) { throw new IllegalArgumentException("no class " + className + " is on this database's class path"); }
	if(!ACS_ArityCatalogRowBase.class.isAssignableFrom(rowClass)) { throw new IllegalArgumentException(className + " is no catalog row"); }
	Constructor<?> inArity = constructorOf(rowClass, ACS_Core.class, int.class, int.class), byFile = constructorOf(rowClass, Path.class), plain = constructorOf(rowClass);
	if(inArity == null && byFile == null && plain == null) { throw new IllegalArgumentException(className + " takes none of (ACS_Core, int, int), a Path or nothing, so it cannot be built by name"); }
	if(inArity == null && byFile != null && root == null) { throw new IllegalStateException("this core has no database root yet: a file row is made after its first store or load"); }
	int slot = target.catalogCount();
	ACS_ArityCatalogRowBase row;
	try { row = (ACS_ArityCatalogRowBase) (inArity != null ? inArity.newInstance(this, arity, slot) : byFile != null ? byFile.newInstance(root.resolve(rowFile(arity, slot))) : plain.newInstance()); }
	catch(InvocationTargetException e) {
		if(e.getCause() instanceof RuntimeException failure) { throw failure; }
		if(e.getCause() instanceof IOException failure) { throw new UncheckedIOException(failure.getMessage(), failure); }
		throw new IllegalStateException("cannot build " + className + ": " + e.getCause(), e.getCause());
	} catch(ReflectiveOperationException e) { throw new IllegalArgumentException("cannot build " + className + ": " + e, e); }
	return addCatalogRow(arity, row, description, comment);
}

/** The arity, refused unless it is a composable one, the only kind that takes catalog rows. */
ACSE_ComposableArityCatalog composable(int arity) {
	ACS_ArityComponentBase target = arities.get(arity);
	if(!(target instanceof ACSE_ComposableArityCatalog composable)) {
		throw new UnsupportedOperationException("arity " + arity + " is " + (target == null ? "removed" : target.getClass().getSimpleName() + ", which takes no catalog rows"));
	}
	return composable;
}

/** The public constructor taking exactly {@code parameters}, or null. */
static Constructor<?> constructorOf(Class<?> type, Class<?>... parameters) {
	try { return type.getConstructor(parameters); }
	catch(NoSuchMethodException e) { return null; }
}

/**
 * The request's line: a structure is added as an arity is, by a description and a comment, while what it is
 * follows them on the stream, which no line can carry - the class's full name, then the settings its class
 * reads. A call without the stream is refused; the request reaches {@link #addStructure(DataInputStream,
 * DataOutputStream, String, String)}.
 * */
@ACS_Requestable(7)
public int addStructure(String description, String comment) {
	throw new IllegalArgumentException("addStructure reads the structure's class name and settings from the request's stream");
}

/**
 * Builds the structure named first on {@code parameters} by its full class name, through this core's own
 * class loader, so a loaded database finds the structure classes its folder carries, by its public
 * constructor taking {@code (ACS_Core, ACS_Journal, DataInputStream, DataOutputStream)}: the request's own
 * streams, so the class reads its settings and writes what it gives back. Refused during an inspection, and
 * for a class that is no structure or lacks that constructor, before anything is built. Answers the slot.
 * */
public int addStructure(DataInputStream parameters, DataOutputStream returns, String description, String comment) throws IOException {
	if(journal.isInspecting()) { throw new IllegalStateException("an inspection stands; call endInspection before recording"); }
	String className = ACS_Actor.readString(parameters);
	Class<?> structureClass;
	try { structureClass = Class.forName(className, false, getClass().getClassLoader()); }
	catch(ClassNotFoundException e) { throw new IllegalArgumentException("no class " + className + " is on this database's class path"); }
	if(!ACS_StructureBase.class.isAssignableFrom(structureClass)) { throw new IllegalArgumentException(className + " is no structure"); }
	Constructor<?> byStream = constructorOf(structureClass, ACS_Core.class, ACS_Journal.class, DataInputStream.class, DataOutputStream.class);
	if(byStream == null) { throw new IllegalArgumentException(className + " takes no (ACS_Core, ACS_Journal, DataInputStream, DataOutputStream), so it cannot be built by name"); }
	ACS_StructureBase structure;
	try { structure = (ACS_StructureBase) byStream.newInstance(this, journal, parameters, returns); }
	catch(InvocationTargetException e) {
		if(e.getCause() instanceof RuntimeException failure) { throw failure; }
		if(e.getCause() instanceof IOException failure) { throw failure; }
		throw new IllegalStateException("cannot build " + className + ": " + e.getCause(), e.getCause());
	} catch(ReflectiveOperationException e) { throw new IllegalArgumentException("cannot build " + className + ": " + e, e); }
	int slot = getStructureCount();
	addStructure(structure, description, comment);
	return slot;
}

@Override
public void addStructure(ACS_StructureBase acseArityComponent, String description, String comment) { addComponent(structures, acseArityComponent, descriptions.structureDescriptions, description, STRUCTURE, "addStructure", comment); }

@ACS_Requestable(2)
@Override
public void removeArity(int arity, String comment) { removeComponent(arities, arity, ARITY, "removeArity", comment); }

@ACS_Requestable(3)
@Override
public void removeStructure(int structure, String comment) { removeComponent(structures, structure, STRUCTURE, "removeStructure", comment); }

@ACS_Requestable(4)
@Override
public void delegateRemoval(int arity, int instance, String comment) {
	if(!arities.get(arity).occupied().contains(instance)) { return; }
	int request = journal.beginRecordingActivity(this, "delegateRemoval", params, comment);
	try {
		arities.get(arity).removeIf(instance, null);
		forgetEverywhere(arity, instance);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
}

@ACS_Requestable(5)
@Override
public long captureIdentity(int arity, String comment) {
	if(isCausedArity(arity)) { throw new IllegalStateException("arity " + arity + " is caused by a structure; client capture is refused"); }
	int request = journal.beginRecordingActivity(this, "captureIdentity", params, comment);
	int identity;
	try {
		ACS_ArityComponentBase target = arities.get(arity);
		identity = Math.toIntExact(target.occupied().lowestFree());
		forgetEverywhere(arity, identity);
		target.registerIf(identity, null);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return identity;
}

@ACS_Requestable(6)
@Override
public long captureIdentity(int arity, long identity, String comment) {
	if(isCausedArity(arity)) { throw new IllegalStateException("arity " + arity + " is caused by a structure; client capture is refused"); }
	ACS_ArityComponentBase target = arities.get(arity);
	int chosen = Math.toIntExact(identity);
	if(chosen < 0) { throw new IllegalArgumentException("identity " + identity + " is negative"); }
	if(target.occupied().contains(chosen)) { throw new IllegalStateException("arity " + arity + " already holds " + identity); }
	int request = journal.beginRecordingActivity(this, "captureIdentity", params, comment);
	try {
		forgetEverywhere(arity, chosen);
		target.registerIf(chosen, null);
	} catch(RuntimeException | Error e) {
		abort(request);
		throw e;
	}
	complete(request);
	return chosen;
}

private boolean isCausedArity(int arity) {
	for(ACS_StructureBase structure : structures) {
		if(structure == null) { continue; }
		for(int i = 0; i < structure.getArityCount(); i++) {
			if(structure.getArityIdentity(i) == arity && structure.isCaused(i)) { return true; }
		}
	}
	return false;
}

void forgetEverywhere(int arity, int identity) {
	for(ACS_StructureBase structure : structures) {
		if(structure != null && structure.isRelatedArity(arity)) { structure.forget(arity, identity); }
	}
}




/**
 * Writes one file per arity and per structure, the descriptions, the journal's log, the indexer naming
 * every class those files use, and the jar of those classes, then makes the folder's entries durable.
 * A component whose class has no static loadFrom is left out, its slot stored as emptied, and so is a
 * catalog row inside its arity; the warnings name each, and each structure spanning a left-out arity.
 * */
@Override
public List<String> store(Path folder) throws IOException {
	checkBetweenRequests("store");
	Files.createDirectories(folder);
	if(root == null) { root = folder.toAbsolutePath().normalize().getParent(); }
	ACSE_PersistenceManagerBasic.ClassTable table = new ACSE_PersistenceManagerBasic.ClassTable();
	table.root = root;
	// The slots' classes first, so they keep the low numbers whatever their parts name.
	IntArrayList aritySlots = slotNumbers(arities, "arity", table), structureSlots = slotNumbers(structures, "structure", table);
	for(int slot = 0; slot < arities.size(); slot++) {
		if(aritySlots.getInt(slot) < 0) { continue; }
		table.place = "arity " + slot;
		if(!ACSE_PersistenceManagerBasic.write(folder.resolve(arityFile(slot)), table, arities.get(slot)::saveTo)) { throw new IOException("arity " + slot + " refused to be stored"); }
	}
	for(int slot = 0; slot < structures.size(); slot++) {
		if(structureSlots.getInt(slot) < 0) { continue; }
		ACS_StructureBase structure = structures.get(slot);
		for(int i = 0; i < structure.getArityCount(); i++) {
			int arity = structure.getArityIdentity(i);
			if(arities.get(arity) != null && aritySlots.getInt(arity) < 0) { table.warnings.add("structure " + slot + " spans arity " + arity + ", which was left out"); }
		}
		table.place = "structure " + slot;
		if(!ACSE_PersistenceManagerBasic.write(folder.resolve(structureFile(slot)), table, structure::saveTo)) { throw new IOException("structure " + slot + " refused to be stored"); }
	}
	ACSE_PersistenceManagerBasic.write(folder.resolve(DESCRIPTIONS), table, descriptions::writeDescriptions);
	ACSE_PersistenceManagerBasic.write(folder.resolve(JOURNAL_LOG), table, dos -> { journal.storeAll(dos); return true; });
	ACSE_PersistenceManagerBasic.write(folder.resolve(INDEXER), table, dos -> writeIndexer(dos, table, aritySlots, structureSlots));
	ACSE_PersistenceManagerBasic.storeClasses(folder, table, getClass(), journal.getClass());
	ACSE_PersistenceManagerBasic.syncDirectory(folder);
	return table.warnings;
}

/** Each slot's class number: -1 for an emptied slot, and for one left out. */
static IntArrayList slotNumbers(ArrayList<?> components, String kind, ACSE_PersistenceManagerBasic.ClassTable table) throws IOException {
	IntArrayList numbers = new IntArrayList(components.size());
	for(int slot = 0; slot < components.size(); slot++) {
		Object component = components.get(slot);
		table.place = kind + " " + slot;
		numbers.add(table.keeps(component) ? table.numberOf(component.getClass()) : -1);
	}
	return numbers;
}

/**
 * Arities first, so a structure can already reach the arities it spans while it loads. An emptied slot
 * comes back empty, keeping every later slot where it was. Every class is found before any data is
 * read, so a missing one fails the load at once, naming all of them. They are found through this core's
 * own class loader: a core {@link ACS_PersistenceManager#loadCore} built from a folder's jar finds the
 * folder's classes. Nothing is journaled, so undo history begins here; a failed load puts back the
 * descriptions it found and leaves no component.
 * */
@Override
public void load(Path folder) throws IOException {
	checkBetweenRequests("load");
	if(!arities.isEmpty() || !structures.isEmpty()) {
		throw new IllegalStateException("load needs a core without components; this one has " + arities.size() + " arity and " + structures.size() + " structure slots");
	}
	ClassLoader loader = getClass().getClassLoader();
	IntArrayList aritySlots = new IntArrayList(), structureSlots = new IntArrayList();
	Class<?>[] classes = ACSE_PersistenceManagerBasic.read(folder.resolve(INDEXER), null, dis -> readIndexer(dis, folder.resolve(INDEXER), loader, aritySlots, structureSlots));
	String priorCoreDescription = descriptions.coreDescription;
	HashMap<String, String> priorArityClasses = new HashMap<>(descriptions.arityClassDescriptions);
	HashMap<String, String> priorRowClasses = new HashMap<>(descriptions.catalogRowClassDescriptions);
	HashMap<String, String> priorStructureClasses = new HashMap<>(descriptions.structureClassDescriptions);
	try {
		ACSE_PersistenceManagerBasic.read(folder.resolve(DESCRIPTIONS), classes, dis -> descriptions.readDescriptions(dis, aritySlots.size(), structureSlots.size()));
		for(int slot = 0; slot < aritySlots.size(); slot++) {
			int number = aritySlots.getInt(slot);
			arities.add(number < 0 ? null : ACSE_PersistenceManagerBasic.read(folder.resolve(arityFile(slot)), classes, dis -> ACSE_PersistenceManagerBasic.build(classes[number], ACS_ArityComponentBase.class, this, journal, dis)));
		}
		for(int slot = 0; slot < structureSlots.size(); slot++) {
			int number = structureSlots.getInt(slot);
			structures.add(number < 0 ? null : ACSE_PersistenceManagerBasic.read(folder.resolve(structureFile(slot)), classes, dis -> ACSE_PersistenceManagerBasic.build(classes[number], ACS_StructureBase.class, this, journal, dis)));
		}
		root = folder.toAbsolutePath().normalize().getParent();
	} catch(IOException | RuntimeException | Error e) {
		arities.clear();
		structures.clear();
		descriptions.arityDescriptions.clear();
		descriptions.structureDescriptions.clear();
		descriptions.catalogRowDescriptions.clear();
		descriptions.coreDescription = priorCoreDescription;
		ACSE_DescriptionManagerBasic.replace(descriptions.arityClassDescriptions, priorArityClasses);
		ACSE_DescriptionManagerBasic.replace(descriptions.catalogRowClassDescriptions, priorRowClasses);
		ACSE_DescriptionManagerBasic.replace(descriptions.structureClassDescriptions, priorStructureClasses);
		throw e;
	}
}

void checkBetweenRequests(String action) {
	if(journal.isRecordingActivity()) { throw new IllegalStateException(action + " runs between requests, and one is open"); }
}

/** The contract's header naming this core's class, its journal's class, every class the folder's files name, then each slot's class by its number, -1 for an emptied or left-out slot. */
boolean writeIndexer(DataOutputStream dos, ACSE_PersistenceManagerBasic.ClassTable table, IntArrayList aritySlots, IntArrayList structureSlots) throws IOException {
	ACS_PersistenceManager.writeHeader(dos, getClass());
	ACS_PersistenceManager.writeString(dos, journal.getClass().getName());
	ACSE_PersistenceManagerBasic.writeClasses(dos, table);
	writeSlots(dos, aritySlots);
	writeSlots(dos, structureSlots);
	return true;
}

/** Refuses a folder whose core or journal is another class: its classes would not be this core's, nor its journal this one. */
Class<?>[] readIndexer(DataInputStream dis, Path indexer, ClassLoader loader, IntArrayList aritySlots, IntArrayList structureSlots) throws IOException {
	String core = ACS_PersistenceManager.readHeader(dis, indexer);
	if(!core.equals(getClass().getName())) { throw new IOException(indexer + " belongs to the core " + core + ", not " + getClass().getName()); }
	String recorder = ACS_PersistenceManager.readString(dis);
	if(!journal.getClass().getName().equals(recorder)) { throw new IOException(indexer + " names the journal " + recorder + ", this core's is " + journal.getClass().getName()); }
	Class<?>[] classes = ACSE_PersistenceManagerBasic.readClasses(dis, loader);
	readSlots(dis, aritySlots, classes.length);
	readSlots(dis, structureSlots, classes.length);
	return classes;
}

static void writeSlots(DataOutputStream dos, IntArrayList slots) throws IOException {
	dos.writeInt(slots.size());
	for(int slot = 0; slot < slots.size(); slot++) { dos.writeInt(slots.getInt(slot)); }
}

static void readSlots(DataInputStream dis, IntArrayList slots, int classCount) throws IOException {
	for(int slot = 0, count = dis.readInt(); slot < count; slot++) {
		int number = dis.readInt();
		if(number < -1 || number >= classCount) { throw new IOException("slot " + slot + " names class " + number + ", the indexer names " + classCount); }
		slots.add(number);
	}
}

static String arityFile(int slot) { return "arity-" + slot + ".bin"; }
static String structureFile(int slot) { return "structure-" + slot + ".bin"; }
/** A row made by name keeps its values here, at the database's root; a single file holding several rows would be {@code arity-<k>-row-<j>plus.bin}. */
static String rowFile(int arity, int row) { return "arity-" + arity + "-row-" + row + ".bin"; }


<T> void addComponent(ArrayList<T> list, T component, ArrayList<String> descriptions, String description, byte pair, String methodName, String comment) {
	int request = journal.beginRecordingActivity(this, methodName, params, comment);
	list.add(component);
	descriptions.add(description);
	params[0] = list.size() - 1;
	params[1] = journal.recordObject(component);
	journal.recordActivityStep(pair, 2);
	complete(request);
}

<T> void removeComponent(ArrayList<T> list, int slot, byte pair, String methodName, String comment) {
	T removed = list.get(slot);
	if(removed == null) { throw new IllegalStateException(methodName + ": slot " + slot + " is already empty"); }
	int request = journal.beginRecordingActivity(this, methodName, params, comment);
	list.set(slot, null);
	params[0] = slot;
	params[1] = journal.recordObject(removed);
	journal.recordActivityStep((byte) -pair, 2);
	complete(request);
}

/** Code 7, {@link #addStructure(String, String)}, reads the structure's class and settings past its marked inputs, so it is taken here; the others go to {@link ACS_Actor#dispatch}. */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	if(code != 7) { return ACS_Actor.dispatch(this, code, parameters, returns); }
	try {
		String description = ACS_Actor.readString(parameters), comment = ACS_Actor.readString(parameters);
		returns.writeInt(addStructure(parameters, returns, description, comment));
	} catch(RuntimeException e) {
		ACS_Actor.writeString(returns, ACSE_JournalBasic.reason(e));
		return false;
	}
	return true;
}

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	switch(stepPair) {
		case ARITY -> put(arities, params[index], (ACS_ArityComponentBase) journal.getObject(params[index + 1]));
		case -ARITY -> arities.set(params[index], null);
		case STRUCTURE -> put(structures, params[index], (ACS_StructureBase) journal.getObject(params[index + 1]));
		case -STRUCTURE -> structures.set(params[index], null);
		default -> throw new IllegalArgumentException("no step " + stepPair);
	}
}

static <T> void put(ArrayList<T> list, int slot, T component) {
	if(slot == list.size()) { list.add(component); }
	else { list.set(slot, component); }
}

/** Aborts nested requests left open by the failure too, up to and including {@code request}. */
void abort(int request) {
	while(journal.abortRecordingActivity() != request) { }
}

void complete(int request) {
	int completed = journal.completeRecordingActivity();
	if(completed != request) { throw new IllegalStateException("began request " + request + ", completed " + completed); }
}

}

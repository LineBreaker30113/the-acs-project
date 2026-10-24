package org.halim.acse;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import org.halim.acs.structure.ACS_StructureComponentBase;

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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;


/** Components by slot, with their addition and removal journaled. Nothing here is multi thread safe. */
public class ACSE_CoreBasic implements ACS_Core, ACS_DescriptionManager {

/** Built for this core by the factory a constructor is given, so its class is the caller's choice; a load's is the one the folder's indexer names. */
public final ACS_Journal journal;
final ArrayList<ACS_ArityComponentBase> arities = new ArrayList<>();
final ArrayList<ACS_StructureComponentBase> structures = new ArrayList<>();
final int[] params = new int[2];
/**
 * The database's root, the folder this core's stores go into: the parent of the first folder it stores
 * into or loads from, so a copy stored elsewhere does not move it. A row made by name keeps its file here,
 * and a store keeps a file under it relative to it. Null until then.
 * */
Path root;

/** The descriptions are kept outside the journal: undo leaves them alone, and a removed component keeps its own. */
String coreDescription;
/** By slot, beside the components. */
final ArrayList<String> arityDescriptions = new ArrayList<>();
final ArrayList<String> structureDescriptions = new ArrayList<>();
/** The arity in the high half of the key, the row in the low half. */
final Long2ObjectOpenHashMap<String> catalogRowDescriptions = new Long2ObjectOpenHashMap<>();
/** By class name, so a description is kept and stored even while its class is not loaded. */
final HashMap<String, String> arityClassDescriptions = new HashMap<>();
final HashMap<String, String> catalogRowClassDescriptions = new HashMap<>();
final HashMap<String, String> structureClassDescriptions = new HashMap<>();

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
public ACSE_CoreBasic(Function<ACS_Core, ACS_Journal> journal) { this.journal = journal.apply(this); }


@Override
public int getArityCount() { return arities.size(); }

@Override
public ACS_ArityComponentBase getArity(int arity) { return arities.get(arity); }

@Override
public ACS_Journal getJournal() { return journal; }

@Override
public int getStructureCount() { return structures.size(); }

@Override
public ACS_StructureComponentBase getStructure(int structure) { return structures.get(structure); }


@ACS_Requestable(0)
@Override
public void addArity(String description, String comment) { addArity(new ACSE_ComposableArityCatalog(new IdentitiesSetNegativeInt(-1), journal), description, comment); }

@Override
public void addArity(ACS_ArityComponentBase acseArityComponent, String description, String comment) { addComponent(arities, acseArityComponent, arityDescriptions, description, ARITY, "addArity", comment); }

@Override
public int addCatalogRow(int arity, ACS_ArityCatalogRowBase row, String description, String comment) {
	int catalog = composable(arity).addCatalogRow(row, comment);
	catalogRowDescriptions.put(rowKey(arity, catalog), description);
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

@Override
public void addStructure(ACS_StructureComponentBase acseArityComponent, String description, String comment) { addComponent(structures, acseArityComponent, structureDescriptions, description, STRUCTURE, "addStructure", comment); }

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

void forgetEverywhere(int arity, int identity) {
	for(ACS_StructureComponentBase structure : structures) {
		if(structure != null && structure.isRelatedArity(arity)) { structure.forget(arity, identity); }
	}
}


@Override
public String getCoreDescription() { return coreDescription; }

@Override
public void setCoreDescription(String coreDescription) { this.coreDescription = coreDescription; }

@Override
public String getArityDescription(int arityIndex) { return arityDescriptions.get(arityIndex); }

@Override
public void setArityDescription(int arityIndex, String arityDescription) { arityDescriptions.set(arityIndex, arityDescription); }

@Override
public String getArityCatalogRowDescription(int arityIndex, int rowIndex) { return catalogRowDescriptions.get(rowKey(arityIndex, rowIndex)); }

@Override
public void setArityCatalogRowDescription(int arityIndex, int rowIndex, String arityDescription) { catalogRowDescriptions.put(rowKey(arityIndex, rowIndex), arityDescription); }

@Override
public String getStructureDescription(int structureIndex) { return structureDescriptions.get(structureIndex); }

@Override
public void setStructureDescription(int structureIndex, String structureDescription) { structureDescriptions.set(structureIndex, structureDescription); }

@Override
public String getArityClassDescription(String className) { return arityClassDescriptions.get(className); }

@Override
public void setArityClassDescription(String className, String arityDescription) { arityClassDescriptions.put(className, arityDescription); }

@Override
public String getArityCatalogRowClassDescription(String className) { return catalogRowClassDescriptions.get(className); }

@Override
public void setArityCatalogRowClassDescription(String className, String arityDescription) { catalogRowClassDescriptions.put(className, arityDescription); }

@Override
public String getStructureClassDescription(String className) { return structureClassDescriptions.get(className); }

@Override
public void setStructureClassDescription(String className, String arityDescription) { structureClassDescriptions.put(className, arityDescription); }

/** Throws for a slot out of range; the row is checked only against a live arity, since a removed one's row count went with it. */
long rowKey(int arity, int row) {
	ACS_ArityComponentBase target = arities.get(arity);
	if(target != null) { Objects.checkIndex(row, target.catalogCount()); }
	return (long) arity << 32 | Integer.toUnsignedLong(row);
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
		ACS_StructureComponentBase structure = structures.get(slot);
		for(int i = 0; i < structure.getArityCount(); i++) {
			int arity = structure.getArityIdentity(i);
			if(arities.get(arity) != null && aritySlots.getInt(arity) < 0) { table.warnings.add("structure " + slot + " spans arity " + arity + ", which was left out"); }
		}
		table.place = "structure " + slot;
		if(!ACSE_PersistenceManagerBasic.write(folder.resolve(structureFile(slot)), table, structure::saveTo)) { throw new IOException("structure " + slot + " refused to be stored"); }
	}
	ACSE_PersistenceManagerBasic.write(folder.resolve(DESCRIPTIONS), table, this::writeDescriptions);
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
	String priorCoreDescription = coreDescription;
	HashMap<String, String> priorArityClasses = new HashMap<>(arityClassDescriptions);
	HashMap<String, String> priorRowClasses = new HashMap<>(catalogRowClassDescriptions);
	HashMap<String, String> priorStructureClasses = new HashMap<>(structureClassDescriptions);
	try {
		ACSE_PersistenceManagerBasic.read(folder.resolve(DESCRIPTIONS), classes, dis -> readDescriptions(dis, aritySlots.size(), structureSlots.size()));
		for(int slot = 0; slot < aritySlots.size(); slot++) {
			int number = aritySlots.getInt(slot);
			arities.add(number < 0 ? null : ACSE_PersistenceManagerBasic.read(folder.resolve(arityFile(slot)), classes, dis -> ACSE_PersistenceManagerBasic.build(classes[number], ACS_ArityComponentBase.class, this, journal, dis)));
		}
		for(int slot = 0; slot < structureSlots.size(); slot++) {
			int number = structureSlots.getInt(slot);
			structures.add(number < 0 ? null : ACSE_PersistenceManagerBasic.read(folder.resolve(structureFile(slot)), classes, dis -> ACSE_PersistenceManagerBasic.build(classes[number], ACS_StructureComponentBase.class, this, journal, dis)));
		}
		root = folder.toAbsolutePath().normalize().getParent();
	} catch(IOException | RuntimeException | Error e) {
		arities.clear();
		structures.clear();
		arityDescriptions.clear();
		structureDescriptions.clear();
		catalogRowDescriptions.clear();
		coreDescription = priorCoreDescription;
		replace(arityClassDescriptions, priorArityClasses);
		replace(catalogRowClassDescriptions, priorRowClasses);
		replace(structureClassDescriptions, priorStructureClasses);
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

/** The core's description, each slot's, each catalog row's, and each class's by name. */
boolean writeDescriptions(DataOutputStream dos) throws IOException {
	ACS_PersistenceManager.writeString(dos, coreDescription);
	writeStrings(dos, arityDescriptions);
	writeStrings(dos, structureDescriptions);
	dos.writeInt(catalogRowDescriptions.size());
	for(Long2ObjectMap.Entry<String> entry : catalogRowDescriptions.long2ObjectEntrySet()) {
		dos.writeLong(entry.getLongKey());
		ACS_PersistenceManager.writeString(dos, entry.getValue());
	}
	writeNames(dos, arityClassDescriptions);
	writeNames(dos, catalogRowClassDescriptions);
	writeNames(dos, structureClassDescriptions);
	return true;
}

/** Refuses descriptions for a slot count other than the indexer's. */
boolean readDescriptions(DataInputStream dis, int arityCount, int structureCount) throws IOException {
	coreDescription = ACS_PersistenceManager.readString(dis);
	readStrings(dis, arityDescriptions, arityCount, "arity");
	readStrings(dis, structureDescriptions, structureCount, "structure");
	for(int i = 0, count = dis.readInt(); i < count; i++) { catalogRowDescriptions.put(dis.readLong(), ACS_PersistenceManager.readString(dis)); }
	readNames(dis, arityClassDescriptions);
	readNames(dis, catalogRowClassDescriptions);
	readNames(dis, structureClassDescriptions);
	return true;
}

static void writeStrings(DataOutputStream dos, ArrayList<String> strings) throws IOException {
	dos.writeInt(strings.size());
	for(String string : strings) { ACS_PersistenceManager.writeString(dos, string); }
}

static void readStrings(DataInputStream dis, ArrayList<String> strings, int expected, String kind) throws IOException {
	int count = dis.readInt();
	if(count != expected) { throw new IOException(count + " " + kind + " descriptions for " + expected + " " + kind + " slots"); }
	for(int slot = 0; slot < count; slot++) { strings.add(ACS_PersistenceManager.readString(dis)); }
}

static void writeNames(DataOutputStream dos, HashMap<String, String> descriptions) throws IOException {
	dos.writeInt(descriptions.size());
	for(Map.Entry<String, String> entry : descriptions.entrySet()) {
		ACS_PersistenceManager.writeString(dos, entry.getKey());
		ACS_PersistenceManager.writeString(dos, entry.getValue());
	}
}

static void readNames(DataInputStream dis, HashMap<String, String> descriptions) throws IOException {
	for(int i = 0, count = dis.readInt(); i < count; i++) { descriptions.put(ACS_PersistenceManager.readString(dis), ACS_PersistenceManager.readString(dis)); }
}

static void replace(HashMap<String, String> descriptions, HashMap<String, String> with) {
	descriptions.clear();
	descriptions.putAll(with);
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

/** The requests, by code in the order of the marks: each reads its inputs, calls the public method, which keeps its checks, and writes the result. */
@Override
public boolean request(DataInputStream parameters, DataOutputStream returns) throws IOException {
	int code = parameters.readInt();
	try {
		switch(code) {
			case 0 -> addArity(ACS_Actor.readString(parameters), ACS_Actor.readString(parameters));
			case 1 -> returns.writeInt(addCatalogRow(parameters.readInt(), ACS_Actor.readString(parameters), ACS_Actor.readString(parameters), ACS_Actor.readString(parameters)));
			case 2 -> removeArity(parameters.readInt(), ACS_Actor.readString(parameters));
			case 3 -> removeStructure(parameters.readInt(), ACS_Actor.readString(parameters));
			case 4 -> delegateRemoval(parameters.readInt(), parameters.readInt(), ACS_Actor.readString(parameters));
			case 5 -> returns.writeLong(captureIdentity(parameters.readInt(), ACS_Actor.readString(parameters)));
			case 6 -> returns.writeLong(captureIdentity(parameters.readInt(), parameters.readLong(), ACS_Actor.readString(parameters)));
			default -> throw new IllegalArgumentException(getClass().getName() + " has no request " + code);
		}
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
		case STRUCTURE -> put(structures, params[index], (ACS_StructureComponentBase) journal.getObject(params[index + 1]));
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

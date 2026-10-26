package org.halim.acse;

import org.halim.acs.ACS_Journal;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.halim.acs.persistence.ACS_DescriptionManager;
import org.halim.acs.persistence.ACS_PersistenceManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * The descriptions of a database's components and classes, held by the core as its own actor per acs 95.
 * Not journaled: undo leaves descriptions alone, and a removed component keeps its own.
 * */
public class ACSE_DescriptionManagerBasic implements ACS_DescriptionManager {

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
/** General class descriptions by Class object. */
final HashMap<Class<?>, String> classDescriptions = new HashMap<>();

/** The core whose arities give the row count for rowKey checks. */
private final ACSE_CoreBasic core;

ACSE_DescriptionManagerBasic(ACSE_CoreBasic core) { this.core = core; }


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

@Override
public int getClassNameCount() { return classDescriptions.size(); }

@Override
public Class<?> getClassName(int index) {
	int i = 0;
	for(Class<?> key : classDescriptions.keySet()) { if(i++ == index) { return key; } }
	throw new IndexOutOfBoundsException("class index " + index + " of " + classDescriptions.size());
}

@Override
public String getClassDescription(Class<?> classType) { return classDescriptions.get(classType); }

@Override
public void setClassDescription(Class<?> classType, String description) { classDescriptions.put(classType, description); }

@Override
public void doTraceableStep(byte stepPair, int[] params, int index) {
	throw new UnsupportedOperationException("description changes are not journaled");
}


/** Throws for a slot out of range; the row is checked only against a live arity, since a removed one's row count went with it. */
long rowKey(int arity, int row) {
	var target = core.arities.get(arity);
	if(target != null) { Objects.checkIndex(row, target.catalogCount()); }
	return (long) arity << 32 | Integer.toUnsignedLong(row);
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

@Override
public ACS_Journal getJournal() { return core.journal; }

}

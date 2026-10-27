package org.halim.acsgui.acsess;

import org.halim.acsgui.mhexagon.ACSGUI_Service;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Arity;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Components;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Refusal;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Row;
import org.halim.acsgui.mhexagon.ACSGUI_Service.Structure;
import org.halim.acsgui.mhexagon.ACSGUI_Service.ValueType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * One database acsed holds, addressed as acsess addresses it: {@code <name>:core}, {@code <name>:journal},
 * {@code <name>:arity-<k>} and {@code <name>:arity-<k>.row-<j>}, and its name after a database command.
 * Every parameter goes by name, {@code identity=5}, so a value that looks like a flag stays a value; a
 * boolean goes as {@code +value} or not at all, since acsess takes a boolean no other way.
 * <p>
 * What a read gives back is kept in the {@link Kept} the service holds for the name, so every object it
 * makes for one database shares it, and dropped by {@link #forget}: the components, each arity's
 * identities, each value read, the stored classes and the decompiled sources. Reads run on the GUI's one
 * request thread and forget may come from the event thread, so the kept data is guarded by its own lock;
 * a read is done outside it, and only its answer is put back.
 * */
public class AcsessDatabase implements ACSGUI_Service.DatabaseService {

public static final String COMMENT = "acsess-gui";

final Acsess acsess;
final String name;
final Path folder;
final Kept kept;

AcsessDatabase(Acsess acsess, String name, Path folder, Kept kept) {
	this.acsess = acsess;
	this.name = name;
	this.folder = folder;
	this.kept = kept;
}

/** What was read of one database since the last change; a value's key is its arity, row and identity. */
static final class Kept {
	Components components;
	final HashMap<Integer, long[]> identities = new HashMap<>();
	final HashMap<String, String> values = new HashMap<>();
	ACSGUI_Service.Classes classes;
	final HashMap<String, String> sources = new HashMap<>();

	synchronized void clear() {
		components = null;
		identities.clear();
		values.clear();
		classes = null;
		sources.clear();
	}
}

static String key(int arity, int row, long identity) { return arity + "/" + row + "/" + identity; }

@Override public void forget() { kept.clear(); }

@Override public String getName() { return name; }

@Override public Path getFolder() { return folder; }

String address(String actor) { return name + ":" + actor; }

static String arity(int arity) { return "arity-" + arity; }

static String row(int arity, int row) { return "arity-" + arity + ".row-" + row; }

static String comment() { return "comment=" + COMMENT; }

/** A string parameter by name; none for "", since acsess refuses {@code name=} with nothing after it and takes a string not given as "". */
static List<String> named(String name, String value) { return value.isEmpty() ? List.of() : List.of(name + "=" + value); }

/**
 * Read from the tab-separated lines {@code components} prints, "-" for a field with nothing in it:
 * {@code arity k}, its class, description and identity count; under it a line per row,
 * {@code catalog j}, its kind and description; then {@code structure k}, its class, description and
 * the arities it spans. A removed slot prints {@code (removed)} in place of its class.
 * */
@Override
public Components getComponents() {
	synchronized(kept) { if(kept.components != null) { return kept.components; } }
	Components read = readComponents();
	synchronized(kept) { kept.components = read; }
	return read;
}

Components readComponents() {
	String printed = acsess.command("components", name);
	ArrayList<Arity> arities = new ArrayList<>();
	ArrayList<Structure> structures = new ArrayList<>();
	if(printed.equals("no components")) { return new Components(arities, structures); }
	int slot = -1;
	String className = null, description = null;
	long identities = 0;
	ArrayList<Row> rows = null;
	for(String line : printed.split("\n")) {
		String[] fields = line.split("\t", -1);
		if(line.startsWith("arity ")) {
			if(rows != null) { arities.add(new Arity(slot, className, description, identities, rows)); }
			slot = number(fields[0].substring(6), line);
			boolean removed = fields[1].equals("(removed)");
			className = removed ? null : fields[1];
			description = removed || fields.length < 3 ? null : field(fields[2]);
			identities = removed || fields.length < 4 ? 0 : Long.parseLong(fields[3].substring(0, fields[3].indexOf(' ')));
			rows = new ArrayList<>();
		} else if(line.startsWith("\tcatalog ")) {
			if(rows == null) { throw new Refusal("components printed a row before any arity: " + line); }
			boolean removed = fields[2].equals("(removed)");
			rows.add(new Row(number(fields[1].substring(8), line), removed ? null : fields[2], removed || fields.length < 4 ? null : field(fields[3])));
		} else if(line.startsWith("structure ")) {
			boolean removed = fields[1].equals("(removed)");
			int[] spanned = new int[0];
			if(!removed && fields.length >= 4 && fields[3].startsWith("arities")) {
				String[] parts = fields[3].substring(7).trim().split(" +");
				spanned = parts[0].isEmpty() ? new int[0] : java.util.Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
			}
			structures.add(new Structure(number(fields[0].substring(10), line), removed ? null : fields[1], removed || fields.length < 3 ? null : field(fields[2]), spanned));
		} else { throw new Refusal("components printed a line this program does not read: " + line); }
	}
	if(rows != null) { arities.add(new Arity(slot, className, description, identities, rows)); }
	return new Components(arities, structures);
}

static String field(String printed) { return printed.equals("-") ? null : printed; }

static int number(String digits, String line) {
	try { return Integer.parseInt(digits.trim()); }
	catch(NumberFormatException e) { throw new Refusal("components printed a slot this program does not read: " + line); }
}

@Override
public long[] getIdentities(int arity) {
	synchronized(kept) {
		long[] known = kept.identities.get(arity);
		if(known != null) { return known.clone(); }
	}
	String printed = acsess.command(address(arity(arity)), "occupiedIdentities");
	long[] read = printed.isEmpty() ? new long[0] : printed.lines().mapToLong(Long::parseLong).toArray();
	synchronized(kept) { kept.identities.put(arity, read.clone()); }
	return read;
}

/**
 * One session for the cells not kept, run past refusals, so a cell acsed refuses shows its reason and
 * costs no other cell; the refusal is kept as the value is, until the next change.
 * */
@Override
public String[][] getValues(int arity, List<Row> rows, long[] identities) {
	String[][] values = new String[identities.length][rows.size()];
	ArrayList<List<String>> requests = new ArrayList<>();
	ArrayList<int[]> cells = new ArrayList<>();
	synchronized(kept) {
		for(int i = 0; i < identities.length; i++) {
			for(int j = 0; j < rows.size(); j++) {
				Row row = rows.get(j);
				if(row.type().requestPart == null) { continue; }
				String known = kept.values.get(key(arity, row.slot(), identities[i]));
				if(known != null) { values[i][j] = known; continue; }
				requests.add(List.of(address(row(arity, row.slot())), "get" + row.type().requestPart + "For", "identity=" + identities[i]));
				cells.add(new int[] { i, j });
			}
		}
	}
	if(requests.isEmpty()) { return values; }
	List<ACSGUI_Service.Answer> answers = acsess.session(requests, true);
	synchronized(kept) {
		for(int k = 0; k < cells.size(); k++) {
			int i = cells.get(k)[0], j = cells.get(k)[1];
			ACSGUI_Service.Answer answer = answers.get(k);
			values[i][j] = answer.done() ? answer.text() : "(" + answer.text() + ")";
			kept.values.put(key(arity, rows.get(j).slot(), identities[i]), values[i][j]);
		}
	}
	return values;
}

@Override
public void setValue(int arity, Row row, long identity, String value) {
	ValueType type = row.type();
	if(type.requestPart == null) { throw new Refusal("row " + row.slot() + " holds " + row.kind() + ", which acsess cannot write"); }
	ArrayList<String> arguments = new ArrayList<>(List.of(address(row(arity, row.slot())), "set" + type.requestPart + "For", "identity=" + identity));
	if(type == ValueType.BOOLEAN) {
		if(!value.equals("true") && !value.equals("false")) { throw new Refusal(value + " is no boolean; true or false"); }
		if(value.equals("true")) { arguments.add("+value"); }
	} else { arguments.addAll(named("value", value)); }
	arguments.add(comment());
	acsess.command(arguments);
	forget();
}

@Override
public long captureIdentity(int arity) {
	long identity = Long.parseLong(acsess.command(address("core"), "captureIdentity", "arity=" + arity, comment()));
	forget();
	return identity;
}

@Override
public void deleteIdentity(int arity, long identity) {
	acsess.command(address("core"), "delegateRemoval", "arity=" + arity, "instance=" + identity, comment());
	forget();
}

@Override
public void addArity(String description) {
	ArrayList<String> arguments = new ArrayList<>(List.of(address("core"), "addArity"));
	arguments.addAll(named("description", description));
	arguments.add(comment());
	acsess.command(arguments);
	forget();
}

@Override
public int addCatalogRow(int arity, String className, String description) {
	ArrayList<String> arguments = new ArrayList<>(List.of(address("core"), "addCatalogRow", "arity=" + arity, "className=" + className));
	arguments.addAll(named("description", description));
	arguments.add(comment());
	int row = Integer.parseInt(acsess.command(arguments));
	forget();
	return row;
}

@Override
public void removeArity(int arity) {
	acsess.command(address("core"), "removeArity", "arity=" + arity, comment());
	forget();
}

@Override
public void removeStructure(int structure) {
	acsess.command(address("core"), "removeStructure", "structure=" + structure, comment());
	forget();
}

@Override
public void undo() {
	acsess.command(address("journal"), "undoLastActivity");
	forget();
}

@Override
public void redo() {
	acsess.command(address("journal"), "redoLastActivity");
	forget();
}

@Override
public void undoUntil(int activityIndex) {
	acsess.command(address("journal"), "undoUntilActivityIndex", "activityIndex=" + activityIndex);
	forget();
}

@Override
public void redoUntil(int activityIndex) {
	acsess.command(address("journal"), "redoUntilActivityIndex", "activityIndex=" + activityIndex);
	forget();
}

@Override public int getCurrentActivityIndex() { return Integer.parseInt(acsess.command(address("journal"), "getCurrentActivityIndex")); }

@Override public int getPendingActivityCount() { return Integer.parseInt(acsess.command(address("journal"), "getPendingActivityCount")); }

/** A store makes a new generation, so the stored classes are read again after it. */
@Override
public String store() {
	String answer = acsess.command("storeDB", name, comment());
	synchronized(kept) {
		kept.classes = null;
		kept.sources.clear();
	}
	return answer;
}

@Override
public ACSGUI_Service.Classes getClasses() {
	synchronized(kept) { if(kept.classes != null) { return kept.classes; } }
	ACSGUI_Service.Classes read = StoredClasses.read(folder);
	synchronized(kept) { kept.classes = read; }
	return read;
}

@Override
public String decompile(String className) {
	synchronized(kept) {
		String known = kept.sources.get(className);
		if(known != null) { return known; }
	}
	String source = StoredClasses.decompile(getClasses().store(), className);
	synchronized(kept) { kept.sources.put(className, source); }
	return source;
}

/** Forgets the database in acsed without storing it. */
public String unload() { return acsess.command("unloadDB", name, comment()); }

@Override public String toString() { return name; }

@Override public boolean equals(Object other) { return other instanceof AcsessDatabase database && database.name.equals(name); }

@Override public int hashCode() { return name.hashCode(); }

}

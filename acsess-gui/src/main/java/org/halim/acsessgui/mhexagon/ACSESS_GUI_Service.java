package org.halim.acsessgui.mhexagon;

import java.nio.file.Path;
import java.util.List;


/**
 * The port the GUI programs against, in the place OntoDirectory's {@code OntoDirectoryService} has: the
 * GUI never sees how a request reaches acsed. Every method may take as long as a program run, so the GUI
 * calls them off the event thread, and every one may throw {@link Refusal}, carrying acsed's or acsess's
 * own words.
 * <p>
 * The databases are acsed's, not this program's: one a terminal loaded is listed and shown as well, and
 * one this program loaded stays loaded for the terminal until it is closed.
 * */
public interface ACSESS_GUI_Service {

/** Starts acsed when nothing answers on its socket, and answers how it stands, as {@code acsess boot} prints it. */
String boot();

/** Makes a database in the folder and loads it, named after the folder. */
void createDatabase(Path folder);

void loadDatabase(Path folder);

/** Stores the database, then unloads it from acsed. */
void closeDatabase(DatabaseService database);

/** The database the workspace shows, or null on the welcome page. */
DatabaseService getActiveDatabase();

void dispatchDatabaseChooseRequest(String name);

/** acsed's databases, as {@code databases} lists them. */
List<DatabaseService> getLoadedDatabases();

void addACSESS_GUI_ServiceListener(ACSESS_GUI_ServiceListener listener);

/**
 * Lines as a person types them into an acsess session, sent as one connection when they are all queued:
 * the console's way, as an SQL tool's editor runs a script. A line acsess cannot translate refuses the
 * whole run, since nothing has been sent then.
 * */
List<Answer> runSession(List<String> lines, boolean continueOnRefusal);

/** One request of a session and its answer: done, refused, or not run after a refusal. */
record Answer(String typed, boolean done, String text) {}

/** Tells the listeners an action was refused; the GUI shows it and goes on. */
void reportRefusal(String action, RuntimeException refusal);

interface ACSESS_GUI_ServiceListener {
	void onGUI_Change();
	void onDatabaseLoad(DatabaseService database);
	void showRefusal(String action, String reason);
}

/**
 * One database acsed holds, addressed by its name. Each change is one request, so one journal activity,
 * and carries the comment {@code acsess-gui}.
 * <p>
 * What a read gives back is kept, so another view reading the same thing costs no program run, until
 * {@link #forget}, which the workspace calls after any change, and each change here calls itself. A change
 * a terminal makes is seen at the next refresh, as before.
 * */
interface DatabaseService {
	String getName();
	Path getFolder();
	Components getComponents();
	long[] getIdentities(int arity);
	/** A value per identity and row, as acsess prints it; null where the row's type cannot be read through acsess. */
	String[][] getValues(int arity, List<Row> rows, long[] identities);
	void setValue(int arity, Row row, long identity, String value);
	long captureIdentity(int arity);
	void deleteIdentity(int arity, long identity);
	void addArity(String description);
	int addCatalogRow(int arity, String className, String description);
	void removeArity(int arity);
	void removeStructure(int structure);
	void undo();
	void redo();
	void undoUntil(int activityIndex);
	void redoUntil(int activityIndex);
	int getCurrentActivityIndex();
	int getPendingActivityCount();
	String store();
	/** The classes of the current store, read from its files, since no request lists them. */
	Classes getClasses();
	/** The class's source, decompiled from the current store's classes.jar. */
	String decompile(String className);
	/** Drops what was read and kept. */
	void forget();
}

/**
 * The classes a database's current store carries: the core's and the journal's class, the classes its
 * indexer numbers, in the order met, each arity slot's and structure slot's class by that number, -1 for
 * an emptied slot, and the class files in its classes.jar. A class used since the last store has no number.
 * */
record Classes(Path store, String core, String journal, List<String> numbered, int[] arityClasses, int[] structureClasses, List<String> files) {
	/** The class's number, or -1 when the store numbers it not. */
	public int numberOf(String className) { return className == null ? -1 : numbered.indexOf(className); }
}

/** What a row's values are read and written as: the get and set requests of the arity that take them. */
enum ValueType {
	LONG("Long"), DOUBLE("Double"), BOOLEAN("Boolean"), STRING("String"), OBJECT(null);

	/** The part of {@code get<part>For} and {@code set<part>For}; null for a row acsess cannot read. */
	public final String requestPart;

	ValueType(String requestPart) { this.requestPart = requestPart; }

	/**
	 * From what {@code components} prints for a row: the row's class, named by ACSE's convention after the
	 * contract it implements, or the column's type for an arity that keeps no row objects. A class the
	 * convention does not cover is OBJECT.
	 * */
	public static ValueType of(String kind) {
		String simple = kind.substring(kind.lastIndexOf('.') + 1);
		if(simple.startsWith("IntegerValueCatalog") || kind.equals("int") || kind.equals("long")) { return LONG; }
		if(simple.startsWith("FloatingValueCatalog") || kind.equals("floating")) { return DOUBLE; }
		if(simple.startsWith("BooleanValueCatalog") || kind.equals("boolean")) { return BOOLEAN; }
		if(simple.startsWith("StringValueCatalog") || kind.equals("String")) { return STRING; }
		return OBJECT;
	}
}

/** The database's components, as {@code components} lists them; a removed slot keeps its place, with null fields. */
record Components(List<Arity> arities, List<Structure> structures) {}

record Arity(int slot, String className, String description, long identities, List<Row> rows) {
	public boolean isRemoved() { return className == null; }
}

record Row(int slot, String kind, String description) {
	public boolean isRemoved() { return kind == null; }
	public ValueType type() { return isRemoved() ? ValueType.OBJECT : ValueType.of(kind); }
}

record Structure(int slot, String className, String description, int[] arities) {
	public boolean isRemoved() { return className == null; }
}

/** A request acsess or acsed refused, or a program run that went wrong; the message is their own text. */
class Refusal extends RuntimeException {
	public Refusal(String message) { super(message); }
}

}

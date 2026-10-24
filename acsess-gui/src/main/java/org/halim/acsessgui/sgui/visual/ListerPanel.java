package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Classes;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Row;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Structure;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;


/**
 * A lister: the selection at its top chooses what it lists of the shown database, a line each, and its
 * single goal is that listing; any number may be open, each listing what it chose. A line is an element:
 * clicked to focus it, clicked again once focused to give it to the content panels whose kind takes it,
 * and dragged onto one. A removed slot keeps its line, and is no element.
 * <p>
 * The arities: slot, description, class and the number the current store gives that class, identities and
 * rows. The structures: slot, description, class and its number, and the arities it spans. The classes
 * the current store carries: the number its indexer gives each, its name, the slots it was stored in, by
 * the indexer, and what it is now, by the name components prints; the numbered ones first, by number, the
 * jar's other class files after, by name. A class number the store cannot give is left blank, and does
 * not stop the list. The procedures wait for their requests.
 * */
public class ListerPanel extends WorkSpaceViewPanel {

static final String NO_KIND = "(choose a list)";

/** The listers' selection, in its order. */
public enum Kind {
	ARITY_LIST("arity list"), STRUCTURE_LIST("structure list"), CLASS_LIST("class list"), PROCEDURE_LIST("procedure list");

	public final String label;

	Kind(String label) { this.label = label; }

	@Override public String toString() { return label; }
}

final JComboBox<Object> selection = new JComboBox<>();
final JLabel title = new JLabel(" ");
final LineModel model = new LineModel();
final JTable table = new JTable(model);
Kind kind;

/** What a lister shows: a title, and lines, each the element it is, or null. */
record Listed(String title, String[] columns, Object[][] cells, WorkspaceElement[] elements) {
	static Listed note(String title) { return new Listed(title, new String[0], new Object[0][0], new WorkspaceElement[0]); }
}

class LineModel extends AbstractTableModel {
	Listed listed = Listed.note(" ");

	@Override public int getRowCount() { return listed.cells.length; }

	@Override public int getColumnCount() { return listed.columns.length; }

	@Override public String getColumnName(int column) { return listed.columns[column]; }

	@Override public Object getValueAt(int line, int column) { return listed.cells[line][column]; }
}

public ListerPanel(ApplicationController app) {
	super(app);
	selection.addItem(NO_KIND);
	for(Kind each : Kind.values()) { selection.addItem(each); }
	selection.addActionListener(e -> {
		Object chosen = selection.getSelectedItem();
		kind = chosen instanceof Kind wanted ? wanted : null;
		reload();
	});
	JPanel top = new JPanel(new BorderLayout(0, 4));
	top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	top.add(selection, BorderLayout.NORTH);
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	top.add(title, BorderLayout.CENTER);
	add(top, BorderLayout.NORTH);
	table.setFillsViewportHeight(true);
	table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	table.setDefaultEditor(Object.class, null);
	add(new JScrollPane(table), BorderLayout.CENTER);
	WorkspaceElement.Transfer.source(table, point -> {
		int line = table.rowAtPoint(point);
		return line < 0 ? null : model.listed.elements[line];
	}, app.wsModeller::click);
	setPreferredSize(new Dimension(480, 600));
	show(Listed.note("Choose what to list."));
}

/** Lists what the kind names, as a sidebar's or a check's choice. */
public void choose(Kind chosen) { selection.setSelectedItem(chosen == null ? NO_KIND : chosen); }

public Kind getKind() { return kind; }

@Override public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) { reload(); }

void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(kind == null) { show(Listed.note("Choose what to list.")); return; }
	if(kind == Kind.PROCEDURE_LIST) { show(Listed.note("The procedures wait for their requests: acsess has none of a procedure, and components lists none.")); return; }
	if(database == null) { show(Listed.note("No database is shown.")); return; }
	Kind wanted = kind;
	app.request("list the " + wanted + " of " + database.getName(), () -> read(database, wanted), listed -> { if(wanted == kind) { show(listed); } });
}

void show(Listed listed) {
	model.listed = listed;
	model.fireTableStructureChanged();
	title.setText("<html>" + WelcomePage.escaped(listed.title) + "</html>");
}

/** Runs on the request thread; the classes are read through the kept data, and their absence leaves the numbers blank. */
static Listed read(ACSESS_GUI_Service.DatabaseService database, Kind kind) {
	Components components = database.getComponents();
	Classes classes;
	try { classes = database.getClasses(); }
	catch(RuntimeException e) {
		if(kind == Kind.CLASS_LIST) { throw e; }
		classes = null;
	}
	return switch(kind) {
		case ARITY_LIST -> arities(components, classes);
		case STRUCTURE_LIST -> structures(components, classes);
		case CLASS_LIST -> classes(components, classes);
		case PROCEDURE_LIST -> Listed.note("The procedures wait for their requests.");
	};
}

static Object number(Classes classes, String className) {
	int number = classes == null ? -1 : classes.numberOf(className);
	return number < 0 ? "" : number;
}

static Listed arities(Components components, Classes classes) {
	List<Arity> arities = components.arities();
	Object[][] cells = new Object[arities.size()][];
	WorkspaceElement[] elements = new WorkspaceElement[arities.size()];
	for(int line = 0; line < arities.size(); line++) {
		Arity arity = arities.get(line);
		if(arity.isRemoved()) {
			cells[line] = new Object[] { arity.slot(), "(removed)", "", "", "", "" };
			continue;
		}
		long rows = arity.rows().stream().filter(r -> !r.isRemoved()).count();
		cells[line] = new Object[] { arity.slot(), Utilities.described(arity.description()), Utilities.simple(arity.className()), number(classes, arity.className()), arity.identities(), rows };
		elements[line] = WorkspaceElement.arity(arity.slot());
	}
	return new Listed(arities.size() + (arities.size() == 1 ? " arity" : " arities"), new String[] { "arity", "description", "class", "number", "identities", "rows" }, cells, elements);
}

static Listed structures(Components components, Classes classes) {
	List<Structure> structures = components.structures();
	Object[][] cells = new Object[structures.size()][];
	WorkspaceElement[] elements = new WorkspaceElement[structures.size()];
	for(int line = 0; line < structures.size(); line++) {
		Structure structure = structures.get(line);
		if(structure.isRemoved()) {
			cells[line] = new Object[] { structure.slot(), "(removed)", "", "", "" };
			continue;
		}
		String spans = String.join(" ", Arrays.stream(structure.arities()).mapToObj(String::valueOf).toList());
		cells[line] = new Object[] { structure.slot(), Utilities.described(structure.description()), Utilities.simple(structure.className()), number(classes, structure.className()), spans };
		elements[line] = WorkspaceElement.structure(structure.slot());
	}
	return new Listed(structures.size() + (structures.size() == 1 ? " structure" : " structures"), new String[] { "structure", "description", "class", "number", "spans arities" }, cells, elements);
}

static Listed classes(Components components, Classes classes) {
	TreeSet<String> rest = new TreeSet<>(classes.files());
	ArrayList<String> ordered = new ArrayList<>(classes.numbered());
	rest.removeAll(classes.numbered());
	ordered.addAll(rest);
	Object[][] cells = new Object[ordered.size()][];
	WorkspaceElement[] elements = new WorkspaceElement[ordered.size()];
	for(int line = 0; line < ordered.size(); line++) {
		String name = ordered.get(line);
		int number = classes.numberOf(name);
		cells[line] = new Object[] { number < 0 ? "" : number, name, stored(classes, number), now(components, name) };
		elements[line] = classes.files().contains(name) ? WorkspaceElement.javaClass(name) : null;
	}
	return new Listed(classes.store() + ": " + classes.numbered().size() + " numbered, " + classes.files().size() + " class files", new String[] { "number", "class", "stored in", "now" }, cells, elements);
}

static String stored(Classes classes, int number) {
	if(number < 0) { return ""; }
	List<String> slots = new ArrayList<>();
	for(int slot = 0; slot < classes.arityClasses().length; slot++) { if(classes.arityClasses()[slot] == number) { slots.add("arity " + slot); } }
	for(int slot = 0; slot < classes.structureClasses().length; slot++) { if(classes.structureClasses()[slot] == number) { slots.add("structure " + slot); } }
	return String.join(", ", slots);
}

static String now(Components components, String name) {
	List<String> uses = new ArrayList<>();
	for(Arity arity : components.arities()) {
		if(name.equals(arity.className())) { uses.add("arity " + arity.slot()); }
		for(Row row : arity.rows()) { if(name.equals(row.kind())) { uses.add("row " + row.slot() + " of arity " + arity.slot()); } }
	}
	for(Structure structure : components.structures()) { if(name.equals(structure.className())) { uses.add("structure " + structure.slot()); } }
	return String.join(", ", uses);
}

}

package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.mhexagon.ACSGUI_Service;
import org.halim.acsgui.sgui.ApplicationController;
import org.halim.acsgui.sgui.sglib.Utilities;
import org.halim.acsgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * The kernel view: the shown database itself, in four types chosen from its header: the core, the
 * journal, the acs-city, a plane of its arities and structures, and the request console. The types take no element, so any
 * number of kernel panels may be open, each showing a type of its own choice, and a click on a city's
 * node is an element as any view's.
 * */
public class KernelPanel extends WorkSpaceViewPanel {

/** The types in the chooser's order: the name the popup lists, the title the header shows, and the icon. */
public enum Kind {
	CORE("core", "Core", "globe.svg"), JOURNAL("journal", "Journal", "book-bookmark.svg"), ACS_CITY("acs-city", "ACS-City", "city.svg"), CONSOLE("console", "Console", "terminal.svg");

	public final String label, title, icon;

	Kind(String label, String title, String icon) {
		this.label = label;
		this.title = title;
		this.icon = icon;
	}

	public Icon icon(int size) { return Utilities.loadSVGIcon("icons/" + icon, size, size, Utilities.ELEMENT_TEXT_PRIMARY); }
}

final JButton selection = Utilities.iconButton(Utilities.loadSVGIcon("icons/asterisk-simple.svg", 18, 18, Utilities.ELEMENT_TEXT_PRIMARY), "Kernel type");
final JPopupMenu kinds = new JPopupMenu();
/** Receives the header title, the chosen type's name, and its icon. */
public java.util.function.BiConsumer<String, Icon> heading = (name, icon) -> { };
final CardLayout cards = new CardLayout();
final JPanel body = new JPanel(cards);
final CoreView core;
final JournalView journal;
final StructurePlane plane;
final ConsoleView console;
Kind kind;

public KernelPanel(ApplicationController app) {
	super(app);
	console = new ConsoleView(app);
	core = new CoreView(app);
	journal = new JournalView(app);
	plane = new StructurePlane(app.wsModeller::click);
	core.updated = () -> updated.run();
	journal.updated = () -> updated.run();
	console.updated = () -> updated.run();
	for(Kind each : Kind.values()) {
		JMenuItem item = new JMenuItem(each.title, each.icon(18));
		item.addActionListener(e -> choose(each));
		kinds.add(item);
	}
	selection.addActionListener(e -> kinds.show(selection, 0, selection.getHeight()));
	body.add(core, Kind.CORE.name());
	body.add(journal, Kind.JOURNAL.name());
	body.add(plane, Kind.ACS_CITY.name());
	body.add(console, Kind.CONSOLE.name());
	add(body, BorderLayout.CENTER);
	setPreferredSize(new Dimension(360, 600));
}

/** Shows the type, reading it again. */
public void choose(Kind chosen) {
	kind = chosen;
	heading.accept(chosen.title, chosen.icon(18));
	cards.show(body, chosen.name());
	reload(database());
}

public Kind getKind() { return kind; }

/** The button choosing the type, which the panel's header holds. */
public JButton getChooser() { return selection; }

@Override public void onSelectedDatabaseChange(ACSGUI_Service.DatabaseService database) { reload(database); }

void reload(ACSGUI_Service.DatabaseService database) {
	if(kind == null) { return; }
	switch(kind) {
		case CORE -> core.onSelectedDatabaseChange(database);
		case JOURNAL -> journal.onSelectedDatabaseChange(database);
		case ACS_CITY -> city(database);
		case CONSOLE -> { }
	}
}

void city(ACSGUI_Service.DatabaseService database) {
	if(database == null) { plane.show(List.of(), List.of(), "No database is shown."); return; }
	app.request("read the acs-city of " + database.getName(), () -> city(database.getComponents()), drawn -> {
		if(kind != Kind.ACS_CITY) { return; }
		updated.run();
		plane.show(drawn.nodes(), drawn.edges(), drawn.note());
	});
}

/** Every structure and the arities it spans: the arities in a row, the structures in a row under them, removed slots left out. */
static ContentPanel.Plane city(ACSGUI_Service.Components components) {
	ArrayList<StructurePlane.Node> nodes = new ArrayList<>();
	ArrayList<StructurePlane.Edge> edges = new ArrayList<>();
	HashMap<Integer, Integer> arityNodes = new HashMap<>();
	for(ACSGUI_Service.Arity arity : components.arities()) {
		if(arity.isRemoved()) { continue; }
		arityNodes.put(arity.slot(), nodes.size());
		nodes.add(new StructurePlane.Node(org.halim.acsgui.sgui.sglib.WorkspaceElement.arity(arity.slot()), "arity " + arity.slot() + ": " + Utilities.described(arity.description()), (arityNodes.size() - 1) * StructurePlane.SPACING, 0, false));
	}
	int placed = 0;
	for(ACSGUI_Service.Structure structure : components.structures()) {
		if(structure.isRemoved()) { continue; }
		int at = nodes.size();
		nodes.add(new StructurePlane.Node(org.halim.acsgui.sgui.sglib.WorkspaceElement.structure(structure.slot()), "structure " + structure.slot() + ": " + Utilities.described(structure.description()), placed++ * StructurePlane.SPACING, StructurePlane.ROWS, true));
		for(int spanned : structure.arities()) {
			Integer to = arityNodes.get(spanned);
			if(to != null) { edges.add(new StructurePlane.Edge(at, to)); }
		}
	}
	return new ContentPanel.Plane("acs-city", nodes, edges, arityNodes.size() + " arities, " + placed + " structures. Drag the plane to move it, the wheel to zoom.");
}

}

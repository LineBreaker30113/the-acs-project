package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Structure;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;


/**
 * The structure chosen in the structures view: its class, description and spanned arities, and on a plane
 * the structure with its arities. The connections it creates wait for their requests: acsess has none that
 * reads a structure's relations, so the plane holds the spans alone until one is marked requestable.
 * */
public class StructureView extends WorkSpaceViewPanel {

final JLabel title = new JLabel("Choose a structure in the structures view.");
final StructurePlane plane;
int structure = -1;

public StructureView(ApplicationController app) {
	super(app);
	title.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	add(title, BorderLayout.NORTH);
	plane = new StructurePlane(app.wsModeller::click);
	add(plane, BorderLayout.CENTER);
	setPreferredSize(new Dimension(520, 600));
}

@Override
public void onSelectedStructureChange(int chosen) {
	structure = chosen;
	reload();
}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null || app.wsModeller.getSelectedStructure() < 0) { structure = -1; }
	reload();
}

void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || structure < 0) {
		title.setText(database == null ? "No database is shown." : "Choose a structure in the structures view.");
		plane.show(new ArrayList<>(), new ArrayList<>(), "");
		return;
	}
	int wanted = structure;
	app.request("read structure " + wanted, database::getComponents, components -> { if(wanted == structure) { show(components); } });
}

void show(Components components) {
	Structure shown = components.structures().stream().filter(s -> s.slot() == structure).findFirst().orElse(null);
	if(shown == null || shown.isRemoved()) {
		title.setText("Structure " + structure + " is not in the database.");
		plane.show(new ArrayList<>(), new ArrayList<>(), "");
		return;
	}
	title.setText("<html><b>structure " + structure + "</b>: " + WelcomePage.escaped(Utilities.described(shown.description())) + "<br>" + WelcomePage.escaped(shown.className()) + "</html>");
	ArrayList<StructurePlane.Node> nodes = new ArrayList<>();
	ArrayList<StructurePlane.Edge> edges = new ArrayList<>();
	int count = shown.arities().length;
	nodes.add(new StructurePlane.Node(WorkspaceElement.structure(structure), "structure " + structure, (count - 1) * StructuresView.SPACING / 2, StructuresView.ROWS, true));
	for(int i = 0; i < count; i++) {
		int spanned = shown.arities()[i];
		Arity arity = components.arities().stream().filter(a -> a.slot() == spanned).findFirst().orElse(null);
		String label = "arity " + spanned + ": " + (arity == null || arity.isRemoved() ? "(not in the database)" : Utilities.described(arity.description()));
		nodes.add(new StructurePlane.Node(WorkspaceElement.arity(spanned), label, i * StructuresView.SPACING, 0, false));
		edges.add(new StructurePlane.Edge(0, nodes.size() - 1));
	}
	plane.show(nodes, edges, "Spans " + count + (count == 1 ? " arity" : " arities") + ". Its connections wait for their requests: acsess has none that reads a structure's relations.");
}

}

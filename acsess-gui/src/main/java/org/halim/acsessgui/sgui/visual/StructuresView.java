package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Structure;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * The shown database's structures and arities on a plane: the arities in a row, the structures in a row
 * under them, and an edge from each structure to each arity it spans, as components prints them; removed
 * slots are left out. A click on a structure shows it in the structure view and focuses it; a click on an
 * arity only focuses it, since choosing arities is the arities view's goal; either is dragged as its element.
 * */
public class StructuresView extends WorkSpaceViewPanel {

static final double SPACING = 180, ROWS = 160;

final StructurePlane plane;

public StructuresView(ApplicationController app) {
	super(app);
	plane = new StructurePlane(element -> { if(element.type() == WorkspaceElement.Type.STRUCTURE) { app.wsModeller.choose(element); } else { app.wsModeller.click(element); } });
	add(plane, BorderLayout.CENTER);
	plane.show(new ArrayList<>(), new ArrayList<>(), "No database is shown.");
	setPreferredSize(new Dimension(560, 600));
}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null) {
		plane.show(new ArrayList<>(), new ArrayList<>(), "No database is shown.");
		return;
	}
	app.request("read the components of " + database.getName(), database::getComponents, this::show);
}

void show(Components components) {
	ArrayList<StructurePlane.Node> nodes = new ArrayList<>();
	ArrayList<StructurePlane.Edge> edges = new ArrayList<>();
	HashMap<Integer, Integer> arityNodes = new HashMap<>();
	for(Arity arity : components.arities()) {
		if(arity.isRemoved()) { continue; }
		arityNodes.put(arity.slot(), nodes.size());
		nodes.add(new StructurePlane.Node(WorkspaceElement.arity(arity.slot()), "arity " + arity.slot() + ": " + Utilities.described(arity.description()), arityNodes.size() * SPACING - SPACING, 0, false));
	}
	int placed = 0;
	for(Structure structure : components.structures()) {
		if(structure.isRemoved()) { continue; }
		int at = nodes.size();
		nodes.add(new StructurePlane.Node(WorkspaceElement.structure(structure.slot()), "structure " + structure.slot() + ": " + Utilities.described(structure.description()), placed++ * SPACING, ROWS, true));
		for(int spanned : structure.arities()) {
			Integer to = arityNodes.get(spanned);
			if(to != null) { edges.add(new StructurePlane.Edge(at, to)); }
		}
	}
	String note = arityNodes.size() + " arities, " + placed + " structures. Drag the plane to move it, the wheel to zoom.";
	plane.show(nodes, edges, note);
}

}

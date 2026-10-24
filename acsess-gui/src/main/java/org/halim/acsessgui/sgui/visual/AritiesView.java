package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Classes;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


/**
 * The shown database's arities, a line each: its slot, description, class and the number the current store
 * gives that class, its identities and rows; a removed slot keeps its line. A click on a line shows the
 * arity in the arity table view and focuses it; a line is dragged as its arity. A class number the store
 * cannot give is left blank, and does not stop the list.
 * */
public class AritiesView extends WorkSpaceViewPanel {

final DefaultTableModel model = new DefaultTableModel(new Object[] { "arity", "description", "class", "number", "identities", "rows" }, 0) {
	@Override public boolean isCellEditable(int row, int column) { return false; }
};
final JTable table = new JTable(model);
List<Arity> lines = List.of();

public AritiesView(ApplicationController app) {
	super(app);
	table.setFillsViewportHeight(true);
	table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	add(new JScrollPane(table), BorderLayout.CENTER);
	WorkspaceElement.Transfer.source(table, point -> {
		int line = table.rowAtPoint(point);
		return line < 0 || line >= lines.size() || lines.get(line).isRemoved() ? null : WorkspaceElement.arity(lines.get(line).slot());
	}, app.wsModeller::choose);
	setPreferredSize(new Dimension(480, 600));
}

record Listing(Components components, Classes classes) {}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null) {
		lines = List.of();
		model.setRowCount(0);
		return;
	}
	app.request("read the arities of " + database.getName(), () -> {
		Classes classes;
		try { classes = database.getClasses(); }
		catch(RuntimeException e) { classes = null; }
		return new Listing(database.getComponents(), classes);
	}, this::show);
}

void show(Listing listing) {
	lines = new ArrayList<>(listing.components().arities());
	model.setRowCount(0);
	int selected = -1;
	for(int line = 0; line < lines.size(); line++) {
		Arity arity = lines.get(line);
		if(arity.isRemoved()) {
			model.addRow(new Object[] { arity.slot(), "(removed)", "", "", "", "" });
			continue;
		}
		int number = listing.classes() == null ? -1 : listing.classes().numberOf(arity.className());
		long rows = arity.rows().stream().filter(r -> !r.isRemoved()).count();
		model.addRow(new Object[] { arity.slot(), Utilities.described(arity.description()), Utilities.simple(arity.className()), number < 0 ? "" : number, arity.identities(), rows });
		if(arity.slot() == app.wsModeller.getSelectedArity()) { selected = line; }
	}
	if(selected >= 0) { table.getSelectionModel().setSelectionInterval(selected, selected); }
}

}

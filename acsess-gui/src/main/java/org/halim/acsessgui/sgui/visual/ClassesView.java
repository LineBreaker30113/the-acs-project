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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;


/**
 * The classes the shown database's current store carries, a line each: the number its indexer gives it,
 * its name, the slots it was stored in, by the indexer, and what it is now, by the name components prints:
 * the numbers are what the other views match. The classes the indexer numbers come first, by number; the
 * jar's other class files, what those reach, follow by name. Choosing one shows it in the code view.
 * */
public class ClassesView extends WorkSpaceViewPanel {

final JLabel title = new JLabel("No database is shown.");
final DefaultTableModel model = new DefaultTableModel(new Object[] { "number", "class", "stored in", "now" }, 0) {
	@Override public boolean isCellEditable(int row, int column) { return false; }
};
final JTable table = new JTable(model);

public ClassesView(ApplicationController app) {
	super(app);
	title.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	add(title, BorderLayout.NORTH);
	table.setFillsViewportHeight(true);
	table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	table.getSelectionModel().addListSelectionListener(e -> {
		int line = table.getSelectedRow();
		if(!e.getValueIsAdjusting() && line >= 0) { app.wsModeller.broadcastClassSelection((String) model.getValueAt(line, 1)); }
	});
	add(new JScrollPane(table), BorderLayout.CENTER);
	setPreferredSize(new Dimension(560, 600));
}

record Listing(Classes classes, Components components) {}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	model.setRowCount(0);
	if(database == null) {
		title.setText("No database is shown.");
		return;
	}
	app.request("read the stored classes of " + database.getName(), () -> new Listing(database.getClasses(), database.getComponents()), this::show);
}

void show(Listing listing) {
	Classes classes = listing.classes();
	String selected = app.wsModeller.getSelectedClass();
	model.setRowCount(0);
	TreeSet<String> rest = new TreeSet<>(classes.files());
	ArrayList<String> ordered = new ArrayList<>(classes.numbered());
	rest.removeAll(classes.numbered());
	ordered.addAll(rest);
	for(String name : ordered) {
		int number = classes.numberOf(name);
		model.addRow(new Object[] { number < 0 ? "" : number, name, stored(classes, number), now(listing.components(), name) });
	}
	title.setText("<html>" + WelcomePage.escaped(classes.store().toString()) + "<br>" + classes.numbered().size() + " numbered, " + classes.files().size() + " class files</html>");
	for(int line = 0; line < model.getRowCount(); line++) {
		if(model.getValueAt(line, 1).equals(selected)) { table.getSelectionModel().setSelectionInterval(line, line); }
	}
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

package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;


/**
 * The shown database's core: its class, as the current store's indexer names it, and how many arities and
 * structures it holds, removed slots left out; and its requests, a button each, asking what they need.
 * Every request is one journal activity, and every view reads again after it. A core class the store
 * cannot give is shown with the reason, and does not stop the counts.
 * */
public class CoreView extends WorkSpaceViewPanel {

/** The ACSE rows offered when adding one; any other class name may be typed. */
static final String[] ROW_CLASSES = {
	"org.halim.acse.arity.catalog.memory.StringValueCatalogArrays",
	"org.halim.acse.arity.catalog.memory.IntegerValueCatalogArraysLong",
	"org.halim.acse.arity.catalog.memory.IntegerValueCatalogArraysInt",
	"org.halim.acse.arity.catalog.memory.FloatingValueCatalogArraysDouble",
	"org.halim.acse.arity.catalog.memory.BooleanValueCatalogBits",
};

final JLabel state = new JLabel("No database is shown.");

public CoreView(ApplicationController app) {
	super(app);
	JPanel body = new JPanel();
	body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
	body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
	state.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	state.setAlignmentX(Component.LEFT_ALIGNMENT);
	body.add(state);
	body.add(Box.createVerticalStrut(12));

	JPanel tools = new JPanel(new GridLayout(0, 1, 0, 4));
	tools.setAlignmentX(Component.LEFT_ALIGNMENT);
	tools.add(button("Add Arity...", this::addArity));
	tools.add(button("Add Row...", this::addRow));
	tools.add(button("Remove Arity...", this::removeArity));
	tools.add(button("Remove Structure...", this::removeStructure));
	tools.add(button("Capture Identity...", this::captureIdentity));
	tools.add(button("Delegate Removal...", this::delegateRemoval));
	tools.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));
	body.add(tools);
	add(body, BorderLayout.NORTH);
	setPreferredSize(new Dimension(320, 600));
}

static JButton button(String text, Runnable action) {
	JButton button = new JButton(text);
	button.addActionListener(e -> action.run());
	return button;
}

record Standing(Components components, String core) {}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null) {
		state.setText("No database is shown.");
		return;
	}
	app.request("read the core of " + database.getName(), () -> {
		String core;
		try { core = database.getClasses().core(); }
		catch(RuntimeException e) { core = "(" + e.getMessage() + ")"; }
		return new Standing(database.getComponents(), core);
	}, standing -> {
		long arities = standing.components().arities().stream().filter(a -> !a.isRemoved()).count();
		long structures = standing.components().structures().stream().filter(s -> !s.isRemoved()).count();
		state.setText("<html>" + WelcomePage.escaped(database.getName()) + "<br>core: " + WelcomePage.escaped(standing.core()) + "<br>arities: " + arities + "<br>structures: " + structures + "</html>");
	});
}

/** The numbers the form asks, or null when cancelled or not numbers; the first field starts at {@code initial}. */
long[] askNumbers(String title, long initial, String... labels) {
	JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
	JTextField[] fields = new JTextField[labels.length];
	for(int i = 0; i < labels.length; i++) {
		fields[i] = new JTextField(i == 0 && initial >= 0 ? String.valueOf(initial) : "", 12);
		form.add(new JLabel(labels[i]));
		form.add(fields[i]);
	}
	if(JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) { return null; }
	long[] numbers = new long[labels.length];
	for(int i = 0; i < labels.length; i++) {
		try { numbers[i] = Long.parseLong(fields[i].getText().trim()); }
		catch(NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "'" + fields[i].getText() + "' is no number.", title, JOptionPane.WARNING_MESSAGE);
			return null;
		}
	}
	return numbers;
}

void addArity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	String description = JOptionPane.showInputDialog(this, "The new arity's description:", "Add Arity", JOptionPane.PLAIN_MESSAGE);
	if(description != null) { app.perform("add an arity", () -> database.addArity(description), () -> app.wsModeller.triggerDatabaseRefresh(database)); }
}

void addRow() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	int selected = app.wsModeller.getSelectedArity();
	JTextField arity = new JTextField(selected >= 0 ? String.valueOf(selected) : "", 12);
	JComboBox<String> className = new JComboBox<>(ROW_CLASSES);
	className.setEditable(true);
	JTextField description = new JTextField(24);
	JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
	form.add(new JLabel("The arity:"));
	form.add(arity);
	form.add(new JLabel("The row's class, as the database's classes name it:"));
	form.add(className);
	form.add(new JLabel("Its description:"));
	form.add(description);
	if(JOptionPane.showConfirmDialog(this, form, "Add Row", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) { return; }
	int at;
	try { at = Integer.parseInt(arity.getText().trim()); }
	catch(NumberFormatException e) {
		JOptionPane.showMessageDialog(this, "'" + arity.getText() + "' is no arity.", "Add Row", JOptionPane.WARNING_MESSAGE);
		return;
	}
	String chosenClass = String.valueOf(className.getSelectedItem()).trim();
	app.request("add a row to arity " + at, () -> database.addCatalogRow(at, chosenClass, description.getText()), row -> app.wsModeller.triggerDatabaseRefresh(database));
}

void removeArity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	long[] numbers = askNumbers("Remove Arity", app.wsModeller.getSelectedArity(), "The arity to remove:");
	if(numbers == null || !confirmed("Remove arity " + numbers[0] + "? It is one journal activity, undone as one.")) { return; }
	int at = (int) numbers[0];
	app.perform("remove arity " + at, () -> database.removeArity(at), () -> app.wsModeller.triggerDatabaseRefresh(database));
}

void removeStructure() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	long[] numbers = askNumbers("Remove Structure", app.wsModeller.getSelectedStructure(), "The structure to remove:");
	if(numbers == null || !confirmed("Remove structure " + numbers[0] + "? It is one journal activity, undone as one.")) { return; }
	int at = (int) numbers[0];
	app.perform("remove structure " + at, () -> database.removeStructure(at), () -> app.wsModeller.triggerDatabaseRefresh(database));
}

void captureIdentity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	long[] numbers = askNumbers("Capture Identity", app.wsModeller.getSelectedArity(), "The arity to capture an identity in:");
	if(numbers == null) { return; }
	int at = (int) numbers[0];
	app.request("capture an identity in arity " + at, () -> database.captureIdentity(at), identity -> {
		app.wsModeller.triggerDatabaseRefresh(database);
		JOptionPane.showMessageDialog(this, "Arity " + at + " captured identity " + identity + ".", "Captured", JOptionPane.INFORMATION_MESSAGE);
	});
}

void delegateRemoval() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	long[] numbers = askNumbers("Delegate Removal", app.wsModeller.getSelectedArity(), "The arity:", "The identity to remove, the structures holding it letting go first:");
	if(numbers == null || !confirmed("Remove identity " + numbers[1] + " from arity " + numbers[0] + "? It is one journal activity, undone as one.")) { return; }
	int at = (int) numbers[0];
	long identity = numbers[1];
	app.perform("remove identity " + identity + " of arity " + at, () -> database.deleteIdentity(at, identity), () -> app.wsModeller.triggerDatabaseRefresh(database));
}

boolean confirmed(String question) { return JOptionPane.showConfirmDialog(this, question, "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }

}

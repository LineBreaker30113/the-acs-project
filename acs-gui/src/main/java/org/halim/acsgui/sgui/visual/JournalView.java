package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.mhexagon.ACSGUI_Service;
import org.halim.acsgui.sgui.ApplicationController;
import org.halim.acsgui.sgui.sglib.Utilities;
import org.halim.acsgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;


/** The shown database's journal: where it stands and how much redo holds, undo and redo of one activity or to an activity index, and storing. */
public class JournalView extends WorkSpaceViewPanel {

final JLabel state = new JLabel("No database is shown.");

public JournalView(ApplicationController app) {
	super(app);
	JPanel body = new JPanel();
	body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
	body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
	state.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	state.setAlignmentX(Component.LEFT_ALIGNMENT);
	body.add(state);
	body.add(Box.createVerticalStrut(12));

	JPanel tools = new JPanel(new GridLayout(0, 2, 4, 4));
	tools.setAlignmentX(Component.LEFT_ALIGNMENT);
	JButton undo = new JButton("Undo (Ctrl+Z)");
	undo.addActionListener(e -> app.undo());
	JButton redo = new JButton("Redo (Ctrl+Y)");
	redo.addActionListener(e -> app.redo());
	JButton store = new JButton("Store");
	store.addActionListener(e -> {
		ACSGUI_Service.DatabaseService database = database();
		if(database == null) { return; }
		// A store makes a new generation, whose indexer numbers the classes anew, so the views read again.
		app.request("store " + database.getName(), database::store, answer -> {
			app.wsModeller.triggerDatabaseRefresh(database);
			JOptionPane.showMessageDialog(this, answer, "Stored", JOptionPane.INFORMATION_MESSAGE);
		});
	});
	JButton undoTo = new JButton("Undo To...");
	undoTo.addActionListener(e -> moveTo(true));
	JButton redoTo = new JButton("Redo To...");
	redoTo.addActionListener(e -> moveTo(false));
	tools.add(undo);
	tools.add(redo);
	tools.add(undoTo);
	tools.add(redoTo);
	tools.add(store);
	body.add(tools);
	add(body, BorderLayout.NORTH);
	setPreferredSize(new Dimension(360, 600));
}

/** Undoes or redoes until the journal stands at the activity index asked, each activity on the way one step back or forth. */
void moveTo(boolean undoing) {
	ACSGUI_Service.DatabaseService database = database();
	if(database == null) { return; }
	String verb = undoing ? "Undo" : "Redo";
	String typed = JOptionPane.showInputDialog(this, verb + " until the activity index:", verb + " To", JOptionPane.PLAIN_MESSAGE);
	if(typed == null) { return; }
	int index;
	try { index = Integer.parseInt(typed.trim()); }
	catch(NumberFormatException e) {
		JOptionPane.showMessageDialog(this, "'" + typed + "' is no activity index.", verb + " To", JOptionPane.WARNING_MESSAGE);
		return;
	}
	app.perform(verb.toLowerCase() + " to activity " + index, () -> { if(undoing) { database.undoUntil(index); } else { database.redoUntil(index); } }, () -> app.wsModeller.triggerDatabaseRefresh(database));
}

record Standing(int index, int pending) {}

@Override
public void onSelectedDatabaseChange(ACSGUI_Service.DatabaseService database) {
	if(database == null) {
		state.setText("No database is shown.");
		return;
	}
	app.request("read the journal of " + database.getName(), () -> new Standing(database.getCurrentActivityIndex(), database.getPendingActivityCount()), standing -> {
		updated.run();
		state.setText("<html>" + WelcomePage.escaped(database.getName()) + "<br>activity index: " + standing.index() + "<br>undone, redo holds: " + standing.pending() + "</html>");
	});
}

}

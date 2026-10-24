package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;


/**
 * The class chosen in the classes view, decompiled by Vineflower from the current store's classes.jar,
 * with its nested classes. It is read only: the source is what the stored bytes say, not the file the
 * class was compiled from, and decompiling costs time once, then is kept until the next change or store.
 * */
public class CodeView extends WorkSpaceViewPanel {

final JLabel title = new JLabel("Choose a class in the classes view.");
final JTextArea code = new JTextArea();
String className;

public CodeView(ApplicationController app) {
	super(app);
	title.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	add(title, BorderLayout.NORTH);
	code.setEditable(false);
	code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
	code.setTabSize(4);
	add(new JScrollPane(code), BorderLayout.CENTER);
	setPreferredSize(new Dimension(640, 600));
}

@Override
public void onSelectedClassChange(String chosen) {
	className = chosen;
	reload();
}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null || app.wsModeller.getSelectedClass() == null) {
		className = null;
		title.setText(database == null ? "No database is shown." : "Choose a class in the classes view.");
		code.setText("");
		return;
	}
	reload();
}

void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || className == null) { return; }
	String wanted = className;
	title.setText("decompiling " + wanted + "...");
	app.request("decompile " + wanted, () -> database.decompile(wanted), source -> {
		if(!wanted.equals(className)) { return; }
		title.setText(wanted);
		code.setText(source);
		code.setCaretPosition(0);
	});
}

}

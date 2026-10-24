package org.halim.acsessgui.sgui;

import javax.swing.*;
import java.awt.*;


/** The window's content, as OntoDirectory's: the sidebar on the left, the pages in the center, and the app-wide undo and redo hotkeys. */
public class GUI_RootPanel extends JPanel {

public final LeftSidebar leftSidebar;
public final CenterPanel centerPanel;
public final WorkspacePanel workspacePanel;
public final ApplicationController appController;

public GUI_RootPanel(ApplicationController appController) {
	this.appController = appController;
	setLayout(new BorderLayout());
	setPreferredSize(new Dimension(1280, 800));

	// WorkspacePanel before CenterPanel, which holds it.
	workspacePanel = new WorkspacePanel(this);
	leftSidebar = new LeftSidebar(this);
	centerPanel = new CenterPanel(this);

	add(leftSidebar, BorderLayout.WEST);
	add(centerPanel, BorderLayout.CENTER);

	InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
	ActionMap am = getActionMap();
	im.put(KeyStroke.getKeyStroke("control Z"), "UndoAction");
	am.put("UndoAction", new AbstractAction() {
		@Override public void actionPerformed(java.awt.event.ActionEvent e) { appController.undo(); }
	});
	im.put(KeyStroke.getKeyStroke("control Y"), "RedoAction");
	am.put("RedoAction", new AbstractAction() {
		@Override public void actionPerformed(java.awt.event.ActionEvent e) { appController.redo(); }
	});

	centerPanel.add(workspacePanel, "WORKSPACE");
}

}

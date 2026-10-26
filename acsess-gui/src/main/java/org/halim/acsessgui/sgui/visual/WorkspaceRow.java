package org.halim.acsessgui.sgui.visual;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;


/** One row of the workspace: its views side by side, each column the row's width over the count, whatever a view shows, so an update never moves a neighbor. */
public class WorkspaceRow extends JPanel {

public final ArrayList<WorkPanelWrapper> panels = new ArrayList<>();

public WorkspaceRow() {
	setLayout(new GridLayout(1, 0));
	setOpaque(false);
}

public void render() {
	removeAll();
	for(WorkPanelWrapper wrapper : panels) { add(wrapper); }
	revalidate();
	repaint();
}

}

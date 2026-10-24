package org.halim.acsessgui.sgui.visual;

import javax.swing.*;
import java.util.ArrayList;


/** One row of the workspace: its views side by side, as the workspace held all of them before it had rows. */
public class WorkspaceRow extends JPanel {

public final ArrayList<WorkPanelWrapper> panels = new ArrayList<>();

public WorkspaceRow() {
	setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	setOpaque(false);
}

public void render() {
	removeAll();
	for(WorkPanelWrapper wrapper : panels) { add(wrapper); }
	revalidate();
	repaint();
}

}

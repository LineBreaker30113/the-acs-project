package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.sgui.LeftSidebar;
import org.halim.acsessgui.sgui.WorkspacePanel;
import org.halim.acsessgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.util.ArrayList;


/** The toggles of the workspace's fundamental views, and the buttons opening a new lister and a new content panel. */
public class WorkspaceSection extends JPanel {

final ArrayList<JButton> buttons = new ArrayList<>();

public WorkspaceSection(LeftSidebar leftSidebar) {
	setBackground(leftSidebar.getBackground().brighter());
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	toggle(leftSidebar, "Core", "hard-drives.svg", WorkspacePanel.coreVN);
	toggle(leftSidebar, "Journal", "notebook.svg", WorkspacePanel.journalVN);
	JButton lister = place(Utilities.sidebarButton("Lister", "tree-view.svg", "tree-view.svg"));
	lister.addActionListener(e -> leftSidebar.owner.workspacePanel.openListerPanel());
	JButton content = place(Utilities.sidebarButton("Content", "house.svg", "house.svg"));
	content.addActionListener(e -> leftSidebar.owner.workspacePanel.openContentPanel());
	toggle(leftSidebar, "Request Console", "note.svg", WorkspacePanel.consoleVN);
}

void toggle(LeftSidebar leftSidebar, String name, String icon, String view) {
	JButton btn = place(Utilities.sidebarButton(name, icon, icon));
	btn.addActionListener(e -> leftSidebar.owner.workspacePanel.panelToggled(view));
}

JButton place(JButton btn) {
	if(!buttons.isEmpty()) { add(Box.createVerticalStrut(8)); }
	buttons.add(btn);
	add(btn);
	return btn;
}

public void updateCollapseState(boolean isCollapsed) {
	for(JButton btn : buttons) { Utilities.updateCollapseState(btn, isCollapsed); }
}

}

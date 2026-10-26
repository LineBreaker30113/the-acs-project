package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.sgui.LeftSidebar;
import org.halim.acsessgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.util.ArrayList;


/** The buttons opening a new kernel panel, a new lister and a new content panel. */
public class WorkspaceSection extends JPanel {

final ArrayList<JButton> buttons = new ArrayList<>();

public WorkspaceSection(LeftSidebar leftSidebar) {
	setBackground(leftSidebar.getBackground().brighter());
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	JButton kernel = place(Utilities.sidebarButton("Kernel", "asterisk-simple.svg", "asterisk-simple.svg"));
	kernel.addActionListener(e -> leftSidebar.owner.workspacePanel.openKernelPanel().choose(KernelPanel.Kind.CORE));
	JButton lister = place(Utilities.sidebarButton("Lister", "list.svg", "list.svg"));
	lister.addActionListener(e -> leftSidebar.owner.workspacePanel.openListerPanel());
	JButton content = place(Utilities.sidebarButton("Content", "app-window.svg", "app-window.svg"));
	content.addActionListener(e -> leftSidebar.owner.workspacePanel.openContentPanel());
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

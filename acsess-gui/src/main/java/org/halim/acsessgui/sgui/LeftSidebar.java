package org.halim.acsessgui.sgui;

import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.visual.AnchorSection;
import org.halim.acsessgui.sgui.visual.LoadedDatabasesSection;
import org.halim.acsessgui.sgui.visual.WorkspaceSection;

import javax.swing.*;
import java.awt.*;


/** The sidebar, as OntoDirectory's: the anchors, acsed's databases, and the toggles of the workspace's views; it collapses to its icons. */
public class LeftSidebar extends JPanel {

public final GUI_RootPanel owner;
boolean isCollapsed = false;

public final int expanded = 260, contracted = 56;

public final AnchorSection anchorSection;
public final LoadedDatabasesSection loadedDatabasesSection;
public final WorkspaceSection workspaceSection;

public LeftSidebar(GUI_RootPanel owner) {
	this.owner = owner;
	setBackground(Utilities.sideBarBG);
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	setSidebarWidth(expanded);

	anchorSection = new AnchorSection(this);
	loadedDatabasesSection = new LoadedDatabasesSection(this);
	workspaceSection = new WorkspaceSection(this);

	anchorSection.setAlignmentX(Component.RIGHT_ALIGNMENT);
	loadedDatabasesSection.setAlignmentX(Component.RIGHT_ALIGNMENT);
	workspaceSection.setAlignmentX(Component.RIGHT_ALIGNMENT);

	add(anchorSection);
	add(Box.createVerticalStrut(32));
	add(loadedDatabasesSection);
	add(Box.createVerticalStrut(32));
	// The toggles outgrow a short window, so they scroll.
	JScrollPane toggles = new JScrollPane(workspaceSection, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
	toggles.setBorder(BorderFactory.createEmptyBorder());
	toggles.getVerticalScrollBar().setUnitIncrement(16);
	toggles.setAlignmentX(Component.RIGHT_ALIGNMENT);
	add(toggles);
	add(Box.createVerticalStrut(40));
}

void setSidebarWidth(int width) {
	Dimension dim = new Dimension(width, Short.MAX_VALUE);
	setPreferredSize(dim);
	setMinimumSize(dim);
	setMaximumSize(dim);
}

public void toggleCollapse() {
	isCollapsed = !isCollapsed;
	setSidebarWidth(isCollapsed ? contracted : expanded);
	anchorSection.updateCollapseState(isCollapsed);
	loadedDatabasesSection.updateCollapseState(isCollapsed);
	workspaceSection.updateCollapseState(isCollapsed);
	revalidate();
	repaint();
}

}

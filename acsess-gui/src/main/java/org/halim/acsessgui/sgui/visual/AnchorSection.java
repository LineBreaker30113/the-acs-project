package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.sgui.LeftSidebar;
import org.halim.acsessgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.util.ArrayList;


/** The sidebar's anchors: collapse, and the welcome page. */
public class AnchorSection extends JPanel {

final ArrayList<JButton> buttons = new ArrayList<>();

public AnchorSection(LeftSidebar leftSidebar) {
	setBackground(leftSidebar.getBackground().brighter());
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	JButton collapseBtn = Utilities.sidebarButton("Toggle Sidebar", "arrow-line-left.svg", "caret-right.svg");
	collapseBtn.addActionListener(e -> leftSidebar.toggleCollapse());
	JButton welcomeBtn = Utilities.sidebarButton("Welcome Page", "house.svg", "house.svg");
	welcomeBtn.addActionListener(e -> leftSidebar.owner.centerPanel.showPage("WELCOME"));
	buttons.add(collapseBtn);
	buttons.add(welcomeBtn);
	add(collapseBtn);
	add(Box.createVerticalStrut(8));
	add(welcomeBtn);
}

public void updateCollapseState(boolean isCollapsed) {
	for(JButton btn : buttons) { Utilities.updateCollapseState(btn, isCollapsed); }
}

}

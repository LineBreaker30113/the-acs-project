package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.sgui.LeftSidebar;
import org.halim.acsgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.util.ArrayList;


/** The sidebar's anchors: collapse, the welcome page, and the instruction manual. */
public class AnchorSection extends JPanel {

final ArrayList<JButton> buttons = new ArrayList<>();

public AnchorSection(LeftSidebar leftSidebar) {
	setBackground(leftSidebar.getBackground().brighter());
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	JButton collapseBtn = Utilities.sidebarButton("Toggle Sidebar", "arrow-line-left.svg", "caret-right.svg");
	collapseBtn.addActionListener(e -> leftSidebar.toggleCollapse());
	JButton welcomeBtn = Utilities.sidebarButton("Welcome Page", "house.svg", "house.svg");
	welcomeBtn.addActionListener(e -> leftSidebar.owner.centerPanel.showPage("WELCOME"));
	JButton instructionsBtn = Utilities.sidebarButton("Instruction Manual", "book-open-text.svg", "book-open-text.svg");
	instructionsBtn.addActionListener(e -> leftSidebar.owner.centerPanel.showPage("INSTRUCTIONS"));
	buttons.add(collapseBtn);
	buttons.add(welcomeBtn);
	buttons.add(instructionsBtn);
	add(collapseBtn);
	add(Box.createVerticalStrut(8));
	add(welcomeBtn);
	add(Box.createVerticalStrut(8));
	add(instructionsBtn);
}

public void updateCollapseState(boolean isCollapsed) {
	for(JButton btn : buttons) { Utilities.updateCollapseState(btn, isCollapsed); }
}

}

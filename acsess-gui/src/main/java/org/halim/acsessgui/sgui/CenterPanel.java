package org.halim.acsessgui.sgui;

import org.halim.acsessgui.sgui.visual.InstructionsPage;
import org.halim.acsessgui.sgui.visual.WelcomePage;

import javax.swing.*;
import java.awt.*;


/** The pages, one shown at a time: the welcome page, the instruction manual, and the workspace of the shown database. */
public class CenterPanel extends JPanel {

public final GUI_RootPanel owner;
public final CardLayout layout;
public final WelcomePage welcomePage;

public CenterPanel(GUI_RootPanel owner) {
	this.owner = owner;
	this.layout = new CardLayout();
	setLayout(layout);
	this.welcomePage = new WelcomePage(owner);
	add(welcomePage, "WELCOME");
	add(new InstructionsPage(), "INSTRUCTIONS");
}

public void showPage(String pageName) { layout.show(this, pageName); }

}

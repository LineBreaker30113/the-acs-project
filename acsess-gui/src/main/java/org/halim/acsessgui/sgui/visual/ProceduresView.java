package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;


/** The procedures: they wait for their requests, since acsess has none of a procedure and components lists none. */
public class ProceduresView extends WorkSpaceViewPanel {

public ProceduresView(ApplicationController app) {
	super(app);
	JLabel text = new JLabel("<html>The procedures wait for their requests: acsess has none of a procedure, and components lists none.</html>");
	text.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
	text.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	add(text, BorderLayout.NORTH);
	setPreferredSize(new Dimension(280, 600));
}

}

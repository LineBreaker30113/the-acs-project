package org.halim.acsessgui.sgui.sglib;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.ApplicationController;

import javax.swing.*;
import java.awt.*;


/** A workspace view: it hears the workspace and reads through the controller's request thread; what it does not care about it leaves as a no-op. */
public abstract class WorkSpaceViewPanel extends JPanel implements WorkspaceListener {

protected final ApplicationController app;

protected WorkSpaceViewPanel(ApplicationController app) {
	this.app = app;
	setLayout(new BorderLayout());
	setBackground(Utilities.WP_BG);
}

protected ACSESS_GUI_Service.DatabaseService database() { return app.servicePort.getActiveDatabase(); }

@Override public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) { }

@Override public void onFocusChange(WorkspaceElement focused) { }

}

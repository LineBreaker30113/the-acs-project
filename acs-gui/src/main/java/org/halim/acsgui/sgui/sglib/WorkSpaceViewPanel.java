package org.halim.acsgui.sgui.sglib;

import org.halim.acsgui.mhexagon.ACSGUI_Service;
import org.halim.acsgui.sgui.ApplicationController;

import javax.swing.*;
import java.awt.*;


/** A workspace view: it hears the workspace and reads through the controller's request thread; what it does not care about it leaves as a no-op. */
public abstract class WorkSpaceViewPanel extends JPanel implements WorkspaceListener {

protected final ApplicationController app;
/** Called when the view shows something new; the header it sits in flashes. */
public Runnable updated = () -> { };

protected WorkSpaceViewPanel(ApplicationController app) {
	this.app = app;
	setLayout(new BorderLayout());
	setBackground(Utilities.WP_BG);
}

protected ACSGUI_Service.DatabaseService database() { return app.servicePort.getActiveDatabase(); }

@Override public void onSelectedDatabaseChange(ACSGUI_Service.DatabaseService database) { }

@Override public void onFocusChange(WorkspaceElement focused) { }

}

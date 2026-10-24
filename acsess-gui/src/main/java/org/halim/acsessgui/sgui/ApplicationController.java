package org.halim.acsessgui.sgui;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * Deploys the Swing frame and orchestrates it, as OntoDirectory's ApplicationController does, talking to
 * acsed only through {@link ACSESS_GUI_Service}. Where that one owns the debounced-save thread, this one owns
 * the request thread: every request runs there, one at a time and in the order asked, since each is a
 * program run long enough to freeze the window, and two runs at once could interleave their changes.
 * */
public class ApplicationController {

public final ACSESS_GUI_Service servicePort;
public GUI_RootPanel view;
public final WorkspaceController wsModeller;

final ExecutorService requests = Executors.newSingleThreadExecutor(r -> {
	Thread thread = new Thread(r, "ACSESS_GUI-Request-Thread");
	thread.setDaemon(true);
	return thread;
});

public ApplicationController(ACSESS_GUI_Service servicePort) {
	this.servicePort = servicePort;
	this.wsModeller = new WorkspaceController(this);
	SwingUtilities.invokeLater(() -> {
		this.view = new GUI_RootPanel(this);
		this.servicePort.addACSESS_GUI_ServiceListener(new ServiceListenerImpl());
		request("boot acsed", servicePort::boot, view.centerPanel.welcomePage::showDaemonState);
	});
}

public void deployTo(JFrame applicationWindow) {
	applicationWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	applicationWindow.add(view);
	applicationWindow.pack();
	applicationWindow.setLocationRelativeTo(null);
	applicationWindow.setVisible(true);
}

/**
 * Runs the work on the request thread and hands its result to {@code done} on the event thread; a
 * refusal goes to the service's listeners instead, named by {@code action}.
 * */
public <T> void request(String action, Supplier<T> work, Consumer<T> done) {
	requests.execute(() -> {
		T result;
		try { result = work.get(); }
		catch(RuntimeException e) {
			servicePort.reportRefusal(action, e);
			return;
		}
		SwingUtilities.invokeLater(() -> done.accept(result));
	});
}

/** The same for work that answers nothing. */
public void perform(String action, Runnable work, Runnable done) {
	request(action, () -> { work.run(); return null; }, ignored -> done.run());
}

/** Stores and unloads the database, then shows the welcome page. */
public void dispatchDatabaseCloseRequest(ACSESS_GUI_Service.DatabaseService database) {
	perform("close " + database.getName(), () -> servicePort.closeDatabase(database), () -> {
		view.leftSidebar.loadedDatabasesSection.refresh();
		wsModeller.triggerDatabaseRefresh(null);
		view.centerPanel.showPage("WELCOME");
	});
}

/** Undo and redo of the shown database, as the hotkeys and the journal view ask them. */
public void undo() {
	ACSESS_GUI_Service.DatabaseService database = servicePort.getActiveDatabase();
	if(database != null) { perform("undo", database::undo, () -> wsModeller.triggerDatabaseRefresh(database)); }
}

public void redo() {
	ACSESS_GUI_Service.DatabaseService database = servicePort.getActiveDatabase();
	if(database != null) { perform("redo", database::redo, () -> wsModeller.triggerDatabaseRefresh(database)); }
}

class ServiceListenerImpl implements ACSESS_GUI_Service.ACSESS_GUI_ServiceListener {

	@Override
	public void onGUI_Change() {
		SwingUtilities.invokeLater(() -> {
			view.centerPanel.welcomePage.refreshDatabases();
			view.leftSidebar.loadedDatabasesSection.refresh();
			view.revalidate();
			view.repaint();
		});
	}

	@Override
	public void onDatabaseLoad(ACSESS_GUI_Service.DatabaseService database) {
		SwingUtilities.invokeLater(() -> {
			view.leftSidebar.loadedDatabasesSection.refresh();
			view.centerPanel.welcomePage.refreshDatabases();
			view.centerPanel.showPage("WORKSPACE");
			wsModeller.triggerDatabaseRefresh(database);
		});
	}

	@Override
	public void showRefusal(String action, String reason) {
		SwingUtilities.invokeLater(() -> {
			JTextArea text = new JTextArea(reason, Math.min(12, Math.max(2, reason.split("\n").length)), 60);
			text.setEditable(false);
			text.setLineWrap(true);
			text.setWrapStyleWord(true);
			JOptionPane.showMessageDialog(view, new JScrollPane(text), "Refused: " + action, JOptionPane.WARNING_MESSAGE);
		});
	}
}

}

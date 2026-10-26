package org.halim.acsessgui.sgui;

import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.visual.ContentPanel;
import org.halim.acsessgui.sgui.visual.KernelPanel;
import org.halim.acsessgui.sgui.visual.ListerPanel;
import org.halim.acsessgui.sgui.visual.WorkPanelWrapper;
import org.halim.acsessgui.sgui.visual.WorkspaceRow;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;


/**
 * The views in rows of columns, each shown, hidden and moved by the sidebar's toggle and its own arrows,
 * as in OntoDirectory's WorkspacePanel, which had the one row: left and right within a row, up and down
 * across the rows; moving down from the last row makes a new one, and an emptied row goes. The rows share
 * the height at first and are divided by dividers the user drags; the views of a row share its width. A kernel panel, a lister
 * and a content panel are opened anew by each press of their buttons and closed by their own, since any
 * number may be open. The workspace opens with a kernel panel on the core, one on the journal, and a
 * lister of the arities.
 * */
public class WorkspacePanel extends JPanel {

public static final String kernelVN = "kernelV", listerVN = "listerV", contentVN = "contentV";

final ArrayList<WorkPanelWrapper> allPanels = new ArrayList<>();
final ArrayList<WorkspaceRow> rows = new ArrayList<>();
public final GUI_RootPanel owner;
int kernelCount = 0, listerCount = 0, contentCount = 0;

public WorkspacePanel(GUI_RootPanel owner) {
	this.owner = owner;
	setLayout(new BorderLayout());
	SwingUtilities.invokeLater(this::initViews);
}

void initViews() {
	openKernelPanel().choose(KernelPanel.Kind.CORE);
	openKernelPanel().choose(KernelPanel.Kind.JOURNAL);
	openListerPanel().choose(ListerPanel.Kind.ARITY_LIST);
}

void view(String name, String niceTitle, WorkSpaceViewPanel view) {
	owner.appController.wsModeller.registerListener(view);
	allPanels.add(new WorkPanelWrapper(name, niceTitle, view, this));
}

/** A new kernel panel, in the last row, showing nothing until it is told which type. */
public KernelPanel openKernelPanel() {
	ApplicationController app = owner.appController;
	KernelPanel panel = new KernelPanel(app);
	app.wsModeller.registerListener(panel);
	WorkPanelWrapper[] wrapper = new WorkPanelWrapper[1];
	wrapper[0] = new WorkPanelWrapper(kernelVN + (++kernelCount), "Kernel", panel, this, () -> {
		closePanel(wrapper[0]);
		app.wsModeller.removeListener(panel);
	});
	panel.heading = wrapper[0]::setTitle;
	wrapper[0].setLeftButtons(panel.getChooser());
	reopenPanel(wrapper[0]);
	return panel;
}

/** A new lister, in the last row, listing nothing until it is told what. */
public ListerPanel openListerPanel() {
	ApplicationController app = owner.appController;
	ListerPanel panel = new ListerPanel(app);
	app.wsModeller.registerListener(panel);
	WorkPanelWrapper[] wrapper = new WorkPanelWrapper[1];
	wrapper[0] = new WorkPanelWrapper(listerVN + (++listerCount), "Lister", panel, this, () -> {
		closePanel(wrapper[0]);
		app.wsModeller.removeListener(panel);
	});
	wrapper[0].setLeftButtons(panel.getChooser(), panel.getTranspose());
	panel.heading = wrapper[0]::setTitle;
	reopenPanel(wrapper[0]);
	return panel;
}

/** A new content panel, in the last row, reading the shown database at once. */
public ContentPanel openContentPanel() {
	ApplicationController app = owner.appController;
	ContentPanel panel = new ContentPanel(app);
	app.wsModeller.registerContent(panel);
	WorkPanelWrapper[] wrapper = new WorkPanelWrapper[1];
	wrapper[0] = new WorkPanelWrapper(contentVN + (++contentCount), "Content", panel, this, () -> {
		closePanel(wrapper[0]);
		app.wsModeller.removeContent(panel);
	});
	panel.heading = wrapper[0]::setTitle;
	wrapper[0].setLeftButtons(panel.getChooser(), panel.getTranspose());
	reopenPanel(wrapper[0]);
	panel.onSelectedDatabaseChange(app.servicePort.getActiveDatabase());
	return panel;
}

WorkspaceRow rowOf(WorkPanelWrapper wrapper) {
	for(WorkspaceRow row : rows) { if(row.panels.contains(wrapper)) { return row; } }
	return null;
}

public void closePanel(WorkPanelWrapper wrapper) {
	WorkspaceRow row = rowOf(wrapper);
	if(row == null) { return; }
	row.panels.remove(wrapper);
	if(row.panels.isEmpty()) { rows.remove(row); }
	renderRows();
}

/** Into the last row, made when there is none. */
public void reopenPanel(WorkPanelWrapper wrapper) {
	if(rows.isEmpty()) { rows.add(new WorkspaceRow()); }
	rows.get(rows.size() - 1).panels.add(wrapper);
	renderRows();
}

public void panelToggled(String pname) {
	WorkPanelWrapper wrapper = allPanels.stream().filter(w -> w.title.equals(pname)).findFirst().orElse(null);
	if(wrapper == null) { return; }
	if(rowOf(wrapper) != null) { closePanel(wrapper); } else { reopenPanel(wrapper); }
}

/** Moves a view left (-1) or right (1) within its row. */
public void shiftPanel(WorkPanelWrapper wrapper, int direction) {
	WorkspaceRow row = rowOf(wrapper);
	if(row == null) { return; }
	int currentIndex = row.panels.indexOf(wrapper);
	int newIndex = currentIndex + direction;
	if(newIndex < 0 || newIndex >= row.panels.size()) { return; }
	Collections.swap(row.panels, currentIndex, newIndex);
	renderRows();
}

/** Moves a view to the end of the row above (-1) or below (1); below the last row, a new row is made. */
public void shiftRow(WorkPanelWrapper wrapper, int direction) {
	WorkspaceRow row = rowOf(wrapper);
	if(row == null) { return; }
	int target = rows.indexOf(row) + direction;
	if(target < 0) { return; }
	if(target >= rows.size()) { rows.add(new WorkspaceRow()); }
	WorkspaceRow destination = rows.get(target);
	row.panels.remove(wrapper);
	destination.panels.add(wrapper);
	if(row.panels.isEmpty()) { rows.remove(row); }
	renderRows();
}

void renderRows() {
	removeAll();
	for(WorkspaceRow row : rows) { row.render(); }
	if(!rows.isEmpty()) { add(split(0), BorderLayout.CENTER); }
	revalidate();
	repaint();
}

/** The rows from {@code from} down, each pair divided by a divider the user drags; they start sharing the height equally. */
JComponent split(int from) {
	if(from == rows.size() - 1) { return rows.get(from); }
	JSplitPane divided = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rows.get(from), split(from + 1));
	double share = 1.0 / (rows.size() - from);
	divided.setResizeWeight(share);
	divided.setBorder(null);
	divided.setDividerSize(6);
	divided.setContinuousLayout(true);
	SwingUtilities.invokeLater(() -> divided.setDividerLocation(share));
	return divided;
}

}

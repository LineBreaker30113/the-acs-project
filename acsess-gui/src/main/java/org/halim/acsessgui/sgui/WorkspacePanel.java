package org.halim.acsessgui.sgui;

import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.visual.AritiesView;
import org.halim.acsessgui.sgui.visual.ArityTableView;
import org.halim.acsessgui.sgui.visual.ClassesView;
import org.halim.acsessgui.sgui.visual.CodeView;
import org.halim.acsessgui.sgui.visual.ConsoleView;
import org.halim.acsessgui.sgui.visual.ContentPanel;
import org.halim.acsessgui.sgui.visual.CoreView;
import org.halim.acsessgui.sgui.visual.JournalView;
import org.halim.acsessgui.sgui.visual.ProceduresView;
import org.halim.acsessgui.sgui.visual.StructureView;
import org.halim.acsessgui.sgui.visual.StructuresView;
import org.halim.acsessgui.sgui.visual.WorkPanelWrapper;
import org.halim.acsessgui.sgui.visual.WorkspaceRow;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;


/**
 * The views in rows of columns, each shown, hidden and moved by the sidebar's toggles and its own arrows,
 * as in OntoDirectory's WorkspacePanel, which had the one row: left and right within a row, up and down
 * across the rows; moving down from the last row makes a new one, and an emptied row goes. The rows share
 * the height, the views of a row its width. A content panel is opened anew by each press of its toggle
 * and closed by its own button, since any number may be open.
 * */
public class WorkspacePanel extends JPanel {

public static final String coreVN = "coreV", journalVN = "journalV", classesVN = "classesV", codeVN = "codeV", aritiesVN = "aritiesV",
	arityTableVN = "arityTableV", structuresVN = "structuresV", structureVN = "structureV", proceduresVN = "proceduresV", consoleVN = "consoleV", contentVN = "contentV";

final ArrayList<WorkPanelWrapper> allPanels = new ArrayList<>();
final ArrayList<WorkspaceRow> rows = new ArrayList<>();
public final GUI_RootPanel owner;
int contentCount = 0;

public WorkspacePanel(GUI_RootPanel owner) {
	this.owner = owner;
	setLayout(new GridLayout(0, 1));
	SwingUtilities.invokeLater(this::initViews);
}

void initViews() {
	ApplicationController app = owner.appController;
	view(coreVN, "Core", new CoreView(app));
	view(journalVN, "Journal", new JournalView(app));
	view(classesVN, "Classes", new ClassesView(app));
	view(codeVN, "Code", new CodeView(app));
	view(aritiesVN, "Arities", new AritiesView(app));
	view(arityTableVN, "Arity Table", new ArityTableView(app));
	view(structuresVN, "Structures", new StructuresView(app));
	view(structureVN, "Structure", new StructureView(app));
	view(proceduresVN, "Procedures", new ProceduresView(app));
	view(consoleVN, "Request Console", new ConsoleView(app));

	panelToggled(aritiesVN);
	panelToggled(arityTableVN);
}

void view(String name, String niceTitle, WorkSpaceViewPanel view) {
	owner.appController.wsModeller.registerListener(view);
	allPanels.add(new WorkPanelWrapper(name, niceTitle, view, this));
}

/** A new content panel, in the last row, reading the shown database at once. */
public void openContentPanel() {
	ApplicationController app = owner.appController;
	ContentPanel panel = new ContentPanel(app);
	app.wsModeller.registerContent(panel);
	WorkPanelWrapper[] wrapper = new WorkPanelWrapper[1];
	wrapper[0] = new WorkPanelWrapper(contentVN + (++contentCount), "Content", panel, this, () -> {
		closePanel(wrapper[0]);
		app.wsModeller.removeContent(panel);
	});
	reopenPanel(wrapper[0]);
	panel.onSelectedDatabaseChange(app.servicePort.getActiveDatabase());
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
	for(WorkspaceRow row : rows) {
		row.render();
		add(row);
	}
	revalidate();
	repaint();
}

}

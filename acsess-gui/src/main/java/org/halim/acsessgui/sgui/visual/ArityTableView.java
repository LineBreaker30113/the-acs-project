package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Row;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.ValueType;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * The arity chosen in the arities view as a table, as an SQL tool shows a table's rows: an identity per line and a column per
 * row of the arity, a page at a time. A cell is edited in place and written by one set request, so one
 * journal activity; a line is added by capturing an identity, and deleted by delegateRemoval, which lets
 * the structures holding the identity let go of it first.
 * <p>
 * A page is read as one acsess session, so its cost is one program run and a probe per cell; a cell
 * acsed refuses shows the reason in parentheses. A row whose type acsess cannot read shows "(object)" and
 * is not editable.
 * <p>
 * An identity's cell, the first of its line, and a row's column header are elements: clicked to focus,
 * and dragged onto a content panel.
 * */
public class ArityTableView extends WorkSpaceViewPanel {

static final int PAGE_SIZE = 100;

final JLabel title = new JLabel("Choose an arity in the arities view.");
final JLabel pageLabel = new JLabel(" ");
final GridModel model = new GridModel();
final JTable table = new JTable(model);
int arity = -1;
int page = 0;
Arity shown;
long[] identities = new long[0];

class GridModel extends AbstractTableModel {
	List<Row> rows = List.of();
	long[] pageIdentities = new long[0];
	String[][] values = new String[0][0];

	@Override public int getRowCount() { return pageIdentities.length; }

	@Override public int getColumnCount() { return rows.size() + 1; }

	@Override
	public String getColumnName(int column) {
		if(column == 0) { return "identity"; }
		Row row = rows.get(column - 1);
		return (row.description() == null ? "row " + row.slot() : row.description()) + " : " + row.type().name().toLowerCase();
	}

	@Override
	public Object getValueAt(int line, int column) {
		if(column == 0) { return pageIdentities[line]; }
		String value = values[line][column - 1];
		return value == null ? "(object)" : value;
	}

	@Override
	public boolean isCellEditable(int line, int column) {
		return column > 0 && rows.get(column - 1).type() != ValueType.OBJECT && values[line][column - 1] != null;
	}

	@Override
	public void setValueAt(Object edited, int line, int column) {
		String value = String.valueOf(edited);
		if(value.equals(values[line][column - 1])) { return; }
		ACSESS_GUI_Service.DatabaseService database = database();
		Row row = rows.get(column - 1);
		long identity = pageIdentities[line];
		int at = arity;
		app.perform("set row " + row.slot() + " of identity " + identity, () -> database.setValue(at, row, identity, value), () -> app.wsModeller.triggerDatabaseRefresh(database));
	}
}

public ArityTableView(ApplicationController app) {
	super(app);
	JPanel top = new JPanel(new BorderLayout());
	top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	top.add(title, BorderLayout.NORTH);

	JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
	JButton capture = new JButton("+ New Identity");
	capture.addActionListener(e -> captureIdentity());
	JButton delete = new JButton("Delete Selected");
	delete.addActionListener(e -> deleteSelected());
	JButton refresh = new JButton("Refresh");
	refresh.addActionListener(e -> reload());
	JButton previous = new JButton("<");
	previous.addActionListener(e -> turn(-1));
	JButton next = new JButton(">");
	next.addActionListener(e -> turn(1));
	for(JComponent tool : List.<JComponent>of(capture, delete, refresh, previous, pageLabel, next)) { tools.add(tool); }
	top.add(tools, BorderLayout.SOUTH);
	add(top, BorderLayout.NORTH);

	table.setFillsViewportHeight(true);
	table.setAutoCreateRowSorter(false);
	table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
	table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int line, int column) {
			Component cell = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, line, column);
			String text = String.valueOf(value);
			if(!isSelected) { cell.setForeground(column > 0 && text.startsWith("(") && text.endsWith(")") ? Utilities.REFUSED_COLOR : Utilities.ELEMENT_TEXT_PRIMARY); }
			return cell;
		}
	});
	add(new JScrollPane(table), BorderLayout.CENTER);
	WorkspaceElement.Transfer.source(table, point -> {
		int line = table.rowAtPoint(point);
		return line < 0 || table.columnAtPoint(point) != 0 || shown == null ? null : WorkspaceElement.identity(arity, model.pageIdentities[line]);
	}, app.wsModeller::click);
	JTableHeader header = table.getTableHeader();
	header.setReorderingAllowed(false);
	WorkspaceElement.Transfer.source(header, point -> {
		int column = header.columnAtPoint(point);
		if(column <= 0 || shown == null || header.getCursor().getType() == Cursor.E_RESIZE_CURSOR) { return null; }
		return WorkspaceElement.row(arity, model.rows.get(column - 1).slot());
	}, app.wsModeller::click);
	setPreferredSize(new Dimension(640, 600));
}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null) {
		arity = -1;
		clear("Choose an arity in the arities view.");
		return;
	}
	if(arity >= 0) { reload(); }
}

@Override
public void onSelectedArityChange(int chosen) {
	if(chosen == arity) { return; }
	arity = chosen;
	page = 0;
	reload();
}

void clear(String text) {
	title.setText(text);
	shown = null;
	identities = new long[0];
	model.rows = List.of();
	model.pageIdentities = new long[0];
	model.values = new String[0][0];
	model.fireTableStructureChanged();
	pageLabel.setText(" ");
}

record Page(Arity arity, long[] identities, long[] pageIdentities, String[][] values) {}

/** Reads the components, the identities and one page of values, in that order, on the request thread. */
void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || arity < 0) { return; }
	int at = arity, wanted = page;
	app.request("read arity " + at, () -> {
		Arity found = database.getComponents().arities().stream().filter(a -> a.slot() == at).findFirst().orElse(null);
		if(found == null || found.isRemoved()) { return new Page(null, new long[0], new long[0], new String[0][0]); }
		long[] all = database.getIdentities(at);
		int from = Math.min(wanted * PAGE_SIZE, all.length), to = Math.min(from + PAGE_SIZE, all.length);
		long[] pageIdentities = Arrays.copyOfRange(all, from, to);
		List<Row> live = found.rows().stream().filter(r -> !r.isRemoved()).toList();
		return new Page(found, all, pageIdentities, database.getValues(at, live, pageIdentities));
	}, this::show);
}

void show(Page loaded) {
	if(loaded.arity == null) {
		clear("Arity " + arity + " is not in the database.");
		return;
	}
	shown = loaded.arity;
	identities = loaded.identities;
	model.rows = loaded.arity.rows().stream().filter(r -> !r.isRemoved()).toList();
	model.pageIdentities = loaded.pageIdentities;
	model.values = loaded.values;
	model.fireTableStructureChanged();
	title.setText("<html><b>" + WelcomePage.escaped(database().getName()) + "</b> : arity " + arity + " : " + WelcomePage.escaped(Utilities.described(shown.description())) + "</html>");
	int pages = Math.max(1, (identities.length + PAGE_SIZE - 1) / PAGE_SIZE);
	pageLabel.setText("page " + (page + 1) + " of " + pages + ", " + identities.length + (identities.length == 1 ? " identity" : " identities"));
}

void turn(int direction) {
	int pages = Math.max(1, (identities.length + PAGE_SIZE - 1) / PAGE_SIZE);
	int wanted = page + direction;
	if(wanted < 0 || wanted >= pages) { return; }
	page = wanted;
	reload();
}

void captureIdentity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || shown == null) { return; }
	int at = arity;
	app.request("capture an identity in arity " + at, () -> database.captureIdentity(at), identity -> app.wsModeller.triggerDatabaseRefresh(database));
}

void deleteSelected() {
	ACSESS_GUI_Service.DatabaseService database = database();
	int[] lines = table.getSelectedRows();
	if(database == null || lines.length == 0) { return; }
	ArrayList<Long> chosen = new ArrayList<>();
	for(int line : lines) { chosen.add(model.pageIdentities[line]); }
	if(JOptionPane.showConfirmDialog(this, "Delete " + chosen.size() + (chosen.size() == 1 ? " identity" : " identities") + " from arity " + arity + "? Each is one journal activity, undone one at a time.", "Confirm Deletion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
	int at = arity;
	app.perform("delete identities of arity " + at, () -> { for(long identity : chosen) { database.deleteIdentity(at, identity); } }, () -> app.wsModeller.triggerDatabaseRefresh(database));
}

}

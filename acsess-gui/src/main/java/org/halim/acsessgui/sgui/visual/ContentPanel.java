package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Row;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Structure;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement.Type;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A content panel: the selection at its top chooses the type it listens for, and it shows one element of
 * that type. Only one panel listens for a type; choosing a type another panel holds swaps the two panels'
 * states. It takes an element by a click on the focused element of its type, or by a drop of one, and
 * never follows the focus by itself. It shows and does not edit: editing stays in the arity table view.
 * <p>
 * Its lines are elements too, clicked and dragged as any view's: an arity's rows, a row's identities, an
 * identity's rows, a structure's arities. A row's values are read a page at a time, as the arity table
 * reads them, and every read goes through the kept data, so a panel showing what a view already read costs
 * no program run.
 * */
public class ContentPanel extends WorkSpaceViewPanel {

static final int PAGE_SIZE = 100;
static final String NO_TYPE = "(choose a type)";

final JComboBox<Object> selection = new JComboBox<>();
final JLabel title = new JLabel(" ");
final JLabel focus = new JLabel(" ");
final JLabel pageLabel = new JLabel(" ");
final JPanel pager = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
final LineModel model = new LineModel();
final JTable table = new JTable(model);
Type type;
WorkspaceElement element;
String databaseName;
int page = 0, pages = 1;
boolean selecting;

/** What a panel shows: a title, and lines, each the element it is when clicked or dragged, or null. */
record Shown(String title, String[] columns, Object[][] cells, WorkspaceElement[] elements, int pages) {
	static Shown note(String title) { return new Shown(title, new String[0], new Object[0][0], new WorkspaceElement[0], 1); }
}

class LineModel extends AbstractTableModel {
	Shown shown = Shown.note(" ");

	@Override public int getRowCount() { return shown.cells.length; }

	@Override public int getColumnCount() { return shown.columns.length; }

	@Override public String getColumnName(int column) { return shown.columns[column]; }

	@Override public Object getValueAt(int line, int column) { return shown.cells[line][column]; }
}

public ContentPanel(ApplicationController app) {
	super(app);
	selection.addItem(NO_TYPE);
	for(Type each : Type.values()) { selection.addItem(each); }
	selection.addActionListener(e -> {
		if(selecting) { return; }
		Object chosen = selection.getSelectedItem();
		if(chosen instanceof Type wanted) { app.wsModeller.claim(this, wanted); } else { assume(null, null); }
	});

	JPanel top = new JPanel(new BorderLayout(0, 4));
	top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	top.add(selection, BorderLayout.NORTH);
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	focus.setForeground(Utilities.ELEMENT_TEXT_SECONDARY);
	top.add(title, BorderLayout.CENTER);
	JPanel bottom = new JPanel(new BorderLayout());
	bottom.setOpaque(false);
	bottom.add(focus, BorderLayout.NORTH);
	JButton previous = new JButton("<");
	previous.addActionListener(e -> turn(-1));
	JButton next = new JButton(">");
	next.addActionListener(e -> turn(1));
	pager.add(previous);
	pager.add(pageLabel);
	pager.add(next);
	pager.setVisible(false);
	bottom.add(pager, BorderLayout.SOUTH);
	top.add(bottom, BorderLayout.SOUTH);
	add(top, BorderLayout.NORTH);

	table.setFillsViewportHeight(true);
	table.setDefaultEditor(Object.class, null);
	JScrollPane scroll = new JScrollPane(table);
	add(scroll, BorderLayout.CENTER);
	WorkspaceElement.Transfer.install(table, point -> {
		int line = table.rowAtPoint(point);
		return line < 0 ? null : model.shown.elements[line];
	}, app.wsModeller::click, this::accepts, this::take);
	WorkspaceElement.Transfer.target(this, this::accepts, this::take);
	WorkspaceElement.Transfer.target(top, this::accepts, this::take);
	setPreferredSize(new Dimension(420, 600));
	show(Shown.note("Choose a type, then click a focused element of it twice, or drop one here."));
}

public Type getType() { return type; }

public WorkspaceElement getElement() { return element; }

boolean accepts(WorkspaceElement dropped) { return type != null && type != Type.PROCEDURE && dropped.type() == type; }

/** The element, clicked once focused or dropped here, if of this panel's type. */
public void take(WorkspaceElement taken) {
	if(!accepts(taken)) { return; }
	element = taken;
	page = 0;
	ACSESS_GUI_Service.DatabaseService database = database();
	databaseName = database == null ? null : database.getName();
	reload();
}

/** The state the workspace gives this panel when it claims a type, or swaps with the panel that held it. */
public void assume(Type assumed, WorkspaceElement held) {
	type = assumed;
	element = held != null && held.type() == assumed ? held : null;
	page = 0;
	selecting = true;
	selection.setSelectedItem(assumed == null ? NO_TYPE : assumed);
	selecting = false;
	showFocus(app.wsModeller.getFocused());
	reload();
}

@Override
public void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database) {
	if(database == null || (databaseName != null && !databaseName.equals(database.getName()))) {
		element = null;
		databaseName = null;
	}
	showFocus(app.wsModeller.getFocused());
	reload();
}

@Override public void onFocusChange(WorkspaceElement focused) { showFocus(focused); }

void showFocus(WorkspaceElement focused) {
	if(focused == null) { focus.setText("focused: nothing"); return; }
	focus.setText("focused: " + focused + (type != null && focused.type() == type ? " (click it again to show it here)" : ""));
}

void turn(int direction) {
	int wanted = page + direction;
	if(wanted < 0 || wanted >= pages) { return; }
	page = wanted;
	reload();
}

void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(type == null) { show(Shown.note("Choose a type, then click a focused element of it twice, or drop one here.")); return; }
	if(type == Type.PROCEDURE) { show(Shown.note("A procedure waits for its requests: acsess has none of a procedure, and components lists none.")); return; }
	if(database == null) { show(Shown.note("No database is shown.")); return; }
	if(element == null) { show(Shown.note("Click a focused " + type + " twice, or drop one here.")); return; }
	WorkspaceElement shownElement = element;
	int wanted = page;
	app.request("read " + shownElement + " for a content panel", () -> read(database, shownElement, wanted), shown -> {
		if(shownElement.equals(element)) { show(shown); }
	});
}

void show(Shown shown) {
	model.shown = shown;
	model.fireTableStructureChanged();
	title.setText("<html>" + WelcomePage.escaped(shown.title) + "</html>");
	pages = shown.pages;
	pager.setVisible(type == Type.ARITY_ROW && element != null);
	pageLabel.setText("page " + (page + 1) + " of " + pages);
}

/** Runs on the request thread. */
static Shown read(ACSESS_GUI_Service.DatabaseService database, WorkspaceElement element, int page) {
	Components components = database.getComponents();
	if(element.type() == Type.STRUCTURE) {
		Structure structure = components.structures().stream().filter(s -> s.slot() == element.structure()).findFirst().orElse(null);
		if(structure == null || structure.isRemoved()) { return Shown.note(element + " is not in the database."); }
		ArrayList<Object[]> cells = new ArrayList<>();
		ArrayList<WorkspaceElement> elements = new ArrayList<>();
		cells.add(new Object[] { "class", structure.className() });
		elements.add(null);
		cells.add(new Object[] { "description", Utilities.described(structure.description()) });
		elements.add(null);
		for(int spanned : structure.arities()) {
			Arity arity = arityOf(components, spanned);
			cells.add(new Object[] { "spans", "arity " + spanned + ": " + (arity == null || arity.isRemoved() ? "(not in the database)" : Utilities.described(arity.description())) });
			elements.add(WorkspaceElement.arity(spanned));
		}
		return new Shown(element + ": " + Utilities.described(structure.description()), new String[] { "field", "value" }, cells.toArray(new Object[0][]), elements.toArray(new WorkspaceElement[0]), 1);
	}
	Arity arity = arityOf(components, element.arity());
	if(arity == null || arity.isRemoved()) { return Shown.note("arity " + element.arity() + " is not in the database."); }
	List<Row> live = arity.rows().stream().filter(r -> !r.isRemoved()).toList();
	switch(element.type()) {
		case ARITY -> {
			Object[][] cells = new Object[live.size()][];
			WorkspaceElement[] elements = new WorkspaceElement[live.size()];
			for(int i = 0; i < live.size(); i++) {
				Row row = live.get(i);
				cells[i] = new Object[] { row.slot(), Utilities.described(row.description()), Utilities.simple(row.kind()), row.type().name().toLowerCase() };
				elements[i] = WorkspaceElement.row(arity.slot(), row.slot());
			}
			String count = arity.identities() + (arity.identities() == 1 ? " identity" : " identities");
			return new Shown(element + ": " + Utilities.described(arity.description()) + ", " + count, new String[] { "row", "description", "class", "type" }, cells, elements, 1);
		}
		case ARITY_ROW -> {
			Row row = live.stream().filter(r -> r.slot() == element.row()).findFirst().orElse(null);
			if(row == null) { return Shown.note(element + " is not in the database."); }
			long[] all = database.getIdentities(arity.slot());
			int pageCount = Math.max(1, (all.length + PAGE_SIZE - 1) / PAGE_SIZE);
			int from = Math.min(page * PAGE_SIZE, all.length), to = Math.min(from + PAGE_SIZE, all.length);
			long[] identities = Arrays.copyOfRange(all, from, to);
			String[][] values = database.getValues(arity.slot(), List.of(row), identities);
			Object[][] cells = new Object[identities.length][];
			WorkspaceElement[] elements = new WorkspaceElement[identities.length];
			for(int i = 0; i < identities.length; i++) {
				cells[i] = new Object[] { identities[i], values[i][0] == null ? "(object)" : values[i][0] };
				elements[i] = WorkspaceElement.identity(arity.slot(), identities[i]);
			}
			String head = element + ": " + Utilities.described(row.description()) + " (" + row.type().name().toLowerCase() + "), " + all.length + (all.length == 1 ? " identity" : " identities");
			return new Shown(head, new String[] { "identity", "value" }, cells, elements, pageCount);
		}
		case ARITY_IDENTITY -> {
			String[][] values = database.getValues(arity.slot(), live, new long[] { element.identity() });
			Object[][] cells = new Object[live.size()][];
			WorkspaceElement[] elements = new WorkspaceElement[live.size()];
			for(int i = 0; i < live.size(); i++) {
				Row row = live.get(i);
				cells[i] = new Object[] { row.slot(), Utilities.described(row.description()), values[0][i] == null ? "(object)" : values[0][i] };
				elements[i] = WorkspaceElement.row(arity.slot(), row.slot());
			}
			return new Shown(element.toString(), new String[] { "row", "description", "value" }, cells, elements, 1);
		}
		default -> { return Shown.note(element + " has nothing to show."); }
	}
}

static Arity arityOf(Components components, int slot) {
	return components.arities().stream().filter(a -> a.slot() == slot).findFirst().orElse(null);
}

}

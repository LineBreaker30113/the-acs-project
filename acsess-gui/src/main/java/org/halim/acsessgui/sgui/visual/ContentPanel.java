package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Arity;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Components;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Row;
import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service.Structure;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.sglib.CodeColors;
import org.halim.acsessgui.sgui.sglib.Utilities;
import org.halim.acsessgui.sgui.sglib.WorkSpaceViewPanel;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement.Type;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A content panel: the selection at its top chooses the kind it shows, and it shows one element of the
 * type that kind takes, or, for the procedure, the shown database itself. Only one
 * panel listens for a kind; choosing a kind another panel holds swaps the two panels' states. It takes an
 * element by a click on the focused element of its type, or by a drop of one, and never follows the focus
 * by itself; the arity element also by typing the identity's number.
 * <p>
 * The arity's kinds: its summary lists its rows and captures and deletes its identities; its row lists
 * the row's values over the identities, a page at a time, and its element the identity's value in every
 * row, both edited in place, each edit one set request, so one journal activity. The structure's: a
 * structure's summary, its class,
 * description and spanned arities; its content, the structure with its arities on a plane, its
 * connections waiting for their requests. The class: the stored class decompiled.
 * <p>
 * Its lines and nodes are elements too, clicked and dragged as any view's. Every read goes through the
 * kept data, so a panel showing what another already read costs no program run.
 * */
public class ContentPanel extends WorkSpaceViewPanel {

static final int PAGE_SIZE = 100;
static final String NO_KIND = "(none)";
static final String LINES = "lines", PLANE = "plane", CODE = "code";

/** The content panels' selection, in its order: the label it shows, and the element type it takes, null when it takes none. */
public enum Kind {
	ARITY_SUMMARY("arity summary", Type.ARITY, "table.svg"), ARITY_ROW("arity row", Type.ARITY_ROW, "align-left-simple.svg"), ARITY_ELEMENT("arity element", Type.ARITY_IDENTITY, "member-of.svg"),
	STRUCTURE_SUMMARY("structure summary", Type.STRUCTURE, "building.svg"), STRUCTURE_CONTENT("structure content", Type.STRUCTURE, "wall.svg"),
	PROCEDURE("procedure", null, "flow-arrow.svg"), CLASS("class", Type.CLASS, "code.svg");

	public final String label;
	public final Type takes;
	/** The icon in the resources' {@code icons/}. */
	public final String icon;

	Kind(String label, Type takes, String icon) {
		this.label = label;
		this.takes = takes;
		this.icon = icon;
	}

	public Icon icon(int size) { return Utilities.loadSVGIcon("icons/" + icon, size, size, Utilities.ELEMENT_TEXT_PRIMARY); }

	/** Whether the type is one this kind takes: the arity element takes the arity it shows an identity of, besides the identity. */
	public boolean takes(Type type) { return type != null && (type == takes || (this == ARITY_ELEMENT && type == Type.ARITY)); }

	@Override public String toString() { return label; }
}

final JButton selection = Utilities.iconButton(Utilities.loadSVGIcon("icons/app-window.svg", 18, 18, Utilities.ELEMENT_TEXT_PRIMARY), "Content type");
final JPopupMenu kinds = new JPopupMenu();
final JButton transpose = Utilities.iconButton(Utilities.loadSVGIcon("icons/shuffle-simple.svg", 16, 16, Utilities.ELEMENT_TEXT_PRIMARY), "Transpose: the lines as columns");
/** Receives the header title, the chosen kind's name, and its icon. */
public java.util.function.BiConsumer<String, Icon> heading = (name, icon) -> { };
final JLabel title = new JLabel(" ");
final JLabel focus = new JLabel(" ");
final JLabel pageLabel = new JLabel(" ");
final JPanel pager = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
final JPanel summaryTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
final JPanel elementTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
final JTextField typedIdentity = new JTextField(8);
final CardLayout cards = new CardLayout();
final JPanel body = new JPanel(cards);
final LineModel model = new LineModel();
final JTable table = new JTable(model) {
	/** An identity's line names its identity: the arity row view lists the values alone. */
	@Override
	public String getToolTipText(java.awt.event.MouseEvent event) {
		int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
		int line = row < 0 || column < 0 ? -1 : model.lineOf(row, column);
		WorkspaceElement lineElement = line < 0 || line >= model.shown.elements.length ? null : model.shown.elements[line];
		return lineElement != null && lineElement.type() == Type.ARITY_IDENTITY ? lineElement.toString() : null;
	}
};
final StructurePlane plane;
final org.fife.ui.rsyntaxtextarea.RSyntaxTextArea code = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
Kind kind;
WorkspaceElement element;
String databaseName;
int page = 0, pages = 1;

/** A value a line edits: the row and the identity it is set for. */
record Edit(Row row, long identity) {}

/** Lines: a title, and lines, each the element it is when clicked or dragged, or null; the edit column's cells are written through their edits. */
record Shown(String title, String[] columns, Object[][] cells, WorkspaceElement[] elements, int editColumn, Edit[] edits, int pages) {
	static Shown note(String title) { return new Shown(title, new String[0], new Object[0][0], new WorkspaceElement[0], -1, new Edit[0], 1); }

	static Shown lines(String title, String[] columns, Object[][] cells, WorkspaceElement[] elements) { return new Shown(title, columns, cells, elements, -1, new Edit[cells.length], 1); }
}

/** Nodes and edges on the plane, under a title. */
record Plane(String title, List<StructurePlane.Node> nodes, List<StructurePlane.Edge> edges, String note) {}

/** A class's source. */
record Code(String title, String source) {}

/** The lines as rows, or transposed: a column each, under their first cell's text or their number, with the fields as rows. */
class LineModel extends AbstractTableModel {
	Shown shown = Shown.note(" ");
	boolean transposed;
	/** Whether the first column is the lines' number: it has no name above it and stays narrow. */
	boolean indexFirst;

	/** The line a table cell shows, -1 for the transposed field names. */
	int lineOf(int row, int column) { return transposed ? column - 1 : row; }

	/** The field a table cell shows. */
	int fieldOf(int row, int column) { return transposed ? row : column; }

	@Override public int getRowCount() { return transposed ? shown.columns.length : shown.cells.length; }

	@Override public int getColumnCount() { return transposed ? shown.cells.length + 1 : shown.columns.length; }

	@Override
	public String getColumnName(int column) {
		if(!transposed) { return indexFirst && column == 0 ? "" : shown.columns[column]; }
		if(column == 0) { return ""; }
		return shown.columns.length > 1 ? String.valueOf(shown.cells[column - 1][0]) : String.valueOf(column);
	}

	@Override public Object getValueAt(int row, int column) { return transposed ? (column == 0 ? shown.columns[row] : shown.cells[column - 1][row]) : shown.cells[row][column]; }

	@Override public boolean isCellEditable(int row, int column) { return lineOf(row, column) >= 0 && fieldOf(row, column) == shown.editColumn && shown.edits[lineOf(row, column)] != null; }

	@Override
	public void setValueAt(Object edited, int row, int column) {
		int line = lineOf(row, column);
		if(line < 0) { return; }
		column = fieldOf(row, column);
		String value = String.valueOf(edited);
		if(value.equals(String.valueOf(shown.cells[line][column]))) { return; }
		ACSESS_GUI_Service.DatabaseService database = database();
		Edit edit = shown.edits[line];
		if(database == null || edit == null || element == null) { return; }
		int arity = element.arity();
		app.perform("set row " + edit.row().slot() + " of identity " + edit.identity(), () -> database.setValue(arity, edit.row(), edit.identity(), value), () -> app.wsModeller.triggerDatabaseRefresh(database));
	}
}

public ContentPanel(ApplicationController app) {
	super(app);
	JMenuItem none = new JMenuItem(NO_KIND);
	none.addActionListener(e -> assume(null, null));
	kinds.add(none);
	for(Kind each : Kind.values()) {
		JMenuItem item = new JMenuItem(each.label, each.icon(18));
		item.addActionListener(e -> app.wsModeller.claim(this, each));
		kinds.add(item);
	}
	selection.addActionListener(e -> kinds.show(selection, 0, selection.getHeight()));
	transpose.addActionListener(e -> {
		model.transposed = !model.transposed;
		model.fireTableStructureChanged();
		Utilities.fitColumns(table, model.indexFirst && !model.transposed);
	});

	JButton previous = new JButton("<");
	previous.addActionListener(e -> turn(-1));
	JButton next = new JButton(">");
	next.addActionListener(e -> turn(1));
	pager.add(previous);
	pager.add(pageLabel);
	pager.add(next);
	JButton capture = new JButton("+ New Identity");
	capture.addActionListener(e -> captureIdentity());
	JButton delete = new JButton("Delete Identity...");
	delete.addActionListener(e -> deleteIdentity());
	summaryTools.add(capture);
	summaryTools.add(delete);
	JButton showTyped = new JButton("Show");
	showTyped.addActionListener(e -> showTyped());
	typedIdentity.addActionListener(e -> showTyped());
	JButton previousIdentity = new JButton("<"), nextIdentity = new JButton(">");
	previousIdentity.setToolTipText("The previous identity of this arity");
	nextIdentity.setToolTipText("The next identity of this arity");
	previousIdentity.addActionListener(e -> step(-1));
	nextIdentity.addActionListener(e -> step(1));
	elementTools.add(new JLabel("identity"));
	elementTools.add(previousIdentity);
	elementTools.add(typedIdentity);
	elementTools.add(nextIdentity);
	elementTools.add(showTyped);
	JPanel tools = new JPanel();
	tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
	tools.setOpaque(false);
	for(JPanel each : List.of(pager, summaryTools, elementTools)) {
		each.setOpaque(false);
		each.setAlignmentX(Component.LEFT_ALIGNMENT);
		each.setVisible(false);
		tools.add(each);
	}

	JPanel top = new JPanel(new BorderLayout(0, 4));
	top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	title.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	focus.setForeground(Utilities.ELEMENT_TEXT_SECONDARY);
	top.add(title, BorderLayout.CENTER);
	JPanel bottom = new JPanel(new BorderLayout());
	bottom.setOpaque(false);
	bottom.add(focus, BorderLayout.NORTH);
	bottom.add(tools, BorderLayout.SOUTH);
	top.add(bottom, BorderLayout.SOUTH);
	add(top, BorderLayout.NORTH);

	table.setFillsViewportHeight(true);
	ToolTipManager.sharedInstance().registerComponent(table);
	table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
	table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int line, int column) {
			Component cell = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, line, column);
			String text = String.valueOf(value);
			if(!isSelected) { cell.setForeground(model.lineOf(line, column) >= 0 && model.fieldOf(line, column) == model.shown.editColumn && text.startsWith("(") && text.endsWith(")") ? Utilities.REFUSED_COLOR : Utilities.ELEMENT_TEXT_PRIMARY); }
			return cell;
		}
	});
	plane = new StructurePlane(app.wsModeller::click);
	code.setEditable(false);
	code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
	code.setTabSize(4);
	body.add(new JScrollPane(table), LINES);
	body.add(plane, PLANE);
	CodeColors.apply(code);
	org.fife.ui.rtextarea.RTextScrollPane codePane = new org.fife.ui.rtextarea.RTextScrollPane(code);
	codePane.getGutter().setBackground(Utilities.LIST_BG);
	codePane.getGutter().setLineNumberColor(Utilities.ELEMENT_TEXT_SECONDARY);
	body.add(codePane, CODE);
	add(body, BorderLayout.CENTER);
	WorkspaceElement.Transfer.install(table, point -> {
		int row = table.rowAtPoint(point), column = table.columnAtPoint(point);
		if(row < 0 || column < 0) { return null; }
		int line = model.lineOf(row, column);
		return line < 0 || model.fieldOf(row, column) == model.shown.editColumn ? null : model.shown.elements[line];
	}, app.wsModeller::click, this::accepts, this::take);
	WorkspaceElement.Transfer.target(this, this::accepts, this::take);
	WorkspaceElement.Transfer.target(top, this::accepts, this::take);
	setPreferredSize(new Dimension(420, 600));
	show(Shown.note("Choose a kind, then click a focused element of it twice, or drop one here."));
}

/** A kind's label as a header title: each word capitalized. */
static String titled(String label) {
	StringBuilder words = new StringBuilder();
	for(String word : label.split(" ")) { words.append(words.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); }
	return words.toString();
}

/** The button choosing the kind, which the panel's header holds. */
public JButton getChooser() { return selection; }

/** The button turning the lines into columns and back, which the panel's header holds. */
public JButton getTranspose() { return transpose; }

public Kind getKind() { return kind; }

public WorkspaceElement getElement() { return element; }

boolean accepts(WorkspaceElement dropped) { return kind != null && kind.takes(dropped.type()); }

/** The element, clicked once focused, dropped here or typed, if of the type this panel's kind takes. */
public void take(WorkspaceElement taken) {
	if(!accepts(taken)) { return; }
	element = taken;
	page = 0;
	ACSESS_GUI_Service.DatabaseService database = database();
	databaseName = database == null ? null : database.getName();
	if(kind == Kind.ARITY_ELEMENT && taken.type() == Type.ARITY) { firstIdentity(taken); return; }
	reload();
}

/** An arity chosen for the arity element: its first identity is shown, or a note when it holds none. */
void firstIdentity(WorkspaceElement chosen) {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null) { reload(); return; }
	int at = chosen.arity();
	app.request("read the first identity of arity " + at, () -> {
		long[] all = database.getIdentities(at);
		return all.length == 0 ? null : Arrays.stream(all).min().getAsLong();
	}, found -> {
		if(kind != Kind.ARITY_ELEMENT || !chosen.equals(element)) { return; }
		if(found != null) { element = WorkspaceElement.identity(at, found); }
		reload();
	});
}

/** The state the workspace gives this panel when it claims a kind, or swaps with the panel that held it. */
public void assume(Kind assumed, WorkspaceElement held) {
	kind = assumed;
	element = held != null && assumed != null && assumed.takes(held.type()) ? held : null;
	page = 0;
	heading.accept(assumed == null ? "Content" : titled(assumed.label), assumed == null ? null : assumed.icon(18));
	summaryTools.setVisible(kind == Kind.ARITY_SUMMARY);
	elementTools.setVisible(kind == Kind.ARITY_ELEMENT);
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
	focus.setText("focused: " + focused + (kind != null && kind.takes(focused.type()) ? " (click it again to show it here)" : ""));
}

void turn(int direction) {
	int wanted = page + direction;
	if(wanted < 0 || wanted >= pages) { return; }
	page = wanted;
	reload();
}

/** The identity typed in the arity element's field, of the arity chosen. */
void showTyped() {
	if(element == null) {
		JOptionPane.showMessageDialog(this, "Choose an arity first: click a focused arity twice, or drop one here.", "Arity Element", JOptionPane.WARNING_MESSAGE);
		return;
	}
	try { take(WorkspaceElement.identity(element.arity(), Long.parseLong(typedIdentity.getText().trim()))); }
	catch(NumberFormatException e) { JOptionPane.showMessageDialog(this, "The identity is a number.", "Arity Element", JOptionPane.WARNING_MESSAGE); }
}

/** Shows the previous (-1) or next (1) identity the shown one's arity holds; nothing at either end. */
void step(int direction) {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || element == null || element.type() != Type.ARITY_IDENTITY) { return; }
	int at = element.arity();
	long from = element.identity();
	app.request("step from identity " + from + " of arity " + at, () -> neighbor(database.getIdentities(at), from, direction), found -> {
		if(found != null) { take(WorkspaceElement.identity(at, found)); }
	});
}

/** The identity nearest to {@code from} on the side of {@code direction}, null when there is none; the identities are not assumed sorted. */
static Long neighbor(long[] identities, long from, int direction) {
	Long best = null;
	for(long each : identities) {
		if(direction > 0 ? each > from && (best == null || each < best) : each < from && (best == null || each > best)) { best = each; }
	}
	return best;
}

void captureIdentity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || element == null) { return; }
	int at = element.arity();
	app.request("capture an identity in arity " + at, () -> database.captureIdentity(at), identity -> app.wsModeller.triggerDatabaseRefresh(database));
}

/** Deletes the identity asked, the focused one of this arity offered, by delegateRemoval, so the structures holding it let go first. */
void deleteIdentity() {
	ACSESS_GUI_Service.DatabaseService database = database();
	if(database == null || element == null) { return; }
	int at = element.arity();
	WorkspaceElement focused = app.wsModeller.getFocused();
	String offered = focused != null && focused.type() == Type.ARITY_IDENTITY && focused.arity() == at ? String.valueOf(focused.identity()) : "";
	String typed = (String) JOptionPane.showInputDialog(this, "The identity to delete from arity " + at + ":", "Delete Identity", JOptionPane.PLAIN_MESSAGE, null, null, offered);
	if(typed == null) { return; }
	long identity;
	try { identity = Long.parseLong(typed.trim()); }
	catch(NumberFormatException e) {
		JOptionPane.showMessageDialog(this, "'" + typed + "' is no identity.", "Delete Identity", JOptionPane.WARNING_MESSAGE);
		return;
	}
	if(JOptionPane.showConfirmDialog(this, "Delete identity " + identity + " from arity " + at + "? It is one journal activity, undone as one.", "Confirm Deletion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
	app.perform("delete identity " + identity + " of arity " + at, () -> database.deleteIdentity(at, identity), () -> app.wsModeller.triggerDatabaseRefresh(database));
}

void reload() {
	ACSESS_GUI_Service.DatabaseService database = database();
	pager.setVisible(false);
	if(kind == null) { show(Shown.note("Choose a kind, then click a focused element of it twice, or drop one here.")); return; }
	if(kind == Kind.PROCEDURE) { show(Shown.note("A procedure waits for its requests: acsess has none of a procedure, and components lists none.")); return; }
	if(database == null) { show(Shown.note("No database is shown.")); return; }
	if(element == null && kind.takes != null) {
		show(Shown.note(kind == Kind.ARITY_ELEMENT ? "Click a focused arity twice, or drop one here; then step or type its identity above." : "Click a focused " + kind.takes + " twice, or drop one here."));
		return;
	}
	if(kind == Kind.ARITY_ELEMENT && element.type() == Type.ARITY_IDENTITY) { typedIdentity.setText(String.valueOf(element.identity())); }
	Kind shownKind = kind;
	WorkspaceElement shownElement = element;
	int wanted = page;
	app.request("read " + (shownElement == null ? kind : shownElement) + " for a content panel", () -> read(database, shownKind, shownElement, wanted), shown -> {
		if(shownKind == kind && java.util.Objects.equals(shownElement, element)) { show(shown); }
	});
}

/** A title, its lines broken at the newlines. */
static String html(String text) { return "<html>" + WelcomePage.escaped(text).replace("\n", "<br>") + "</html>"; }

void show(Object shown) {
	updated.run();
	switch(shown) {
		case Shown lines -> {
			boolean indexFirst = kind == Kind.ARITY_SUMMARY || kind == Kind.ARITY_ELEMENT;
			boolean sameColumns = Arrays.equals(model.shown.columns, lines.columns) && indexFirst == model.indexFirst && (!model.transposed || model.shown.cells.length == lines.cells.length);
			model.shown = lines;
			model.indexFirst = indexFirst;
			if(sameColumns) { model.fireTableDataChanged(); } else {
				model.fireTableStructureChanged();
				Utilities.fitColumns(table, indexFirst && !model.transposed);
			}
			title.setText(html(lines.title));
			pages = lines.pages;
			pager.setVisible(kind == Kind.ARITY_ROW && element != null);
			pageLabel.setText("page " + (page + 1) + " of " + pages);
			cards.show(body, LINES);
		}
		case Plane drawn -> {
			title.setText(html(drawn.title));
			plane.show(drawn.nodes, drawn.edges, drawn.note);
			cards.show(body, PLANE);
		}
		case Code source -> {
			title.setText(html(source.title));
			code.setText(source.source);
			code.setCaretPosition(0);
			cards.show(body, CODE);
		}
		default -> throw new IllegalArgumentException(String.valueOf(shown));
	}
}

/** Runs on the request thread. */
static Object read(ACSESS_GUI_Service.DatabaseService database, Kind kind, WorkspaceElement element, int page) {
	if(kind == Kind.CLASS) { return new Code(element.className(), database.decompile(element.className())); }
	Components components = database.getComponents();
	switch(kind) {
		case STRUCTURE_SUMMARY, STRUCTURE_CONTENT -> {
			Structure structure = components.structures().stream().filter(s -> s.slot() == element.structure()).findFirst().orElse(null);
			if(structure == null || structure.isRemoved()) { return Shown.note(element + " is not in the database."); }
			return kind == Kind.STRUCTURE_SUMMARY ? structureSummary(components, structure) : structureContent(components, structure);
		}
		default -> { }
	}
	Arity arity = arityOf(components, element.arity());
	if(arity == null || arity.isRemoved()) { return Shown.note("arity " + element.arity() + " is not in the database."); }
	List<Row> live = arity.rows().stream().filter(r -> !r.isRemoved()).toList();
	switch(kind) {
		case ARITY_SUMMARY -> {
			Object[][] cells = new Object[live.size()][];
			WorkspaceElement[] elements = new WorkspaceElement[live.size()];
			for(int i = 0; i < live.size(); i++) {
				Row row = live.get(i);
				cells[i] = new Object[] { row.slot(), Utilities.described(row.description()), Utilities.simple(row.kind()), row.type().name().toLowerCase() };
				elements[i] = WorkspaceElement.row(arity.slot(), row.slot());
			}
			String summary = element + "\nclass: " + Utilities.simple(arity.className()) + "\ndescription: " + Utilities.described(arity.description()) + "\nidentities: " + arity.identities() + "\nrows: " + live.size();
			return Shown.lines(summary, new String[] { "row", "description", "class", "type" }, cells, elements);
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
			Edit[] edits = new Edit[identities.length];
			for(int i = 0; i < identities.length; i++) {
				cells[i] = new Object[] { values[i][0] == null ? "(object)" : values[i][0] };
				elements[i] = WorkspaceElement.identity(arity.slot(), identities[i]);
				edits[i] = values[i][0] == null ? null : new Edit(row, identities[i]);
			}
			String head = element + ": " + Utilities.described(row.description()) + " (" + row.type().name().toLowerCase() + "), " + all.length + (all.length == 1 ? " identity" : " identities");
			return new Shown(head, new String[] { "value" }, cells, elements, 0, edits, pageCount);
		}
		case ARITY_ELEMENT -> {
			if(element.type() == Type.ARITY) { return Shown.note(element + " holds no identities."); }
			if(Arrays.stream(database.getIdentities(arity.slot())).noneMatch(i -> i == element.identity())) { return Shown.note(element + " is not in the database."); }
			String[][] values = database.getValues(arity.slot(), live, new long[] { element.identity() });
			Object[][] cells = new Object[live.size()][];
			WorkspaceElement[] elements = new WorkspaceElement[live.size()];
			Edit[] edits = new Edit[live.size()];
			for(int i = 0; i < live.size(); i++) {
				Row row = live.get(i);
				cells[i] = new Object[] { row.slot(), Utilities.described(row.description()), Utilities.simple(row.kind()), row.type().name().toLowerCase(), values[0][i] == null ? "(object)" : values[0][i] };
				elements[i] = WorkspaceElement.row(arity.slot(), row.slot());
				edits[i] = values[0][i] == null ? null : new Edit(row, element.identity());
			}
			return new Shown(element.toString(), new String[] { "row", "description", "class", "type", "value" }, cells, elements, 4, edits, 1);
		}
		default -> { return Shown.note(element + " has nothing to show."); }
	}
}

static Shown structureSummary(Components components, Structure structure) {
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
	return Shown.lines("structure " + structure.slot() + ": " + Utilities.described(structure.description()), new String[] { "field", "value" }, cells.toArray(new Object[0][]), elements.toArray(new WorkspaceElement[0]));
}

/** The structure with the arities it spans; the connections it creates wait for a request that reads them. */
static Plane structureContent(Components components, Structure structure) {
	ArrayList<StructurePlane.Node> nodes = new ArrayList<>();
	ArrayList<StructurePlane.Edge> edges = new ArrayList<>();
	int count = structure.arities().length;
	nodes.add(new StructurePlane.Node(WorkspaceElement.structure(structure.slot()), "structure " + structure.slot(), (count - 1) * StructurePlane.SPACING / 2, StructurePlane.ROWS, true));
	for(int i = 0; i < count; i++) {
		int spanned = structure.arities()[i];
		Arity arity = arityOf(components, spanned);
		String label = "arity " + spanned + ": " + (arity == null || arity.isRemoved() ? "(not in the database)" : Utilities.described(arity.description()));
		nodes.add(new StructurePlane.Node(WorkspaceElement.arity(spanned), label, i * StructurePlane.SPACING, 0, false));
		edges.add(new StructurePlane.Edge(0, nodes.size() - 1));
	}
	String note = "Spans " + count + (count == 1 ? " arity" : " arities") + ". Its connections wait for their requests: acsess has none that reads a structure's relations.";
	return new Plane("structure " + structure.slot() + ": " + Utilities.described(structure.description()) + " (" + structure.className() + ")", nodes, edges, note);
}

static Arity arityOf(Components components, int slot) {
	return components.arities().stream().filter(a -> a.slot() == slot).findFirst().orElse(null);
}

}

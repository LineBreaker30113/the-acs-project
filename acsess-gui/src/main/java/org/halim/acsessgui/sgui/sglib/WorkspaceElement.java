package org.halim.acsessgui.sgui.sglib;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DragSource;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * An element of the shown database a view shows and a content panel can take: an arity, a structure, a
 * procedure, a row of an arity, an identity of one, or a stored class, named by its slots or its name. A
 * field its type does not use holds -1, or null for the name, and is never read, since an identity may
 * itself be -1.
 * <p>
 * A click on an element focuses it; a click on the element already focused gives it to the content panel
 * listening for its type; a drag carries it onto one panel, changing no other. {@link Transfer} carries
 * the clicks and the drags of every view alike.
 * */
public record WorkspaceElement(Type type, int arity, int row, long identity, int structure, String className) {

/** What an element is; the label names it in what the panels print. */
public enum Type {
	ARITY("arity"), STRUCTURE("structure"), PROCEDURE("procedure"), ARITY_ROW("arity-row"), ARITY_IDENTITY("arity-identity"), CLASS("class");

	public final String label;

	Type(String label) { this.label = label; }

	@Override public String toString() { return label; }
}

public static WorkspaceElement arity(int arity) { return new WorkspaceElement(Type.ARITY, arity, -1, -1, -1, null); }

public static WorkspaceElement structure(int structure) { return new WorkspaceElement(Type.STRUCTURE, -1, -1, -1, structure, null); }

public static WorkspaceElement row(int arity, int row) { return new WorkspaceElement(Type.ARITY_ROW, arity, row, -1, -1, null); }

public static WorkspaceElement identity(int arity, long identity) { return new WorkspaceElement(Type.ARITY_IDENTITY, arity, -1, identity, -1, null); }

public static WorkspaceElement javaClass(String className) { return new WorkspaceElement(Type.CLASS, -1, -1, -1, -1, className); }

@Override
public String toString() {
	return switch(type) {
		case ARITY -> "arity " + arity;
		case STRUCTURE -> "structure " + structure;
		case PROCEDURE -> "procedure";
		case ARITY_ROW -> "row " + row + " of arity " + arity;
		case ARITY_IDENTITY -> "identity " + identity + " of arity " + arity;
		case CLASS -> "class " + className;
	};
}

/**
 * The clicks and the drags of elements within this program, as Swing's TransferHandler carries them. A
 * source names the element under the mouse, or null where there is none: a click there goes to
 * {@code click}, and a drag past Swing's threshold exports it. A target takes, by a drop, an element
 * {@code accepts} lets in. One handler may be both, as a content panel's table is.
 * <p>
 * Setting a handler on a table or a list replaces the one Swing gave it, so its copy and paste go too.
 * */
public static class Transfer extends TransferHandler {

	public static final DataFlavor FLAVOR = flavor();

	final Function<Point, WorkspaceElement> at;
	final Predicate<WorkspaceElement> accepts;
	final Consumer<WorkspaceElement> drop;
	WorkspaceElement dragged;

	Transfer(Function<Point, WorkspaceElement> at, Predicate<WorkspaceElement> accepts, Consumer<WorkspaceElement> drop) {
		this.at = at;
		this.accepts = accepts;
		this.drop = drop;
	}

	static DataFlavor flavor() {
		try { return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + WorkspaceElement.class.getName()); }
		catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
	}

	/** Elements are clicked and dragged from the component. */
	public static void source(JComponent component, Function<Point, WorkspaceElement> at, Consumer<WorkspaceElement> click) { install(component, at, click, null, null); }

	/** Elements are dropped onto the component. */
	public static void target(JComponent component, Predicate<WorkspaceElement> accepts, Consumer<WorkspaceElement> drop) { install(component, null, null, accepts, drop); }

	public static void install(JComponent component, Function<Point, WorkspaceElement> at, Consumer<WorkspaceElement> click, Predicate<WorkspaceElement> accepts, Consumer<WorkspaceElement> drop) {
		Transfer transfer = new Transfer(at, accepts, drop);
		component.setTransferHandler(transfer);
		if(at == null) { return; }
		MouseAdapter mouse = new MouseAdapter() {
			Point pressed;

			@Override public void mousePressed(MouseEvent e) { pressed = SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null; }

			@Override public void mouseReleased(MouseEvent e) { pressed = null; }

			@Override
			public void mouseClicked(MouseEvent e) {
				if(!SwingUtilities.isLeftMouseButton(e)) { return; }
				WorkspaceElement element = at.apply(e.getPoint());
				if(element != null) { click.accept(element); }
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if(pressed == null || pressed.distance(e.getPoint()) < DragSource.getDragThreshold()) { return; }
				WorkspaceElement element = at.apply(pressed);
				pressed = null;
				if(element == null) { return; }
				transfer.dragged = element;
				transfer.exportAsDrag(component, e, COPY);
			}
		};
		component.addMouseListener(mouse);
		component.addMouseMotionListener(mouse);
	}

	@Override public int getSourceActions(JComponent c) { return at == null ? NONE : COPY; }

	@Override
	protected Transferable createTransferable(JComponent c) {
		WorkspaceElement element = dragged;
		if(element == null) { return null; }
		return new Transferable() {
			@Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { FLAVOR }; }

			@Override public boolean isDataFlavorSupported(DataFlavor flavor) { return FLAVOR.equals(flavor); }

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
				if(!FLAVOR.equals(flavor)) { throw new UnsupportedFlavorException(flavor); }
				return element;
			}
		};
	}

	@Override protected void exportDone(JComponent source, Transferable data, int action) { dragged = null; }

	@Override
	public boolean canImport(TransferSupport support) {
		if(drop == null || !support.isDataFlavorSupported(FLAVOR)) { return false; }
		if(support.isDrop()) { support.setShowDropLocation(false); }
		WorkspaceElement element = carried(support);
		return element != null && accepts.test(element);
	}

	@Override
	public boolean importData(TransferSupport support) {
		if(!canImport(support)) { return false; }
		drop.accept(carried(support));
		return true;
	}

	static WorkspaceElement carried(TransferSupport support) {
		try { return (WorkspaceElement) support.getTransferable().getTransferData(FLAVOR); }
		catch(Exception e) { return null; }
	}
}

}

package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.sgui.sglib.Utilities;
import org.halim.acsgui.sgui.sglib.WorkspaceElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;


/**
 * A plane of nodes and edges, moved by dragging its empty part and zoomed by the mouse wheel around the
 * pointer, as the content panel's structure kinds draw structures and arities. Its own Java2D, so no graph
 * library: the nodes are placed by the panel, a row of arities above a row of structures, and the plane only
 * draws, finds the node under the mouse, and transforms.
 * A node is an element: clicked and dragged as any view's lines are; a note is drawn over the plane
 * without moving with it.
 * */
public class StructurePlane extends JComponent {

static final double WIDTH = 150, HEIGHT = 40, MIN_SCALE = 0.1, MAX_SCALE = 8, SPACING = 180, ROWS = 160;

/** A node's center in plane units; a square node is drawn as a structure is, a round one as an arity. */
public record Node(WorkspaceElement element, String label, double x, double y, boolean square) {}

/** An edge between two nodes, by their places in the list. */
public record Edge(int from, int to) {}

List<Node> nodes = List.of();
List<Edge> edges = List.of();
String note = "";
double scale = 1, offsetX = 100, offsetY = 60;
Point panned;

public StructurePlane(Consumer<WorkspaceElement> click) {
	setOpaque(true);
	setBackground(Utilities.WP_BG);
	MouseAdapter mouse = new MouseAdapter() {
		@Override public void mousePressed(MouseEvent e) { panned = SwingUtilities.isLeftMouseButton(e) && nodeAt(e.getPoint()) == null ? e.getPoint() : null; }

		@Override
		public void mouseDragged(MouseEvent e) {
			if(panned == null) { return; }
			offsetX += e.getX() - panned.x;
			offsetY += e.getY() - panned.y;
			panned = e.getPoint();
			repaint();
		}

		@Override public void mouseReleased(MouseEvent e) { panned = null; }

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			double factor = Math.pow(1.1, -e.getPreciseWheelRotation());
			double next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
			factor = next / scale;
			offsetX = e.getX() - (e.getX() - offsetX) * factor;
			offsetY = e.getY() - (e.getY() - offsetY) * factor;
			scale = next;
			repaint();
		}
	};
	addMouseListener(mouse);
	addMouseMotionListener(mouse);
	addMouseWheelListener(mouse);
	WorkspaceElement.Transfer.source(this, point -> {
		Node node = nodeAt(point);
		return node == null ? null : node.element();
	}, click);
}

public void show(List<Node> shownNodes, List<Edge> shownEdges, String shownNote) {
	nodes = List.copyOf(shownNodes);
	edges = List.copyOf(shownEdges);
	note = shownNote;
	repaint();
}

Point2D toPlane(Point point) { return new Point2D.Double((point.x - offsetX) / scale, (point.y - offsetY) / scale); }

Node nodeAt(Point point) {
	Point2D at = toPlane(point);
	for(int i = nodes.size() - 1; i >= 0; i--) {
		Node node = nodes.get(i);
		if(Math.abs(at.getX() - node.x()) <= WIDTH / 2 && Math.abs(at.getY() - node.y()) <= HEIGHT / 2) { return node; }
	}
	return null;
}

@Override
protected void paintComponent(Graphics graphics) {
	Graphics2D g = (Graphics2D) graphics.create();
	g.setColor(getBackground());
	g.fillRect(0, 0, getWidth(), getHeight());
	g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	Graphics2D plane = (Graphics2D) g.create();
	plane.translate(offsetX, offsetY);
	plane.scale(scale, scale);
	plane.setColor(Utilities.ELEMENT_TEXT_SECONDARY);
	plane.setStroke(new BasicStroke(1.5f));
	for(Edge edge : edges) {
		Node from = nodes.get(edge.from()), to = nodes.get(edge.to());
		plane.drawLine((int) from.x(), (int) from.y(), (int) to.x(), (int) to.y());
	}
	FontMetrics metrics = plane.getFontMetrics();
	for(Node node : nodes) {
		double left = node.x() - WIDTH / 2, top = node.y() - HEIGHT / 2;
		Shape shape = new RoundRectangle2D.Double(left, top, WIDTH, HEIGHT, node.square() ? 6 : HEIGHT, node.square() ? 6 : HEIGHT);
		plane.setColor(node.square() ? new Color(70, 60, 20) : Utilities.ELEMENT_BG);
		plane.fill(shape);
		plane.setColor(node.square() ? Utilities.GOLDEN_COLOR : Utilities.ELEMENT_TEXT_SECONDARY);
		plane.draw(shape);
		plane.setColor(Utilities.ELEMENT_TEXT_PRIMARY);
		String label = node.label();
		while(label.length() > 3 && metrics.stringWidth(label) > WIDTH - 12) { label = label.substring(0, label.length() - 2) + "…"; }
		plane.drawString(label, (float) (node.x() - metrics.stringWidth(label) / 2.0), (float) (node.y() + metrics.getAscent() / 2.0 - 2));
	}
	plane.dispose();
	g.setColor(Utilities.ELEMENT_TEXT_SECONDARY);
	int line = g.getFontMetrics().getHeight();
	String[] noted = note.split("\n");
	for(int i = 0; i < noted.length; i++) { g.drawString(noted[i], 8, line * (i + 1)); }
	g.dispose();
}

}

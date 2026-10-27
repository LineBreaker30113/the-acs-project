package org.halim.acsgui.sgui.sglib;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;


/** The colors and the small helpers of OntoDirectory's Utilities that the cloned structure uses. */
public class Utilities {

public static final Color
	WP_BG = new Color(30, 30, 40),
	LIST_BG = new Color(40, 40, 40),
	ELEMENT_BG = new Color(50, 50, 50),
	ELEMENT_TEXT_PRIMARY = new Color(240, 240, 240),
	ELEMENT_TEXT_SECONDARY = new Color(150, 150, 150),
	GOLDEN_COLOR = new Color(255, 215, 0),
	REFUSED_COLOR = new Color(255, 120, 120),
	SLPEB_Hover = new Color(239, 207, 0);

public static final Color sideBarBG = new Color(0, 102, 88);

/** A description as the views print it. */
public static String described(String description) { return description == null ? "(no description)" : description; }

/** A class name without its package. */
public static String simple(String kind) { return kind.substring(kind.lastIndexOf('.') + 1); }

/** Strips Swing's focus ring and margins off a button, as OntoDirectory does. */
public static void initButton(JButton button) {
	button.setFocusable(false);
	button.setFocusPainted(false);
	button.setMargin(new Insets(0, 0, 0, 0));
	button.setIconTextGap(8);
}

/** An icon from the resources' {@code icons/}, or null when it is missing. */
public static Icon loadSVGIcon(String path, int width, int height) {
	try {
		FlatSVGIcon icon = new FlatSVGIcon(path, width, height);
		return icon.hasFound() ? icon : null;
	} catch(Exception e) { return null; }
}

public static Icon loadSVGIcon(String path, int width, int height, Color color) {
	Icon loaded = loadSVGIcon(path, width, height);
	if(loaded instanceof FlatSVGIcon icon && color != null) { icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color)); }
	return loaded;
}

/** A small drawn glyph for the panel headers' buttons: "left", "right", "up", "down" chevrons or "close". */
public static Icon glyph(String shape) {
	return new Icon() {
		@Override public int getIconWidth() { return 12; }

		@Override public int getIconHeight() { return 12; }

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(c.isEnabled() ? ELEMENT_TEXT_PRIMARY : ELEMENT_TEXT_SECONDARY);
			g2.translate(x, y);
			java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
			switch(shape) {
				case "left" -> { path.moveTo(8, 2); path.lineTo(4, 6); path.lineTo(8, 10); }
				case "right" -> { path.moveTo(4, 2); path.lineTo(8, 6); path.lineTo(4, 10); }
				case "up" -> { path.moveTo(2, 8); path.lineTo(6, 4); path.lineTo(10, 8); }
				case "down" -> { path.moveTo(2, 4); path.lineTo(6, 8); path.lineTo(10, 4); }
				default -> { path.moveTo(2, 2); path.lineTo(10, 10); path.moveTo(10, 2); path.lineTo(2, 10); }
			}
			g2.draw(path);
			g2.dispose();
		}
	};
}

/**
 * Sizes each column of the table to its header and its first rows, so an unchanged structure keeps what
 * the user dragged; the last column takes the rest of the width. A first column that is the lines'
 * number is sized to its cells alone, and stays narrow.
 * */
public static void fitColumns(JTable table, boolean indexFirst) {
	table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
	for(int column = 0; column < table.getColumnCount(); column++) {
		javax.swing.table.TableColumn tableColumn = table.getColumnModel().getColumn(column);
		Component header = table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, -1, column);
		int width = indexFirst && column == 0 ? 0 : header.getPreferredSize().width;
		for(int line = 0; line < Math.min(table.getRowCount(), 200); line++) {
			Component cell = table.prepareRenderer(table.getCellRenderer(line, column), line, column);
			width = Math.max(width, cell.getPreferredSize().width);
		}
		tableColumn.setPreferredWidth(indexFirst && column == 0 ? Math.max(30, Math.min(80, width + 12)) : Math.max(50, Math.min(500, width + 16)));
	}
}

/** A header button: an icon alone, flat until hovered, with its tooltip. */
public static JButton iconButton(Icon icon, String tooltip) {
	JButton button = new JButton(icon);
	button.setToolTipText(tooltip);
	button.putClientProperty("JButton.buttonType", "toolBarButton");
	initButton(button);
	return button;
}

/** A sidebar button: its text when expanded, its icon alone when collapsed. */
public static JButton sidebarButton(String name, String expandedIconName, String collapsedIconName) {
	JButton btn = new JButton(name);
	btn.setActionCommand(name);
	btn.setAlignmentX(Component.RIGHT_ALIGNMENT);
	btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
	btn.setMinimumSize(new Dimension(56, 56));
	btn.setPreferredSize(new Dimension(56, 56));
	btn.setHorizontalAlignment(SwingConstants.RIGHT);
	btn.setHorizontalTextPosition(SwingConstants.LEFT);
	btn.setIconTextGap(8);
	Icon expandedIcon = loadSVGIcon("icons/" + expandedIconName, 40, 40);
	Icon collapsedIcon = loadSVGIcon("icons/" + collapsedIconName, 40, 40);
	if(expandedIcon != null) { btn.setIcon(expandedIcon); }
	btn.putClientProperty("expandedIcon", expandedIcon);
	btn.putClientProperty("collapsedIcon", collapsedIcon);
	initButton(btn);
	return btn;
}

public static void updateCollapseState(JButton btn, boolean isCollapsed) {
	btn.setText(isCollapsed ? "" : btn.getActionCommand());
	Icon icon = (Icon) btn.getClientProperty(isCollapsed ? "collapsedIcon" : "expandedIcon");
	if(icon != null) { btn.setIcon(icon); }
	btn.setHorizontalAlignment(isCollapsed ? SwingConstants.CENTER : SwingConstants.TRAILING);
}

}

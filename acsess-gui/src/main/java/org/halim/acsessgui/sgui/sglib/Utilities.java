package org.halim.acsessgui.sgui.sglib;

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

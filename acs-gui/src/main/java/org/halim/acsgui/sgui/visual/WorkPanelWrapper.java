package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.sgui.WorkspacePanel;
import org.halim.acsgui.sgui.sglib.Utilities;
import org.halim.acsgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;


/**
 * A view with its header: its golden title and the arrows that move it, as in OntoDirectory: left and
 * right in its row, up and down across the rows. A view the sidebar does not toggle, a content panel,
 * carries its own close button.
 * */
public class WorkPanelWrapper extends JPanel {

public final String title;
public final WorkspacePanel parentWorkspace;
public final JPanel content;
final JLabel titleLabel;
final JPanel header;
javax.swing.Timer flashing;

static final Color HEADER_BG = new Color(35, 35, 40), FLASH = new Color(255, 226, 110);
static final int FLASH_STEPS = 14;

public WorkPanelWrapper(String title, String niceTitle, JPanel content, WorkspacePanel parentWorkspace) { this(title, niceTitle, content, parentWorkspace, null); }

public WorkPanelWrapper(String title, String niceTitle, JPanel content, WorkspacePanel parentWorkspace, Runnable close) {
	this.title = title;
	this.content = content;
	this.parentWorkspace = parentWorkspace;
	setLayout(new BorderLayout());
	setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.DARK_GRAY));

	header = new JPanel(new BorderLayout());
	header.setBackground(HEADER_BG);
	header.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
	titleLabel = new JLabel(niceTitle, SwingConstants.CENTER);
	titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
	titleLabel.setForeground(Utilities.GOLDEN_COLOR);
	header.add(titleLabel, BorderLayout.CENTER);

	JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
	controls.setOpaque(false);
	JButton leftBtn = Utilities.iconButton(Utilities.glyph("left"), "Move left"), rightBtn = Utilities.iconButton(Utilities.glyph("right"), "Move right");
	JButton upBtn = Utilities.iconButton(Utilities.glyph("up"), "Move to the row above"), downBtn = Utilities.iconButton(Utilities.glyph("down"), "Move to the row below");
	for(JButton btn : new JButton[] { leftBtn, rightBtn, upBtn, downBtn }) {
		btn.setPreferredSize(new Dimension(22, 22));
		controls.add(btn);
	}
	leftBtn.addActionListener(e -> parentWorkspace.shiftPanel(this, -1));
	rightBtn.addActionListener(e -> parentWorkspace.shiftPanel(this, 1));
	upBtn.addActionListener(e -> parentWorkspace.shiftRow(this, -1));
	downBtn.addActionListener(e -> parentWorkspace.shiftRow(this, 1));
	if(close != null) {
		JButton closeBtn = Utilities.iconButton(Utilities.glyph("close"), "Close");
		closeBtn.setPreferredSize(new Dimension(22, 22));
		closeBtn.addActionListener(e -> close.run());
		controls.add(closeBtn);
	}
	header.add(controls, BorderLayout.EAST);

	if(content instanceof WorkSpaceViewPanel view) { view.updated = this::flash; }
	add(header, BorderLayout.NORTH);
	add(content, BorderLayout.CENTER);
}

/** A soft yellow light in the header that fades out, when the view shows something new. */
public void flash() {
	if(flashing != null) { flashing.stop(); }
	int[] step = { 0 };
	header.setBackground(blend(0.5));
	flashing = new javax.swing.Timer(35, e -> {
		step[0]++;
		double left = 1 - (double) step[0] / FLASH_STEPS;
		header.setBackground(step[0] >= FLASH_STEPS ? HEADER_BG : blend(0.5 * left * left));
		if(step[0] >= FLASH_STEPS) { ((javax.swing.Timer) e.getSource()).stop(); }
	});
	flashing.start();
}

static Color blend(double light) {
	return new Color((int) (HEADER_BG.getRed() + (FLASH.getRed() - HEADER_BG.getRed()) * light), (int) (HEADER_BG.getGreen() + (FLASH.getGreen() - HEADER_BG.getGreen()) * light), (int) (HEADER_BG.getBlue() + (FLASH.getBlue() - HEADER_BG.getBlue()) * light));
}

/** A panel's own buttons, such as its chooser, at the header's left. */
public void setLeftButtons(JButton... buttons) {
	JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
	left.setOpaque(false);
	for(JButton button : buttons) {
		button.setPreferredSize(new Dimension(26, 22));
		left.add(button);
	}
	header.add(left, BorderLayout.WEST);
	header.revalidate();
}

/** The header title and its icon, as a content panel names its kind; the icon may be null. */
public void setTitle(String niceTitle, Icon icon) {
	titleLabel.setText(niceTitle);
	titleLabel.setIcon(icon);
}

}

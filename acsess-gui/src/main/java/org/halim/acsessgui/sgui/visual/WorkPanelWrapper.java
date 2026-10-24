package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.sgui.WorkspacePanel;
import org.halim.acsessgui.sgui.sglib.Utilities;

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

public WorkPanelWrapper(String title, String niceTitle, JPanel content, WorkspacePanel parentWorkspace) { this(title, niceTitle, content, parentWorkspace, null); }

public WorkPanelWrapper(String title, String niceTitle, JPanel content, WorkspacePanel parentWorkspace, Runnable close) {
	this.title = title;
	this.content = content;
	this.parentWorkspace = parentWorkspace;
	setLayout(new BorderLayout());
	setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.DARK_GRAY));

	JPanel header = new JPanel(new BorderLayout());
	header.setBackground(new Color(35, 35, 40));
	header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
	JLabel titleLabel = new JLabel(niceTitle, SwingConstants.CENTER);
	titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
	titleLabel.setForeground(Utilities.GOLDEN_COLOR);
	header.add(titleLabel, BorderLayout.CENTER);

	JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
	controls.setOpaque(false);
	JButton leftBtn = new JButton("<"), rightBtn = new JButton(">"), upBtn = new JButton("^"), downBtn = new JButton("v");
	for(JButton btn : new JButton[] { leftBtn, rightBtn, upBtn, downBtn }) {
		Utilities.initButton(btn);
		controls.add(btn);
	}
	leftBtn.addActionListener(e -> parentWorkspace.shiftPanel(this, -1));
	rightBtn.addActionListener(e -> parentWorkspace.shiftPanel(this, 1));
	upBtn.addActionListener(e -> parentWorkspace.shiftRow(this, -1));
	downBtn.addActionListener(e -> parentWorkspace.shiftRow(this, 1));
	if(close != null) {
		JButton closeBtn = new JButton("x");
		Utilities.initButton(closeBtn);
		closeBtn.addActionListener(e -> close.run());
		controls.add(closeBtn);
	}
	header.add(controls, BorderLayout.EAST);

	add(header, BorderLayout.NORTH);
	add(content, BorderLayout.CENTER);
}

}

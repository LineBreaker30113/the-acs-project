package org.halim.acsessgui.sgui.sglib;

import javax.swing.*;
import java.awt.*;


/** A manual entry: its title and summary always shown, its details behind a toggle, so a page of entries can be scanned before one is read. */
public class AccordionPanel extends JPanel {

final JPanel detailsPanel = new JPanel(new BorderLayout());
final JButton toggle = new JButton("▶ Read Details");
boolean expanded = false;

public AccordionPanel(String title, String summary, String htmlDetails) {
	setLayout(new BorderLayout());
	setOpaque(false);
	setBorder(BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, Utilities.ELEMENT_BG),
		BorderFactory.createEmptyBorder(10, 0, 15, 0)));
	// A vertical BoxLayout would stretch it without this.
	setMaximumSize(new Dimension(850, Integer.MAX_VALUE));
	setAlignmentX(Component.LEFT_ALIGNMENT);

	JPanel header = new JPanel();
	header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
	header.setOpaque(false);
	JLabel titleLabel = new JLabel(title);
	titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
	titleLabel.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
	JLabel summaryLabel = new JLabel("<html><div style='width: 550px; padding-top: 6px;'>" + summary + "</div></html>");
	summaryLabel.setForeground(Utilities.ELEMENT_TEXT_SECONDARY);
	summaryLabel.setFont(summaryLabel.getFont().deriveFont(15f));
	summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
	Utilities.initButton(toggle);
	toggle.setHorizontalAlignment(SwingConstants.LEFT);
	toggle.setFont(toggle.getFont().deriveFont(Font.BOLD, 12f));
	toggle.setForeground(Utilities.GOLDEN_COLOR);
	toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
	toggle.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
	toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
	header.add(titleLabel);
	header.add(summaryLabel);
	header.add(toggle);

	detailsPanel.setOpaque(false);
	detailsPanel.setVisible(false);
	detailsPanel.setBorder(BorderFactory.createEmptyBorder(12, 10, 0, 0));
	JLabel detailsLabel = new JLabel("<html><div style='width: 520px; color: #A0A0A0; border-left: 3px solid #505050; padding-left: 12px;'>" + htmlDetails + "</div></html>");
	detailsLabel.setFont(detailsLabel.getFont().deriveFont(15f));
	detailsPanel.add(detailsLabel, BorderLayout.CENTER);

	toggle.addActionListener(e -> {
		expanded = !expanded;
		detailsPanel.setVisible(expanded);
		toggle.setText(expanded ? "▼ Hide Details" : "▶ Read Details");
		revalidate();
		repaint();
		if(getParent() instanceof JComponent parent) {
			parent.revalidate();
			parent.repaint();
		}
	});
	add(header, BorderLayout.NORTH);
	add(detailsPanel, BorderLayout.CENTER);
}

}

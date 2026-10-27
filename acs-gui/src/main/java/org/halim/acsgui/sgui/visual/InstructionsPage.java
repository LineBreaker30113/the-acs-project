package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.sgui.sglib.AccordionPanel;
import org.halim.acsgui.sgui.sglib.ManualParser;
import org.halim.acsgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.awt.*;


/** The instruction manual: an accordion for each entry of the resources' {@code manual.md}, its summary shown and its details a click away. */
public class InstructionsPage extends JPanel {

public InstructionsPage() {
	setLayout(new BorderLayout());
	setBackground(Utilities.WP_BG);

	JPanel content = new JPanel();
	content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
	content.setBackground(Utilities.WP_BG);
	content.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

	JLabel title = new JLabel("ACS GUI Manual");
	title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
	title.setForeground(Utilities.GOLDEN_COLOR);
	title.setAlignmentX(Component.LEFT_ALIGNMENT);
	JLabel subtitle = new JLabel("Scan the topics below. Expand a section for the details.");
	subtitle.setFont(subtitle.getFont().deriveFont(16f));
	subtitle.setForeground(Utilities.ELEMENT_TEXT_SECONDARY);
	subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
	content.add(title);
	content.add(subtitle);
	content.add(Box.createVerticalStrut(32));

	for(ManualParser.ManualEntry entry : ManualParser.loadManual()) {
		content.add(new AccordionPanel(entry.title(), entry.summary(), entry.details()));
		content.add(Box.createVerticalStrut(10));
	}
	content.add(Box.createVerticalGlue());

	JScrollPane scroll = new JScrollPane(content);
	scroll.setBorder(null);
	scroll.getViewport().setBackground(Utilities.WP_BG);
	scroll.getVerticalScrollBar().setUnitIncrement(16);
	add(scroll, BorderLayout.CENTER);
}

}

package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.LeftSidebar;
import org.halim.acsessgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.awt.*;
import java.util.List;


/**
 * acsed's databases, as OntoDirectory's LoadedLakesSection lists its lakes: a tab per database, showing
 * it on a click, with Store and Close under it. The list is read again on {@link #refresh}, since a
 * terminal may load or unload one at any time.
 * */
public class LoadedDatabasesSection extends JPanel {

final LeftSidebar leftSidebar;
final JPanel databasesContainer;
final JLabel headerLabel;
boolean isCollapsed;

public LoadedDatabasesSection(LeftSidebar leftSidebar) {
	this.leftSidebar = leftSidebar;
	setBackground(leftSidebar.getBackground().darker());
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

	JPanel headerPanel = new JPanel(new BorderLayout());
	headerPanel.setBackground(Color.BLACK);
	headerPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
	headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
	headerPanel.setPreferredSize(new Dimension(leftSidebar.expanded, 56));
	headerLabel = new JLabel("DATABASES", Utilities.loadSVGIcon("icons/database.svg", 40, 40, Utilities.GOLDEN_COLOR), SwingConstants.CENTER);
	headerLabel.setForeground(Utilities.GOLDEN_COLOR);
	headerLabel.setIconTextGap(8);
	headerLabel.setHorizontalTextPosition(SwingConstants.LEFT);
	headerPanel.add(headerLabel, BorderLayout.EAST);

	databasesContainer = new JPanel();
	databasesContainer.setOpaque(false);
	databasesContainer.setLayout(new BoxLayout(databasesContainer, BoxLayout.Y_AXIS));
	databasesContainer.setAlignmentX(Component.RIGHT_ALIGNMENT);

	add(headerPanel);
	add(Box.createVerticalStrut(8));
	add(databasesContainer);
}

/** Reads acsed's list on the request thread and rebuilds the tabs. */
public void refresh() {
	leftSidebar.owner.appController.request("list the databases", leftSidebar.owner.appController.servicePort::getLoadedDatabases, this::show);
}

void show(List<ACSESS_GUI_Service.DatabaseService> databases) {
	databasesContainer.removeAll();
	for(ACSESS_GUI_Service.DatabaseService database : databases) { databasesContainer.add(tab(database)); }
	updateCollapseState(isCollapsed);
	revalidate();
	repaint();
}

JPanel tab(ACSESS_GUI_Service.DatabaseService database) {
	var app = leftSidebar.owner.appController;
	JPanel container = new JPanel();
	container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
	container.setOpaque(false);
	container.setAlignmentX(Component.RIGHT_ALIGNMENT);

	JButton mainButton = new JButton(database.getName());
	mainButton.setActionCommand(database.getName());
	mainButton.setToolTipText(database.getFolder().toString());
	mainButton.addActionListener(e -> app.perform("show " + database.getName(), () -> app.servicePort.dispatchDatabaseChooseRequest(database.getName()), () -> { }));
	mainButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
	mainButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
	mainButton.setPreferredSize(new Dimension(40, 40));
	mainButton.setHorizontalAlignment(SwingConstants.LEFT);
	mainButton.putClientProperty("collapseIcon", Utilities.loadSVGIcon("icons/hard-drives.svg", 32, 32));
	Utilities.initButton(mainButton);

	JPanel actionBar = new JPanel();
	actionBar.setName("actionBar");
	actionBar.setLayout(new BoxLayout(actionBar, BoxLayout.X_AXIS));
	actionBar.setAlignmentX(Component.RIGHT_ALIGNMENT);
	actionBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
	actionBar.add(Box.createHorizontalGlue());
	JButton storeBtn = actionButton("Store", "download-simple.svg");
	storeBtn.addActionListener(e -> app.request("store " + database.getName(), database::store, answer ->
		JOptionPane.showMessageDialog(leftSidebar.owner, answer, "Stored", JOptionPane.INFORMATION_MESSAGE)));
	JButton closeBtn = actionButton("Close", "x-square.svg");
	closeBtn.addActionListener(e -> {
		int res = JOptionPane.showConfirmDialog(leftSidebar.owner, "Store and unload database '" + database.getName() + "' from acsed?", "Confirm Close", JOptionPane.YES_NO_OPTION);
		if(res == JOptionPane.YES_OPTION) { app.dispatchDatabaseCloseRequest(database); }
	});
	actionBar.add(storeBtn);
	actionBar.add(Box.createHorizontalStrut(4));
	actionBar.add(closeBtn);

	container.add(mainButton);
	container.add(actionBar);
	return container;
}

JButton actionButton(String text, String iconName) {
	JButton btn = new JButton(text, Utilities.loadSVGIcon("icons/" + iconName, 14, 14, Utilities.ELEMENT_TEXT_PRIMARY));
	btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
	btn.setPreferredSize(new Dimension(85, 30));
	btn.setMaximumSize(new Dimension(85, 30));
	btn.setFocusPainted(false);
	return btn;
}

public void updateCollapseState(boolean isCollapsed) {
	this.isCollapsed = isCollapsed;
	headerLabel.setText(isCollapsed ? "" : "DATABASES");
	for(Component tab : databasesContainer.getComponents()) {
		JPanel container = (JPanel) tab;
		JButton mainButton = (JButton) container.getComponent(0);
		mainButton.setText(isCollapsed ? "" : mainButton.getActionCommand());
		mainButton.setIcon(isCollapsed ? (Icon) mainButton.getClientProperty("collapseIcon") : null);
		mainButton.setHorizontalAlignment(isCollapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
		container.getComponent(1).setVisible(!isCollapsed);
	}
}

}

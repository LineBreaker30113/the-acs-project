package org.halim.acsessgui.sgui.visual;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.ApplicationController;
import org.halim.acsessgui.sgui.GUI_RootPanel;
import org.halim.acsessgui.sgui.sglib.Utilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;


/** The connection page: how acsed stands, creating or loading a database into it, and the ones it holds, opened with a double click. */
public class WelcomePage extends JPanel {

final GUI_RootPanel owner;
final JLabel daemonState = new JLabel("asking acsess to boot acsed...");
final DefaultListModel<ACSESS_GUI_Service.DatabaseService> databases = new DefaultListModel<>();
final JList<ACSESS_GUI_Service.DatabaseService> databaseList = new JList<>(databases);

public WelcomePage(GUI_RootPanel owner) {
	this.owner = owner;
	setBackground(Utilities.WP_BG);
	setLayout(new BorderLayout(0, 16));
	setBorder(BorderFactory.createEmptyBorder(32, 48, 32, 48));

	JPanel top = new JPanel();
	top.setOpaque(false);
	top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
	JLabel title = new JLabel("ACSESS GUI");
	title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
	title.setForeground(Utilities.GOLDEN_COLOR);
	JLabel subtitle = new JLabel("The databases acsed holds, shown and edited through acsess.");
	subtitle.setForeground(Utilities.ELEMENT_TEXT_SECONDARY);
	daemonState.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	top.add(title);
	top.add(Box.createVerticalStrut(4));
	top.add(subtitle);
	top.add(Box.createVerticalStrut(16));
	top.add(daemonState);
	top.add(Box.createVerticalStrut(16));

	JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	actions.setOpaque(false);
	actions.setAlignmentX(Component.LEFT_ALIGNMENT);
	JButton boot = new JButton("Boot acsed");
	boot.addActionListener(e -> app().request("boot acsed", app().servicePort::boot, this::showDaemonState));
	JButton create = new JButton("+ Create Database...");
	create.addActionListener(e -> chooseFolder("Choose an absent or empty folder for the database", folder ->
		app().perform("create a database in " + folder, () -> app().servicePort.createDatabase(folder), () -> { })));
	JButton load = new JButton("Load Database...");
	load.addActionListener(e -> chooseFolder("Choose a database's folder", folder ->
		app().perform("load " + folder, () -> app().servicePort.loadDatabase(folder), () -> { })));
	JButton refresh = new JButton("Refresh");
	refresh.addActionListener(e -> refreshDatabases());
	for(JButton button : List.of(boot, create, load, refresh)) {
		actions.add(button);
		actions.add(Box.createHorizontalStrut(8));
	}
	top.add(actions);
	add(top, BorderLayout.NORTH);

	JPanel listPanel = new JPanel(new BorderLayout(0, 8));
	listPanel.setOpaque(false);
	JLabel listTitle = new JLabel("Databases acsed holds (double click to open)");
	listTitle.setForeground(Utilities.GOLDEN_COLOR);
	listPanel.add(listTitle, BorderLayout.NORTH);
	databaseList.setBackground(Utilities.LIST_BG);
	databaseList.setCellRenderer(new DefaultListCellRenderer() {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			ACSESS_GUI_Service.DatabaseService database = (ACSESS_GUI_Service.DatabaseService) value;
			return super.getListCellRendererComponent(list, "<html><b>" + escaped(database.getName()) + "</b>&nbsp;&nbsp;<span style='color:#969696'>" + escaped(database.getFolder().toString()) + "</span></html>", index, isSelected, cellHasFocus);
		}
	});
	databaseList.addMouseListener(new MouseAdapter() {
		@Override
		public void mouseClicked(MouseEvent e) {
			ACSESS_GUI_Service.DatabaseService chosen = databaseList.getSelectedValue();
			if(e.getClickCount() == 2 && chosen != null) {
				app().perform("show " + chosen.getName(), () -> app().servicePort.dispatchDatabaseChooseRequest(chosen.getName()), () -> { });
			}
		}
	});
	listPanel.add(new JScrollPane(databaseList), BorderLayout.CENTER);
	add(listPanel, BorderLayout.CENTER);
}

ApplicationController app() { return owner.appController; }

static String escaped(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

void chooseFolder(String title, java.util.function.Consumer<Path> chosen) {
	JFileChooser chooser = new JFileChooser();
	chooser.setDialogTitle(title);
	chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { chosen.accept(chooser.getSelectedFile().toPath()); }
}

/** Shows acsess's answer to boot, then lists the databases, which needs acsed answering. */
public void showDaemonState(String answer) {
	daemonState.setText("acsed: " + answer);
	refreshDatabases();
	owner.leftSidebar.loadedDatabasesSection.refresh();
}

public void refreshDatabases() {
	app().request("list the databases", app().servicePort::getLoadedDatabases, list -> {
		databases.clear();
		for(ACSESS_GUI_Service.DatabaseService database : list) { databases.addElement(database); }
	});
}

}

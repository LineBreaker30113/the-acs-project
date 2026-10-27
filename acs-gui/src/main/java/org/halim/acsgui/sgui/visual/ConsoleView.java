package org.halim.acsgui.sgui.visual;

import org.halim.acsgui.mhexagon.ACSGUI_Service;
import org.halim.acsgui.sgui.ApplicationController;
import org.halim.acsgui.sgui.sglib.Utilities;
import org.halim.acsgui.sgui.sglib.WorkSpaceViewPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;


/**
 * The request console, as an SQL tool's editor: acsess lines, one request each, written as a terminal's
 * {@code beginSession} takes them and run as one connection with Ctrl+Enter. Each answer is printed under
 * its request; afterwards every view reads again, since a request may have changed anything. A blank line
 * is skipped; a quote spanning lines is not taken here, since each line is one request.
 * */
public class ConsoleView extends WorkSpaceViewPanel {

final JTextArea input = new JTextArea(8, 40);
final JTextArea output = new JTextArea(12, 40);
final JCheckBox continueOnRefusal = new JCheckBox("Run past a refusal");

public ConsoleView(ApplicationController app) {
	super(app);
	input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
	output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
	output.setEditable(false);
	output.setForeground(Utilities.ELEMENT_TEXT_PRIMARY);
	input.setText("databases\n");
	input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "run");
	input.getActionMap().put("run", new AbstractAction() {
		@Override public void actionPerformed(java.awt.event.ActionEvent e) { run(); }
	});

	JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
	JButton runButton = new JButton("Run (Ctrl+Enter)");
	runButton.addActionListener(e -> run());
	JButton address = new JButton("Insert Address");
	address.setToolTipText("The shown database's name and a colon, as an actor's request starts");
	address.addActionListener(e -> {
		ACSGUI_Service.DatabaseService database = database();
		if(database != null) { input.replaceSelection(database.getName() + ":"); }
		input.requestFocusInWindow();
	});
	JButton clear = new JButton("Clear Output");
	clear.addActionListener(e -> output.setText(""));
	tools.add(runButton);
	tools.add(continueOnRefusal);
	tools.add(address);
	tools.add(clear);

	JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(input), new JScrollPane(output));
	split.setResizeWeight(0.4);
	add(tools, BorderLayout.NORTH);
	add(split, BorderLayout.CENTER);
	setPreferredSize(new Dimension(520, 600));
}

void run() {
	ArrayList<String> lines = new ArrayList<>();
	for(String line : input.getText().split("\n")) { if(!line.isBlank()) { lines.add(line.strip()); } }
	if(lines.isEmpty()) { return; }
	boolean past = continueOnRefusal.isSelected();
	app.request("run the console's requests", () -> app.servicePort.runSession(lines, past), this::print);
}

void print(List<ACSGUI_Service.Answer> answers) {
	StringBuilder text = new StringBuilder(output.getText());
	for(ACSGUI_Service.Answer answer : answers) {
		if(!text.isEmpty()) { text.append('\n'); }
		text.append("> ").append(answer.typed()).append('\n');
		String body = answer.done() ? answer.text() : (answer.text().equals("not run") ? "not run" : "refused: " + answer.text());
		text.append("  ").append(body.replace("\n", "\n  "));
	}
	updated.run();
	output.setText(text.toString());
	output.setCaretPosition(output.getDocument().getLength());
	app.view.leftSidebar.loadedDatabasesSection.refresh();
	app.view.centerPanel.welcomePage.refreshDatabases();
	ACSGUI_Service.DatabaseService database = database();
	if(database != null) { app.wsModeller.triggerDatabaseRefresh(database); }
}

}

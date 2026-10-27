package org.halim.acsgui;

import org.halim.acsgui.acsess.Acsess;
import org.halim.acsgui.acsess.AcsessServer;
import org.halim.acsgui.sgui.ApplicationController;

import javax.swing.*;
import java.util.List;


/**
 * The entry point, as OntoDirectory's NewMain: the service, then the GUI on it. The one argument,
 * {@code acsessPath=<path>}, names the acsess program to run; without it, ACSES's
 * {@code acses/target/acsess/bin/acsess} inside the project, so nothing outside it is used. acsess finds acsed as a terminal's does, through {@code $ACSED_SOCKET} or the
 * runtime folder, and acsed is left running when the window closes, as acsess leaves it.
 * */
public class ACSGUI_Main {

public static AcsessServer servicePort;
public static ApplicationController guiAdapter;

public static void main(String[] args) {
	List<String> program = program(args);
	servicePort = new AcsessServer(new Acsess(program));
	com.formdev.flatlaf.FlatDarkLaf.setup();
	guiAdapter = new ApplicationController(servicePort);
	SwingUtilities.invokeLater(() -> {
		JFrame frame = new JFrame("ACS GUI");
		guiAdapter.deployTo(frame);
	});
}

static List<String> program(String[] args) {
	for(String arg : args) {
		if(arg.startsWith("acsessPath=")) { return List.of(arg.substring("acsessPath=".length())); }
		throw new IllegalArgumentException("unknown argument " + arg + "; the only one is acsessPath=<path>");
	}
	for(String candidate : List.of("acses/target/acsess/bin/acsess", "../acses/target/acsess/bin/acsess")) {
		java.nio.file.Path path = java.nio.file.Path.of(candidate);
		if(java.nio.file.Files.isExecutable(path)) { return List.of(path.toAbsolutePath().normalize().toString()); }
	}
	throw new IllegalStateException("acsess not found: build it with mvn package, or give acsessPath=<path>");
}

}

package org.halim.acsessgui;

import org.halim.acsessgui.acsess.Acsess;
import org.halim.acsessgui.acsess.AcsessServer;
import org.halim.acsessgui.sgui.ApplicationController;

import javax.swing.*;
import java.util.List;


/**
 * The entry point, as OntoDirectory's NewMain: the service, then the GUI on it. The one argument is the
 * acsess program to run, such as ACSES's {@code target/acsess/bin/acsess}; without it, {@code acsess}
 * as the PATH finds it. acsess finds acsed as a terminal's does, through {@code $ACSED_SOCKET} or the
 * runtime folder, and acsed is left running when the window closes, as acsess leaves it.
 * */
public class ACSESS_GUI_Main {

public static AcsessServer servicePort;
public static ApplicationController guiAdapter;

public static void main(String[] args) {
	List<String> program = args.length == 0 ? List.of("acsess") : List.of(args);
	servicePort = new AcsessServer(new Acsess(program));
	com.formdev.flatlaf.FlatDarkLaf.setup();
	guiAdapter = new ApplicationController(servicePort);
	SwingUtilities.invokeLater(() -> {
		JFrame frame = new JFrame("ACSESS GUI");
		guiAdapter.deployTo(frame);
	});
}

}

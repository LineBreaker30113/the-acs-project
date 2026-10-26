package org.halim.acsessgui.sgui;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;
import org.halim.acsessgui.sgui.sglib.WorkspaceListener;
import org.halim.acsessgui.sgui.visual.ContentPanel;

import java.util.ArrayList;
import java.util.List;


/**
 * The workspace's state, fanned out to the views, as OntoDirectory's WorkspaceController fans out the lake
 * and the tag: the shown database; the focused element; and the content panels, one at most listening for
 * each kind.
 * <p>
 * Each view has a single goal, so no view chooses for another: a click on an element, in a lister or any
 * panel, focuses it and changes no panel; a click on the element already focused gives it to every panel
 * whose kind takes its type, one panel a kind. Choosing for a panel a kind another panel listens for swaps
 * the two panels' states, so the rule of one listener a kind holds without refusing anything.
 * */
public class WorkspaceController {

public final ApplicationController owner;
final List<WorkspaceListener> workspaceListeners = new ArrayList<>();
final List<ContentPanel> contentPanels = new ArrayList<>();
WorkspaceElement focused;
String shownName;

public WorkspaceController(ApplicationController owner) { this.owner = owner; }

public void registerListener(WorkspaceListener listener) {
	if(!workspaceListeners.contains(listener)) { workspaceListeners.add(listener); }
}

public void removeListener(WorkspaceListener listener) { workspaceListeners.remove(listener); }

/**
 * After any change, or with null when no database is shown: what was read and kept is dropped, then every
 * view reads again. Another database drops the focus, which names the former one's slots.
 * */
public void triggerDatabaseRefresh(ACSESS_GUI_Service.DatabaseService database) {
	String name = database == null ? null : database.getName();
	if(name == null || !name.equals(shownName)) { focused = null; }
	shownName = name;
	if(database != null) { database.forget(); }
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onSelectedDatabaseChange(database); }
}

public WorkspaceElement getFocused() { return focused; }

/** The first click focuses the element; a click on it once focused gives it to each panel listening for a kind that takes it. */
public void click(WorkspaceElement element) {
	if(element.equals(focused)) {
		for(ContentPanel.Kind kind : ContentPanel.Kind.values()) {
			if(!kind.takes(element.type())) { continue; }
			ContentPanel listening = listenerOf(kind);
			if(listening != null) { listening.take(element); }
		}
		return;
	}
	focused = element;
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onFocusChange(element); }
}

public void registerContent(ContentPanel panel) {
	if(!contentPanels.contains(panel)) { contentPanels.add(panel); }
	registerListener(panel);
}

public void removeContent(ContentPanel panel) {
	contentPanels.remove(panel);
	removeListener(panel);
}

public ContentPanel listenerOf(ContentPanel.Kind kind) {
	if(kind == null) { return null; }
	for(ContentPanel panel : contentPanels) { if(panel.getKind() == kind) { return panel; } }
	return null;
}

/** The panel takes the kind; a panel already listening for it takes the panel's former kind and element in exchange. */
public void claim(ContentPanel panel, ContentPanel.Kind kind) {
	ContentPanel holder = listenerOf(kind);
	if(holder == panel) { return; }
	ContentPanel.Kind formerKind = panel.getKind();
	WorkspaceElement formerElement = panel.getElement();
	WorkspaceElement taken = holder == null ? null : holder.getElement();
	if(holder != null) { holder.assume(formerKind, formerElement); }
	panel.assume(kind, taken);
}

}

package org.halim.acsessgui.sgui;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;
import org.halim.acsessgui.sgui.sglib.WorkspaceElement;
import org.halim.acsessgui.sgui.sglib.WorkspaceListener;
import org.halim.acsessgui.sgui.visual.ContentPanel;

import java.util.ArrayList;
import java.util.List;


/**
 * The workspace's state, fanned out to the views, as OntoDirectory's WorkspaceController fans out the lake
 * and the tag: the shown database; the arity, the structure and the class chosen in the views listing them;
 * the focused element; and the content panels, at most one listening for each type.
 * <p>
 * A click on an element focuses it and changes no panel; a click on the element already focused gives it
 * to the panel listening for its type. Choosing for a panel a type another panel listens for swaps the two
 * panels' states, so the rule of one listener a type holds without refusing anything.
 * */
public class WorkspaceController {

public final ApplicationController owner;
final List<WorkspaceListener> workspaceListeners = new ArrayList<>();
final List<ContentPanel> contentPanels = new ArrayList<>();
int selectedArity = -1, selectedStructure = -1;
String selectedClass;
WorkspaceElement focused;
String shownName;

public WorkspaceController(ApplicationController owner) { this.owner = owner; }

public void registerListener(WorkspaceListener listener) {
	if(!workspaceListeners.contains(listener)) { workspaceListeners.add(listener); }
}

public void removeListener(WorkspaceListener listener) { workspaceListeners.remove(listener); }

/**
 * After any change, or with null when no database is shown: what was read and kept is dropped, then every
 * view reads again. Another database drops the choices and the focus, which name the former one's slots.
 * */
public void triggerDatabaseRefresh(ACSESS_GUI_Service.DatabaseService database) {
	String name = database == null ? null : database.getName();
	if(name == null || !name.equals(shownName)) {
		selectedArity = -1;
		selectedStructure = -1;
		selectedClass = null;
		focused = null;
	}
	shownName = name;
	if(database != null) { database.forget(); }
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onSelectedDatabaseChange(database); }
}

public void broadcastAritySelection(int arity) {
	selectedArity = arity;
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onSelectedArityChange(arity); }
}

public void broadcastStructureSelection(int structure) {
	selectedStructure = structure;
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onSelectedStructureChange(structure); }
}

public void broadcastClassSelection(String className) {
	selectedClass = className;
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onSelectedClassChange(className); }
}

public int getSelectedArity() { return selectedArity; }

public int getSelectedStructure() { return selectedStructure; }

public String getSelectedClass() { return selectedClass; }

public WorkspaceElement getFocused() { return focused; }

/** The first click focuses the element; a click on it once focused gives it to the panel listening for its type, if one does. */
public void click(WorkspaceElement element) {
	if(element.equals(focused)) {
		ContentPanel listening = listenerOf(element.type());
		if(listening != null) { listening.take(element); }
		return;
	}
	focused = element;
	for(WorkspaceListener listener : List.copyOf(workspaceListeners)) { listener.onFocusChange(element); }
}

/**
 * A click in the one view whose goal is choosing the element's type, the arities view for an arity and
 * the structures view for a structure: it chooses the element for the views that follow that choice, then
 * clicks it. Every other view only clicks.
 * */
public void choose(WorkspaceElement element) {
	if(element.type() == WorkspaceElement.Type.ARITY) { broadcastAritySelection(element.arity()); }
	else if(element.type() == WorkspaceElement.Type.STRUCTURE) { broadcastStructureSelection(element.structure()); }
	click(element);
}

public void registerContent(ContentPanel panel) {
	if(!contentPanels.contains(panel)) { contentPanels.add(panel); }
	registerListener(panel);
}

public void removeContent(ContentPanel panel) {
	contentPanels.remove(panel);
	removeListener(panel);
}

public ContentPanel listenerOf(WorkspaceElement.Type type) {
	if(type == null) { return null; }
	for(ContentPanel panel : contentPanels) { if(panel.getType() == type) { return panel; } }
	return null;
}

/** The panel takes the type; a panel already listening for it takes the panel's former type and element in exchange. */
public void claim(ContentPanel panel, WorkspaceElement.Type type) {
	ContentPanel holder = listenerOf(type);
	if(holder == panel) { return; }
	WorkspaceElement.Type formerType = panel.getType();
	WorkspaceElement formerElement = panel.getElement();
	WorkspaceElement taken = holder == null ? null : holder.getElement();
	if(holder != null) { holder.assume(formerType, formerElement); }
	panel.assume(type, taken);
}

}

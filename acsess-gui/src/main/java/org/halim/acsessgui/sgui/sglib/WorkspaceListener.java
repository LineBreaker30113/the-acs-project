package org.halim.acsessgui.sgui.sglib;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;


/**
 * What a workspace view hears: the shown database, null when none, read again after any change; the arity,
 * the structure and the class chosen in the views that list them; and the focused element.
 * */
public interface WorkspaceListener {

void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database);

void onSelectedArityChange(int arity);

void onSelectedStructureChange(int structure);

void onSelectedClassChange(String className);

void onFocusChange(WorkspaceElement focused);

}

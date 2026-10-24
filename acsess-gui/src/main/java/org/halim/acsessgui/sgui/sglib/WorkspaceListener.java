package org.halim.acsessgui.sgui.sglib;

import org.halim.acsessgui.mhexagon.ACSESS_GUI_Service;


/** What a workspace view hears: the shown database, null when none, read again after any change; and the focused element. */
public interface WorkspaceListener {

void onSelectedDatabaseChange(ACSESS_GUI_Service.DatabaseService database);

void onFocusChange(WorkspaceElement focused);

}

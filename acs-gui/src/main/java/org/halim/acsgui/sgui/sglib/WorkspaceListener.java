package org.halim.acsgui.sgui.sglib;

import org.halim.acsgui.mhexagon.ACSGUI_Service;


/** What a workspace view hears: the shown database, null when none, read again after any change; and the focused element. */
public interface WorkspaceListener {

void onSelectedDatabaseChange(ACSGUI_Service.DatabaseService database);

void onFocusChange(WorkspaceElement focused);

}

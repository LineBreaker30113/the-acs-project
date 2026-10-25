# acsess-gui

- Package `org.halim.acsessgui`, `ACSESS_GUI_Main` and `ACSESS_GUI_Service` (4), replacing `org.halim.acsview` (1).
- The author believes the GUI must have views like core, journal, classes, arities, structures,
  procedures, and content, which may be many and can differ in focus (2); GUI decisions live in this
  module's folder, all confirmed (3).
- The core view doesn't show a tree; arities view lists arities; the structure view shows a structure,
  not the structures, on a plane where zooming and movement are allowed, but we can make a separate
  structures view (5).
- Classes are loaded class files whose identity int can match data in other views; it would be cool to
  add a code view with a java decompiler, Vineflower (6).
- Each view has a single goal only, to descend down (10); views must refresh each other when necessary (11).
- We do not need all views on the left; listers can become any type of lister (arities, structures,
  classes, procedures) alongside content buttons (12).
- Users will likely have many views open; rows of columns can be used if we can trust Swing, and
  custom docking is not an option (7).
- There may be any number of content panels, at most five holding a type; clicking focuses but doesn't
  change the content view, and a user can drag and drop an element without changing other panels; only
  one type of panel listens to only one type of element, and do not allow a second one (8).
- Most recent reading is the source, and more can come later; arity summary view must handle capturing
  and deleting identities, and a user can edit values from arity content, with no plane replacing
  structures (13).
- Descriptions for the manual and daily tips can be written later (9).

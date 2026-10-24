# Plan — acsess-gui: the rename, the views and the content panels

Written 2026-10-23 by Claude, for one execution session, which stops for the author's review and commit.
**The design** is the author's decisions, Claude's readings of them (marked *reading*), and Claude's
options the author picked. **The author's words** are the source. A decision in a folder's files wins any
conflict with this plan: stop and ask. Round 4's plan is in git at `141008e`, the migration's at `553ecc3`.
Edited at the session's checkpoint, 2026-10-23, from the author's answers there.

## The design

### The rename, first

- The module `acs-view` becomes `acsess-gui`: its folder (by `git mv`), its `artifactId`, its `<name>`
  `ACSESS GUI`, the package `org.halim.acsview` → `org.halim.acsessgui`, `ACSViewMain` →
  `ACSESS_GUI_Main`, `ACSViewService` → `ACSESS_GUI_Service`, and the comment every change carries,
  `acs-view` → `acsess-gui`.
- Following the service's name (*reading*, the checkpoint's questions 1 and 2, left to Claude's guess):
  the nested `ACSViewServiceListener` → `ACSESS_GUI_ServiceListener`, `addACSViewServiceListener` →
  `addACSESS_GUI_ServiceListener`; the window's and the welcome page's title "ACS View" → "ACSESS GUI";
  the request thread `ACSView-Request-Thread` → `ACSESS_GUI-Request-Thread`. The other class names stay.
- The root pom lists `acsess-gui` among its modules, so `mvn compile` builds it with the rest; it still
  depends on nothing in `acs`, `acse` or `acses`.
- `.idea/compiler.xml` and `.idea/encodings.xml` follow the folder's name.
- The folder's three files move with it. Claude's words follow the name: the headers, `CLAUDE.md`'s
  lines, `markdowns/CLAUDE.md`'s line naming `acs-view/CLAUDE.md`, and the path
  `acs-view/agents-citable.md` in root 68 (checkpoint question 3). The author's words stay as written,
  "ACS-view" and "acs-viewer" included, under the quote test, and so do the brackets of root 30 and 31.
  `markdowns/new-architecture.md` is not touched.

### The layout

- The views sit in rows of columns, not in one row: "(c) if we can trust swing". *Reading*: Swing is
  trusted here, since a row is today's mechanism, the wrappers in a `BoxLayout`, repeated; no docking
  library, no drag-to-split. A view's header keeps `<` and `>`, moving it in its row, and gains `^` and
  `v`, moving it to the row above or below; `v` on the last row makes a new row; an emptied row goes.

### The views

- Each view shows what its name says: "The views show contents of what their names implies, simple."
  The navigator goes, and the data grid becomes the arity table view.

| View | Shows | Requests and files |
|---|---|---|
| Core | the core's class, its arity and structure counts; its requests as actions: add arity, add row, remove arity, remove structure, capture identity, delegate removal | `components`; `addArity`, `addCatalogRow`, `removeArity`, `removeStructure`, `captureIdentity`, `delegateRemoval`; the core's class from the indexer |
| Journal | where it stands, undo and redo of one activity or to an index, store | `getCurrentActivityIndex`, `getPendingActivityCount`, `undo`/`redoLastActivity`, `undo`/`redoUntilActivityIndex`, `storeDB` |
| Classes | the loaded class files, each with its number, and the arities and structures of that class | the current store's `classes.jar` and `indexer.bin` |
| Code | the class chosen in the classes view, decompiled | the class file from `classes.jar`; Vineflower |
| Arities | the arities: slot, description, class and its number, identity count, row count | `components`; the indexer |
| Arity Table | the arity chosen in the arities view as a table, edited in place: today's data grid | `occupiedIdentities`, `get`/`set<T>For`, `captureIdentity`, `delegateRemoval` |
| Structures | the structures and the arities as nodes in a plane, an edge per arity a structure spans; zoom and movement | `components` |
| Structure | the structure chosen in the structures view: its class, description and spanned arities, and the connections it creates in a plane with zoom and movement; the connections wait for their requests | `components`; none reads a structure's relations yet |
| Procedures | "waits for its requests" | none |
| Content | the content panels, below | per type |
| Request Console | acsess lines run as one session, kept as it is | `beginSession` |

- The classes, checkpoint option (a): a class's number is the one the current store's `indexer.bin`
  gives it, "numbered in the order met"; it is matched to the other views by the class's name, which
  `components` prints. The current store is the generation `acse-persistence.data` names. So the GUI reads
  two ACSE files besides running acsess; a class used since the last store shows no number.
- The code view decompiles with Vineflower (Apache-2.0, Java 17+), a dependency of this module, the first
  download this round needs.

### The content panels

- A content panel has a selection at its top: `arity`, `structure`, `procedure`, `arity-row`,
  `arity-identity`. What each shows (Claude's suggestions, taken):
  - `arity`: its row types and its identity count;
  - `arity-row`: the row's values over the identities, a page at a time;
  - `arity-identity`: the identity's value in every row of its arity;
  - `structure`: its class, description and spanned arities;
  - `procedure`: "waits for its requests"; it takes no element, since `components` lists none.
- One panel listens for one type, and only one panel listens for a type: "Only one type of panel
  listens only one type of element." *Reading* of "If user tries to create a second one simply switch
  the states": choosing in panel B a type panel A holds swaps the two panels' states, type and element.
  A new panel opens with no type, so any number may be open; at most five hold a type. This takes the
  newer rule over "There may be any number of content panels" (Q4), as far as they conflict.
- Clicking an element focuses it and changes no panel; clicking the already focused element puts it in
  the panel listening for its type, if one does.
- An element, focused or not, dragged and dropped onto the panel of its type changes that panel only; a
  drop onto a panel of another type, or of none, is refused.
- *Reading*, the checkpoint's question 5, taken: an element is focused and dragged from the arities view
  (an arity), the arity table view (an identity's line, a row's column), the structures view and the
  structure view (a structure, an arity), and a content panel's own lines (an identity of a row, a row of
  an identity).
- The panels show; editing stays in the arity table view (*reading*, the checkpoint's question 6).

### What is read is kept

- "keeps the queried data in memory" (root 72, confirmed): what a view read is kept, so another view or
  panel showing the same thing reads it without a program run; after a change, the kept data is dropped
  and read again, as every view reads again today.

## The author's words

The author, 2026-10-23:

- "I want you to plan a session that will complete the acs-view, but prior to that rename it to
  acsess-gui, since it is what it is."
- On Claude's options: "(Q1: a)" — package `org.halim.acsessgui`, `ACSESS_GUI_Main` and
  `ACSESS_GUI_Service`, matching `ACSED_`/`ACSESS`; "(Q2: a)" — it joins the root pom's modules;
  "(Q3: a)" — every view acsess can serve today: core, journal, classes, arities, content; the structures
  and procedures views exist but say they wait for requests; "(Q6: a)" — this file, and a launcher.
- "(Q4: The content page will have a selection at the top for "arity" [lists the row types and identity
  count], "structure", "procedure", "arity-row", "arity-identity". The content will take the focused [but
  clicking after focus will trigger it for alignment with the drag-and-drop ability] instance of it's
  type. Or an unfocused element can be dragged and dropped into it. There may be any number of content
  panels. The session can ask me questions.)"
- On the unreviewed "keeps the queried data in memory": "(Q5: Do not overthink on it, I am about to
  confirm.)"

The author, at the session's checkpoint, 2026-10-23:

- "Clicking an already focused element makes it appear in the listening content view panel
  automatically, clicking focuses but doesn't change the content view. So user can simply drag and drop
  a single element to the content view panels without changing other content view panels."
- "The core view doesn't show a tree."
- "The classes are the loaded java class files, each has an identity int which can match data showed in
  other views (like arities view or the structures view). This means it would been cool if we add a code
  view that uses a java decompiler, tell me if feasible."
- "The "arities view" lists the arities. We need a separate "arity table view" for viewing the details
  of the arities."
- "The structure view is like you guessed, but it also shows the connections it creates, in a plane
  where zooming and movement allowed."
- "This model has a lot of view types, and users will likely have many views running on the same time,
  we need to design a better gui than just panels (as columns) on a single row."
- "Go with your suggestions. For "Class identities: ..." go with a. The structure view will show a
  structure, not the structures. But we can make a separate "structures view" for what you said."
- "For "Layout ..." the (b) is not an option because the swing keeps breaking. We can go with (c) if we
  can trust swing, if not and the ModernDocking is trustable go with it."
- "1: Only one type of panel listens only one type of element, that is the rule, and easy to enforce.
  Example: A content view panel that chose arity only listens for arities, and only that content panel
  listens for them, do not allow a second one. If user tries to create a second one simply switch the
  states."
- "2: Yes, your suggestions align with my intentions perfectly." — the core view's contents.
- "3: Yes." — the navigator goes.
- "4: The views show contents of what their names implies, simple. I think you can guess the answers for
  all the questions."
- "5: Yes, edit it." — this file.

## Facts, checked 2026-10-23

- `acs-view`: 25 classes, 1,976 lines, committed at `f979710`; built on its own, not in the root pom's
  `<modules>`. Views: `WelcomePage`, `NavigatorView`, `DataGridView`, `JournalView`, `ConsoleView`, each a
  `WorkSpaceViewPanel` placed by `WorkspacePanel` and toggled by `WorkspaceSection`.
- The shown database and its chosen arity are `WorkspaceController`'s, fanned out by
  `triggerDatabaseRefresh` and `broadcastAritySelection(int)`; no other element can be chosen yet.
- Every request runs on `ApplicationController`'s one request thread, as an acsess program run.
- acsess's requests: 7 of the core, 12 of an arity, 9 of the journal, and acsed's own (7 at subject -1, 9
  under a database); none of a structure or a procedure. `components` lists a structure with the arities
  it spans.
- The name `acs-view` appears in 26 Java files (139 lines), the pom, the two `.idea` files, the folder's
  `CLAUDE.md` and `agents-citable.md`, and `markdowns/`: `CLAUDE.md`, `agents-citable.md` (entries 30 and
  31's brackets), `agents-unreviewed.md` and `new-architecture.md`.
- At the checkpoint, after `bf3b79b`: `markdowns/CLAUDE.md` names `acs-view` only in line 99
  (`acs-view/CLAUDE.md`); `markdowns/agents-unreviewed.md` no longer names it; root 68 holds the path
  `acs-view/agents-citable.md`; root 72 is in `markdowns/agents-citable.md`; `acs-view` has no
  `agents-unreviewed.md`.
- The class numbers: `ACSE_PersistenceManagerBasic.ClassTable`, written by `ACSE_CoreBasic.writeIndexer`:
  the header, the journal's class, the class names by number, then each arity slot's and each structure
  slot's class number, -1 for an emptied slot. Vineflower 1.11.1 and ModernDocking (`modern-docking-api`
  1.1.2) are the latest on Maven Central; neither is in `~/.m2`.

## The session

1. **Checkpoint.** `git status`; say whether the root's "keeps the queried data in memory" entry is now
   in `markdowns/agents-citable.md`. Present the design in ten lines, then two things to decide, and wait:
   - the views table: per view of the seven, what it shows, the requests it uses, and which of today's
     views it becomes or keeps (the navigator, the grid and the console included);
   - the content panels: what `arity-row`, `arity-identity`, `structure` and `procedure` show, and the
     reading of the click after focus.
   Ask anything else the design leaves open, with numbered options; the author: "The session can ask me
   questions."
2. **The rename**, mechanical; then `mvn -o -q compile` from the root.
3. **The layout and the focus**: rows of columns; an element of each type can be focused and dragged
   from the views the content panels' section lists.
4. **The content panels**: the selection, the five types, the listening rule and its switch, the click
   and the drop, any number open.
5. **The views** as the table says; Vineflower added to the module's pom, downloaded once.
6. **What is read is kept.**
7. **Verify**: `mvn -o -q compile`. A check outside the repo, in `~/mein/claude/scripts/`, driving
   `ACSESS_GUI_Service` against an acsed booted on a scratch socket (as `acses-smoke/run.sh` does): a
   database made, an arity with two rows, identities captured, a value set; then each content type's
   reads, the class numbers read from the indexer, one class decompiled, and kept data dropped after a
   change. Start the GUI once and say whether it opens; with no display, say so. Results dated in
   `~/mein/claude/results/`.
8. **Records**: propose entries for this round's decisions, in the format `CLAUDE.md` → Decisions gives:
   the rename to the root's `agents-unreviewed.md`; the class names, the views, the layout and the content
   panels to `acsess-gui/agents-unreviewed.md`, one `Replaces: acsess-gui 1` for the package. Add them on
   approval.
9. **Docs**: list the lines the work makes false (the folder's `CLAUDE.md`, `markdowns/CLAUDE.md`, the
   pom's description), propose replacements, edit on approval.
10. **Stop**: `git status`, the diff's summary, the checks' results. No commit until the author says.

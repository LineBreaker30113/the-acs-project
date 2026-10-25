# acsess-gui: citable

- **1.** [The Java package, picked among Claude's options:] org.halim.acsview. *(2026-10-22)* [replaced by: acsess-gui 4]
- **2.** I believe the GUI must have views like "core", "journal", "classes", "arities", "structures",
  "procedures", "content" (content pages may be many and can focus on different things).
  *(2026-10-22)*
- **3.** [Where these decisions live:] I want you to record the GUI design related decisions of acs-viewer in
  its own maven module folder as "gui-decisions.md" and all confirmed. *(2026-10-22)*

## Names
- **4.** Verbatim: [The Java package and the two classes, picked among Claude's options:] (Q1: a) [package
  `org.halim.acsessgui`, `ACSESS_GUI_Main` and `ACSESS_GUI_Service`]. *(2026-10-23)*
  Meaning: replaces acsess-gui 1, which loses the package org.halim.acsview.
  Conflicts: (acsess-gui 1) the package org.halim.acsview.

## Views
- **5.** Verbatim: The core view doesn't show a tree. The "arities view" lists the arities. We need a separate
  "arity table view" for viewing the details of the arities. The structure view is like you guessed [its
  class, description and spanned arities], but it also shows the connections it creates, in a plane where
  zooming and movement allowed. The structure view will show a structure, not the structures. But we can
  make a separate "structures view" for what you said [the structures and the arities they span, on a
  plane]. [The core view: its class, its counts, and its requests as actions:] Yes, your suggestions align
  with my intentions perfectly. [The navigator goes:] Yes. The views show contents of what their names
  implies, simple. *(2026-10-23)*
  Complements: (acsess-gui 2) the views core, journal, classes, arities, structures, procedures, content.
- **6.** Verbatim: The classes are the loaded java class files, each has an identity int which can match data
  showed in other views (like arities view or the structures view). This means it would been cool if we
  add a code view that uses a java decompiler, tell me if feasible. Go with your suggestions [the
  decompiler Vineflower]. For "Class identities: ..." go with a [the number the current store's
  indexer.bin gives, matched by the class's name]. *(2026-10-23)*
  Complements: (acsess-gui 2) the classes view.
- **10.** Verbatim: Each view has a single goal only. The goals are to descend down, like a view displays the
  core data, another view lists the structure and [is] responsible of selection of structures only,
  another view display[s] the contents of the structure. *(2026-10-24)*
  Meaning: a view chooses only what it lists, the arities view an arity, the structures view a
  structure; a click elsewhere only focuses.
  Complements: (acsess-gui 2) the views; the unreviewed Views entry of 2026-10-23.
- **11.** Verbatim: Views must refresh each other when necessary. *(2026-10-24)*
  Meaning: a view reads again when another view's action changes what it shows, a store included,
  since a store numbers the classes anew.
  Complements: (root 72) keeping the queried data in memory.
- **12.** Verbatim: We do not need all of the views to be on the left, since most of them will be type of
  content views. Instead we need to make a new content view button along side few fundamental views of
  "core", "journal", "listers" (can become any type of lister like arities or structures), "content"
  (shows content of a selected list). [Content is that button:] yes, lister and content for listing and
  viewing, also a [the console stays]. [What a lister lists:] c [arities, structures, classes,
  procedures], ii [any number open]. [A lister's click:] a [it focuses; a click on the focused element
  gives it to the content panels]. *(2026-10-24)*
  Conflicts: the unreviewed Views entry of 2026-10-23: its views leave the sidebar.

## Layout
- **7.** Verbatim: This model has a lot of view types, and users will likely have many views running on the same
  time, we need to design a better gui than just panels (as columns) on a single row. For "Layout ..." the
  (b) [docking of our own] is not an option because the swing keeps breaking. We can go with (c) [rows of
  columns] if we can trust swing, if not and the ModernDocking is trustable go with it. *(2026-10-23)*
  Meaning: rows of columns, Swing trusted for them since a row is the mechanism that already worked; no
  docking library.

## Content panels
- **8.** Verbatim: (Q4: The content page will have a selection at the top for "arity" [lists the row types and
  identity count], "structure", "procedure", "arity-row", "arity-identity". The content will take the
  picked [by clicking while focused, for not conflicting with the drag-and-drop ability] instance
  of it's type. Or an unfocused element can be dragged and dropped into it. There may be any number of
  content panels.) Clicking an already focused element makes it appear in the listening content view
  panel automatically, clicking focuses but doesn't change the content view. So user can simply drag and
  drop a single element to the content view panels without changing other content view panels. Only one
  type of panel listens only one type of element, that is the rule, and easy to enforce. Example: A
  content view panel that chose arity only listens for arities, and only that content panel listens for
  them, do not allow a second one. If user tries to create a second one simply switch the states.
  *(2026-10-23)*
  Meaning: a new panel opens with no type; choosing a type another panel holds swaps the two panels' type
  and element; a drop goes only to a panel of the element's type; any number open, at most five holding
  a type.
  Commons: (acsess-gui 2) content pages may be many and can focus on different things.
- **13.** Verbatim: Forget the former readings, most recent is the source, the arity-row is moved into the arity
  content, more on that later. [The structures plane:] it is a content panel, bound to core. [Asked
  whether it takes no element and replaces the structures view:] no, it meant to be content panel, name
  it "structures". There will be three views related to arities: Arity List, Arity Summary, Arity
  Content. Pick the Arity from the Arity list, then the summary is shown on the Arity Summary and you can
  edit values from the arity content view, which is two options of the content view, the "arity row" and
  "arity element". [A structure:] two separate goals, forget to tell, what you said is "structure
  summary" vs "structure content". [Capturing and deleting identities, in the Core view or an arity
  row:] no, the arity summary view must do it. [An] element can be picked by typing its number, for row
  it is (a) [the arity summary lists the rows, each clicked and dragged]. *(2026-10-24)*
  Meaning: the content kinds are arity summary, arity row, arity element, structures, structure summary,
  structure content, procedure, class; one panel a kind.
  Conflicts: the unreviewed Content panels entry of 2026-10-23: its five types.

## Help
- **9.** Verbatim: One important detail tho, I also want you to later write the descriptions for the
  instruction manual and the daily tips. [When:] a [a later session]; [Where:] a [plain-text resources
  in acsess-gui/src/main/resources/, a description per view and a list of tips]; [How:] a [a "Manual"
  view in the sidebar, and a tip dialog at start that can be turned off]. *(2026-10-23)*
  Complements: (acsess-gui 2) the views the manual describes.

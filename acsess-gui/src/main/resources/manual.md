//# ==============================================================================
//# ACSESS GUI - INSTRUCTION MANUAL
//# ==============================================================================
//# DEV NOTE: This file is parsed by ManualParser.java.
//# Lines starting with '//' are skipped by the parser but remain here.
//# An entry is a '# ' title, then two blocks fenced by four backticks: the summary, always shown,
//# and the details, shown when the entry is expanded. Both are HTML.
//# ==============================================================================

# Introduction to ACSESS GUI

````html
ACSESS GUI shows and edits the databases that <b>acsed</b> holds. It asks <b>acsess</b>, the same program a terminal uses, for everything it shows, so it can do nothing a terminal's request could not.
````

````html
A database is made of <b>arities</b> (groups of identities, each with rows of values), <b>structures</b> (relations between arities), and <b>procedures</b> (waiting for their requests). The GUI has one view for each kind of question you may ask of it, and you open as many of them as you like.<br><br>
<b>The views:</b>
<ul>
    <li><b>Welcome page</b> &mdash; connect, create, load and open databases.</li>
    <li><b>Kernel</b> &mdash; the database's own concepts: its core, its journal, its acs-city, and its request console.</li>
    <li><b>Lister</b> &mdash; lists the arities, the structures, the classes or the procedures.</li>
    <li><b>Content</b> &mdash; shows one element in depth, in one of seven kinds.</li>
</ul>
Each view has a single goal. A view never changes what another view shows: you pick an element in a lister, and hand it to a content panel yourself.
````

# 1. Getting Started: Connecting and Opening a Database

````html
The Welcome page shows how acsed stands and lists the databases it holds. A database must be loaded into acsed before any view can show it.
````

````html
<b>Boot acsed</b> asks acsess to boot acsed; the GUI does this once by itself at start. The line under the title tells how acsed answered.<br><br>
<b>+ Create Database...</b> asks for an absent or empty folder, and creates a database in it. <b>Load Database...</b> asks for the folder of an existing database and loads it into acsed. <b>Refresh</b> reads acsed's list again, since a terminal may load or unload a database at any time.<br><br>
<b>Double-click</b> a database in the list to show it: every view then reads that database. Only one database is shown at a time; the others stay loaded, and the sidebar lists them all.<br><br>
<i>Safety net:</i> acsed is left running when the window closes, as acsess leaves it. Use <b>Store</b> on the database before leaving, so what you changed reaches its files.
````

# 2. The Sidebar

````html
The left sidebar holds the anchors and the loaded databases at the top, and the buttons that open views at the bottom. The top arrow collapses it to its icons.
````

````html
<b>Anchors:</b> <b>Toggle Sidebar</b> collapses or expands it; <b>Welcome Page</b> returns to the Welcome page; <b>Instruction Manual</b> opens this manual.<br><br>
<b>Databases:</b> one tab for each database acsed holds. Click a tab to show that database; hover it to see its folder. Under each tab, <b>Store</b> writes the database to its files, and <b>Close</b> stores and unloads it from acsed, after asking.<br><br>
<b>Views:</b> <b>Kernel</b>, <b>Lister</b> and <b>Content</b> each open a <i>new</i> panel on every press, since any number may be open; they are closed by the <b>x</b> in their own header.
````

# 3. The Workspace: Rows, Panels and Dividers

````html
The workspace holds the views in rows of columns. A row's panels share its width equally, and the rows share the height until you drag the dividers between them.
````

````html
Each panel has a header with its title, and its own buttons at the left: the chooser that picks what it shows, and, in a lister and in a content panel, the transpose button. At the right are the arrows that move it:
<ul>
    <li><b>Left / right:</b> within its row.</li>
    <li><b>Up / down:</b> to the end of the row above or below. Moving down from the last row makes a new row, and a row left empty goes away.</li>
    <li><b>x:</b> closes it, for the panels the sidebar does not toggle.</li>
</ul>
<b>Drag a divider</b> between two rows to give one more height. Moving or closing a panel rebuilds the rows, and the dividers return to equal heights.<br><br>
When a panel shows something new, its header <b>flashes a soft yellow</b> light that fades: the quickest way to see which views a change reached.<br><br>
<b>Ctrl+Z</b> undoes, and <b>Ctrl+Y</b> redoes, the last activity of the shown database, wherever the focus is.
````

# 4. Focus, Click and Drag: How the Views Talk

````html
A single click on an element only <i>focuses</i> it and changes no panel. A second click on the focused element gives it to the content panels that take its type. You can also drag an element onto one panel alone.
````

````html
An <b>element</b> is a line of a lister or of a content panel, or a node of the acs-city: an arity, a structure, a row of an arity, an identity of one, or a stored class.<br><br>
The content panels show the focused element under their chooser, in a line that begins <b>focused:</b>, and say when a second click will show it there. A panel that takes an <b>arity</b> and one that takes a <b>row</b> never fight: each listens for its own kind of element, and only one panel holds each kind.<br><br>
<b>Drag and drop</b> hands an element to exactly the panel you drop it on, and no other panel changes: this is how you keep two arities open side by side.
````

# 5. The Kernel View

````html
The Kernel view is about the database's own kernel concepts. Its header button chooses one of four types: <b>Core</b>, <b>Journal</b>, <b>ACS-City</b> or <b>Console</b>. Any number of Kernel panels may be open, each on a type of its own.
````

````html
<b>Core</b> shows the database's core class and how many arities and structures it holds (removed slots left out). Its buttons ask the core for a change; each change is one activity in the journal, undone as one:
<ul>
    <li><b>Add Arity...</b> asks for the new arity's description.</li>
    <li><b>Add Row...</b> asks for the arity (the focused one is offered), the row's class, and its description.</li>
    <li><b>Remove Arity...</b> and <b>Remove Structure...</b> ask for the slot (the focused element's is offered), and confirm.</li>
</ul>
<b>Journal</b> shows where the journal stands: the current activity index, and how many undone activities redo holds. <b>Undo</b> and <b>Redo</b> move one activity; <b>Undo To...</b> and <b>Redo To...</b> move until the activity index you type; <b>Store</b> writes the database to its files, which gives the classes new numbers, so every view reads again.<br><br>
<b>ACS-City</b> draws every arity and every structure on one plane, with a line from a structure to each arity it spans. <b>Drag the empty plane</b> to move it and use the <b>mouse wheel</b> to zoom around the pointer. A node is an element: click it to focus it, click it again to give it to a content panel, or drag it onto one.<br><br>
<b>Console</b> types acsess requests as a terminal takes them; the next entry describes it.
````

# 6. The Lister View

````html
A lister lists one kind of thing of the shown database, a line each. Its header button chooses what it lists; any number may be open, each listing what it chose.
````

````html
<b>Arity List</b> &mdash; each arity's slot, description, class, the number the current store gives that class, its identities and its rows.<br>
<b>Structure List</b> &mdash; each structure's slot, description, class and number, and the arities it spans.<br>
<b>Class List</b> &mdash; the classes the store carries: their number, name, the slots they were stored in, and what they are now. The numbered ones come first, then the other class files by name.<br>
<b>Procedure List</b> &mdash; waits for its requests: acsess has none on procedures yet.<br><br>
A removed slot keeps its line, and is no element. The first column is the line's number, with no name above it, kept narrow. The <b>transpose</b> button turns the lines into columns and back, which suits a few lines with many fields. <b>Drag a column's edge</b> to size it; a refresh keeps what you set.
````

# 7. The Content View

````html
A content panel shows one element in depth. Its header button chooses the <b>kind</b>, and the kind decides which element it takes. Only one panel holds each kind, and choosing a kind another panel holds swaps the two panels' states.
````

````html
<b>Arity Summary</b> &mdash; takes an arity: its class, description, identities and rows, then a line for each row (row, description, class, type). <b>+ New Identity</b> captures an identity; <b>Delete Identity...</b> removes the one you type (the focused identity of that arity is offered) after confirming. Each is one journal activity.<br><br>
<b>Arity Row</b> &mdash; takes a row: the row's values over the identities, 100 to a page, with <b>&lt;</b> and <b>&gt;</b> to turn the pages. <b>Double-click a value</b> to edit it; each edit is one set request, so one journal activity. Hover a value to see which identity it belongs to.<br><br>
<b>Arity Element</b> &mdash; takes an arity, to choose it, and shows that arity's first identity: that identity's value in every row (row, description, class, type, value). Step with <b>&lt;</b> and <b>&gt;</b> to the previous or next identity that exists, or type an identity number and press Enter. Values are edited in place, too. A value shown as <code>(object)</code> holds an object, and is not edited.<br><br>
<b>Structure Summary</b> &mdash; takes a structure: its class, its description, and each arity it spans, which are elements of their own.<br><br>
<b>Structure Content</b> &mdash; takes a structure: the structure with the arities it spans, on a plane that moves and zooms as the acs-city does. The connections it creates wait for a request that reads them.<br><br>
<b>Procedure</b> &mdash; waits for the requests acsess does not have yet.<br><br>
<b>Class</b> &mdash; takes a class: the stored class, decompiled, with Java syntax colors and line numbers.<br><br>
The first column of the arity summary and the arity element is the row's number, with no name above it. The <b>transpose</b> button turns the lines into columns and back; an editable value stays editable when transposed.
````

# 8. The Console Type of the Kernel View

````html
The Console is the Kernel view's fourth type, a place to type acsess requests as a terminal takes them, one request a line, and to run them as one connection.
````

````html
Write the requests in the upper area and press <b>Run</b> or <b>Ctrl+Enter</b>. Each answer is printed under its request in the lower area; a refused request is printed with the word <i>refused</i>. A blank line is skipped, and a quote may not span lines, since each line is one request.<br><br>
<b>Run past a refusal</b> keeps going after a refused request; without it, the lines after a refusal are printed as <i>not run</i>. <b>Insert Address</b> writes the shown database's name and a colon at the cursor, as a request to a database starts. <b>Clear Output</b> empties the lower area.<br><br>
A request may change anything, so after a run every view reads again, the sidebar's list and the Welcome page's list included.
````

# 9. Undo, Redo and Storing

````html
Every change made through the GUI is one activity in the shown database's journal. You undo and redo activities, and you <b>Store</b> to write the database to its files.
````

````html
<b>Ctrl+Z</b> and <b>Ctrl+Y</b> (or the Kernel's Journal buttons) undo and redo one activity. <b>Undo To...</b> and <b>Redo To...</b> go to an activity index, one activity at a time on the way.<br><br>
<b>Store</b>, in the Kernel's Journal or under a database's tab, writes the current state to a new generation of the database's files. The classes are numbered anew by a store, so every view reads again afterwards. <b>Close</b> in the sidebar stores and then unloads the database from acsed.
````

# root: the project

- ACS is meant to be a database managed with commands: the client starts the engine and must use the
  requester, as `acses boot`, `acses loadDB ~/main --calledBy main`, `acses components main` (1). Then a
  GUI on the terminal input, which can later be isolated from it, and an AI agent using acses (2).
- It is a personal project we can document for reputation (3), good for RAG as the most expressive by
  itself (6); the vision is a database of nearly 512GB of text built with agents and ACSES (7).
- A paragraph of knowledge: a first agent builds a personal knowledge graph from books, an auditing
  agent audits and improves it, a third builds a new acs of paragraphs, better and copyright free;
  paragraphs must cite their resources and reasoning, and we can talk about it later (4). Agents must be
  allowed at every step, to automate it, and humans at every step, to study it (5).
- ACS-alHikmet is not the house of wisdom but its ACS: it doesn't contain the 512GB, but the methodology
  of building it (8); its agents use the terminal to build the RAG from many resources, not the
  author's job, given to a few brilliant newbies (70).
- The author believes any system can be data about objects, structures on them and procedures about
  them: an arity holds a group's data; the identities set matches identity to memory and does set
  operations; structures hold relations and know nothing of data, else that would be another arity, a
  higher order; many structures may do one thing, optimized differently; procedures change both (9). The
  author likely always meant any database-like system as a few types of beings and structures across
  them (10).
- A structure must know only a mostly contiguous set of identities, one per record; data sits in
  arities; the identities set maps identity to memory, so removal shifts nothing (11). Records can be
  about records, a huge part of all the structures (12).
- Later, procedures would be cool, for feed forward and backpropagation (13): a neural network is two
  arities, neurons and synapses, and a procedure that sets every non-input neuron to its bias, adds the
  weighted inputs, and once all are in can apply the activation (14). The author thinks it can represent
  anything, with expressiveness to reason about mathematically, thanks to the contracts (15); it is
  FCA4J as a database and multi-arity (16).
- Four layers: library, database application, requester, GUI or agent (17); the service and the
  requester as an adaptor that the GUI and agents use (18); the requester name and requesting through a
  file were killed (19). The author guesses a parent project compiles all the others (20).
- The six submodules: ACS, ACSE, ACSES, OntoDirectory, ACS-alHikmet, ACSAH viewer (21). ACS holds the
  contract interfaces; ACSE a few good implementations; ACSES a client side for ACS based databases;
  ACS-alHikmet agents building and auditing a database of paragraphs; ACSAH viewer a GUI analyzing its
  product; OntoDirectory, where it all started, to be integrated with ACSE, and later it can be run by
  agents like ACS-alHikmet (22). ACSAH-viewer is the GUI for viewing a RAG, the last project, waiting
  for ACS-alHikmet (71). OntoDirectory is right now only a Set Hierarchy method for sorting files,
  instead of folders; later we can integrate more of the ACS (73).
- ACS-view is the acsess GUI, a simple GUI for ACSES that allows editing and keeps the queried data in
  memory (72): in its own Maven module, `acsess-gui`, a database tool like SQL viewing tools that also
  edits, with OntoDirectory's GUI cloned in for the structure, not the views (68). As its own module,
  it would be a seventh beside the six (21, 68).
- ACS-view organizes ACS based databases, and OntoDirectory is a special version organizing files with
  ACS; both are GUI applications (69), replacing "the GUI is OntoDirectory, needing improvements" (23).
  ACS-view runs acsess like a terminal, with beginSession, one refresh being one program run and one
  connection (30); it shows and edits (31).
- Right now there is no access control or security, not in the MVP (24).
- `acse` is the library, not usable on its own: no working database comes from it alone, as `acses` runs
  it, but a Java program can use it directly (25, 26). `acse` replaced `acs` and `acses` replaced
  `acse`, like "access" (27): ACS is the system, ACSE implements it, ACSES uses it for the database
  service (28). ACSES passes requests to the core; those commands are planned in another session (65).
- A request is described by a line in a small string grammar, written by reflection from the methods
  the implementation marks, so no name is a literal; acsed gathers a database's lines into
  `acse-language.txt` at its root, which acsess reads (66). acsed's own commands are not journaled and
  do not go through `ACS_Actor` (67).
- During an inspection, the rollback `inspectAt`, the journal refuses a write loudly; a command waiting
  for the return answers with the current activity index and the count of activities after it (64).
- ASP would name the system but is likely trademarked, ASPCC its full name; we go with acs, `OE_`
  becoming `ACS_` (29). The package is acse; ACS is the idea, the engine the database side; the
  interfaces in `acs` start with `ACS_`, the core, journal, persistence manager and description manager
  included (63), no longer "except the core, journal and persistence manager" (32).
- The licence would be like a bomb, forcing every one responsible to open source (33); commercial use
  must be commercial (34): AGPL-3.0-only with a commercial licence, if skipping a well-known one costs
  near nothing (35), plus Oracle's Universal FOSS Exception (36); `LICENSE` holds the AGPL, `NOTICE` the
  rest (37).
- We code the classes to design, and later add the boilerplate (38). Decisions were clustered by topic
  (39), the inferred ones beside the rest (40). Urgency is not by time but by being a requirement to
  other processes; decisions can make a thing obsolete (41).
- No more "Module": "Component", and "Composable" for "Modular", defined clearly (42); composable is the
  vocabulary, plugins were left since the core is too thin (43). Right now the core is almost redundant,
  arities and structures can work on their own, but it stays; the ACS doesn't have a core, ACSE has
  (44).
- The former version lacked what the application needs: modularity with logging and undo-redo (45). The
  `acs_beta` plan: a file for every top-tier interface, packages for implementations, named identifiers,
  arity, structures by arity (46).
- The client, acses, gives the comment and can only request; steps are the application's (47).
- The author thinks the database will not change once done, as no library is planned soon, and guesses
  bumping the FORMAT can handle class updates, since supporting all former versions is hard (48).
  Roaring is a dependency (49).
- A loading acses uses only the database's classes: it knows only the contracts, and the database is the
  authority on implementation (50). We can make a persistence manager contract, its class in the root
  package (51). Creating a database: acses copies `acse-persistence.jar`, loads the base classes, makes
  an empty core, saves. Loading: the jar's persistence manager finds the current folder and builds the
  core, which loads all its instances; the name is `--calledBy`'s, or the folder's if the request didn't
  give one (52).
- Words: ontology in the philosophical sense, not OWL (53); an arity, one identity space (54); a
  catalog, one value per identity, an unwritten slot zero, not absent (55); a structure, data about
  relations (56); a component, what the core holds (57). An identity has its iui, the count of lesser
  identities, and its imv, its stable value, "iui <= imv"; "identity" and "index" are no longer used,
  being not distinctive; compacten makes all iui equal their imv (58). Compacten removes unneeded space
  without changing the data's structure (59).
- The target use: iui dense and imv local to an arity (60); edits may be slow, reads may not (61); the
  engine must be able to express anything (62).
- `acs/CLAUDE.md`: the meaning of the interfaces: what arity, structure, procedure, core, journal and
  descriptions are.
- `acse/CLAUDE.md`: the implementations: the journal's and the persistence manager's choices, and the
  identity sets.
- `acses/CLAUDE.md`: the terminal application: the service, boot, and creating and loading databases.
- `acsess-gui/CLAUDE.md`: ACS-view, the GUI for ACSES: its package and views.
- `acs-alhikmet/CLAUDE.md`: ACS-alHikmet: the rows storing a source file and its page number.
- `deprecatedCode/org/halim/ontology/CLAUDE.md`: the former engine, read only.
- In `markdowns/`: `agents-citable.md` and `agents-unreviewed.md`, this folder's entries;
  `new-architecture.md`, the author's words on the three-file method; `plan.md`, the acsess-gui round's
  plan; `current-state.md`, `questions.md`, `terms.md`, `markdown-refining.md`, `novelty-study.md` and
  `naming-fix.md`.

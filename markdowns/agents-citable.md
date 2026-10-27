# root: citable

## Goal

- **1.** ACS is meant to be a database that can be managed with commands. Specifically, the client is meant
  to start the "ACS engine", then must use the "ACS requester" as: `acses boot` (starts the acs
  application), `acses loadDB ~/main --calledBy main` (loads the database in the path main and allows
  access to it with the name of main), `acses components main` (shows the components of the
  database). [The names changed afterwards: see Layers and names.] *(The author wrote "acsr"; renamed
  to "acses" at the author's request. The author wrote "as main"; changed to "--calledBy main" at the
  author's request, 2026-10-15, to match Persistence.)*
- **2.** Then we will develop a GUI for that application that uses the terminal input (later we can isolate
  it from the terminal); also we will build an AI Agent that will use acses. That is the goal.
  *(The author wrote "acsr"; renamed to "acses" at the author's request.)*
- **3.** [Who is it for?] Since this is a good personal project, we can document it for gaining reputation.
- **4.** [What is a "paragraph of knowledge"?] It is more complicated [than a passage from one book, or a
  paragraph written from several books]. The first agent will build the personal knowledge graph from
  the books, then an auditing agent will audit it and improve, then a third agent will build a new
  acs of paragraphs from that first acs, which better describes and [is] copyright free. Also,
  paragraphs must cite the resources and reasoning to be accurate. We can talk about it later.
  *(The author wrote "gap" and confirmed "graph".)*
- **5.** [Who builds the structure, and who audits it?] Must allow agents at every step, since I want to
  automate it. Must allow humans in every step, since I want to study the procedure of developing
  proper knowledge graphs.
- **6.** This database architecture is good specifically for RAG, since it is the most expressive by itself.
- **7.** I have a vision of building a huge data base of nearly 512GB text using agents and ACSES.
  *(2026-10-18)*
- **8.** [ACS-alHikmet:] it is not the house of wisdom but ACS of it, also it actually doesn't contain the
  entire 512GB text data base but the methodology of building it. *(The author wrote "ACS al-Hitmek".)*
  *(2026-10-18)*

## Vision

- **9.** I believe we can represent any system as data about the objects, the structures on them, and
  procedures about them. ArityComponent is responsible for the data of a group of objects. Primary
  goal of the Identities set is to match an identity to memory, while being also responsible for set
  operations. The structures are responsible for relations between identities and have nothing to
  know about data or record data; if [they did], that knowledge would be another arity and the order
  of the structure would [be] higher. There may be many structures that do the same thing but
  optimized in another way. The processes [procedures] are behaviors to change data or structure;
  they depend on some of the data and structure, and when run they change some of the data and some
  of the structure. *(The author wrote "memory" and replaced it with "record data", 2026-10-15. The
  author restated the identities set's part, replacing "only responsible [for] matching", 2026-10-18.)*
- **10.** My vision likely always was that we can express any database-like system (where data exists and
  filtering [is] done based on rules) as a few types of beings and structures across them.
- **11.** The structure must know only a mostly contiguous set of identities each representing a single
  record, while the data about the identities are handled on tables named Arity. Since moving [the]
  rest of the records one block left each time we delete a record is unnecessary, the identities set
  handles identity<->memory mapping, where identity is a single record and memory is the address of
  memory where data related to it [is] stored. *(The author wrote "dense", meaning "mostly full with
  few gaps", 2026-10-15.)*
- **12.** Some of the records actually can be about other records; those relations are a huge part of all
  the structures.
- **13.** Later I realized the addition of procedures would be cool and allow it to express more things,
  like feed forward and gradient descent backpropagation.
- **14.** [The NN example:] Two arities, one for (nodes) neurons and the other for the synapses
  (connections/weights). A procedure that firstly clears every non input neuron's value by setting
  them to the bias (an identity from synapses that has two of the same identity from the neurons
  arity), then for each of the identities on the synapses arity multiplies the first neuron's value
  with the weight and adds it to the second neuron it points [to]; also when all the inputs of a
  neuron [are] calculated, its activation function can be calculated and replace the value on the
  neuron. There are other ways to describe a NN with this architecture; few of them are a bit better
  and more descriptive.
- **15.** This architecture can represent easily [a] database or a neural network, which makes me think this
  can represent anything; more importantly [it] does it with high expressiveness (because of the
  contracts), so we can mathematically reason about them.
- **16.** My architecture is FCA4J but as [a] database and multi-arity. *(2026-10-10)*

## Layers and names

*(Names as spoken at the time. The entry "For the naming, acse replaces the acs" sets the current
ones: the library is `acse`, the service program is `acses`; since 2026-10-14, ACS names the system,
`acse` implements it and `acses` uses it for the database service. Before that entry, `asca`, `ASCA` and the service `acse` mean `acses`; `ACS` and `ASC`
mean `acse`; `acsr`, `asrc` and `ascar` are the requester.)*

- **17.** There are four layers: 1 library, 2 database application, 3 requester, 4 GUI or 4 Agent.
- **18.** The archiver (hereby "asca") is a database service, [the] requester (hereby "ascar") is an
  adaptor, [and] the GUI and agents will use the adaptor.
- **19.** We killed the ascr [the requester] name and [the] requesting through a file idea. *(2026-10-14)*
- **20.** I guess the new plan is to make a parent project that will compile all other projects in order to
  develop our applications.
- **21.** [The project ACS has] six submodules: ACS [the contracts module], ACSE, ACSES, OntoDirectory,
  ACS-alHikmet, ACSAH viewer (a new project, which is like OntoDirectory but viewing the results of
  ACS-alHikmet) [: the other GUI application that focuses on the RAG structure I planned].
  *(2026-10-18)*
- **22.** My better wording for the inferences: The ACS Project: *(2026-10-18)*
  - Vision of the ACS related projects and the goal, also all the Important ACS projects.
    ACS: The contract interfaces, since they elegantly formalize thing into modules.
    ACSE: Few good implementations of the ACS contracts for solidifying the project.
    ACSES: A client side ACSE implementation for managing ACS based databases. ACSE contains few default implementations.
    ACS-alHikmet: Agents for building and auditing an ACSES database of paragraphs.
    ACSAH viewer: A GUI application like OntoDirectory to analyze the product of ACS-alHikmet.
    OntoDirectory: Where it all started, aimed to be integrated with ACSE instead of ontology package int it's source code, later can be implemented with AI agents like ACS-alHikmet.
- **23.** [Is the GUI OntoDirectory?] Yes, but needs improvements. [replaced by: root 69]
- **24.** Right now we do not make access control or cyber security, out of the scope for MVP. *(2026-10-14)*
- **25.** ACS [`acse`] is the library, it will not be usable on its own.
- **26.** "It will not be usable on its own" means no working database comes from `acse` alone, since `acses`
  runs it, but a Java program can still use `acse` directly. *(Author: "(q1 a)".)* *(Claude's
  reading, confirmed by the author on 2026-10-18: "I read and confirm it.")*
- **27.** For the naming, acse replaces the acs, and acses replaces the acse. Now it sounds like access, which
  is cool.
- **28.** The ACS is a system, as it names; ACSE is its implementation, and ACSES uses it for [the] database
  service. *(2026-10-14)*
- **29.** [On renaming to ACC, Arity Component Composer:] In terms of ECS's naming, the appropriate name for
  the system would be ASP (Arity-Structure-Procedure), which is likely trademarked. ASPCC would be
  the full name of the system since it describes exactly what it does. We better go with acs, also
  do it as "OE_ -> ACS_". *(2026-10-14)*
- **30.** [How ACS-view sends its requests to acsed, picked among Claude's options:] Run acsess: runs the
  acsess program, like a terminal, using beginSession so one refresh is one program run and one
  connection. *(2026-10-22)*
- **31.** [Whether the first version changes a database or only shows it, picked among Claude's options:]
  Show and edit. *(2026-10-22)*
- **68.** ACS-view is the acsess GUI, in a separate Maven module, `acsess-gui`: a database tool like SQL viewing
  tools that also edits, OntoDirectory's GUI cloned into it for the structure, not the views, and
  running acsess; its GUI design decisions are in `acsess-gui/agents-citable.md` and root 30, root 31. *(Author, 2026-10-21:
  "For context, we later will also make a similar application called ACS-view, that is simply the
  acsess GUI."; 2026-10-22: "I want you to clone the onto directory GUI to a new maven module of named
  acs-view, and make it prompt the acsess to work and visualize stuff."; [what the clone keeps:] "Not
  the views, but the structure of the GUI."; "I need you to build a separate maven module for the goal
  I specified. Do not make in place edits on other projects. The ACS viewer is a data base tool just
  like SQL viewing tools that also allow editing."; "These two is about the project, keep it at the
  root"; "Also move the SQL-tool sentence.")* Commons: "Then we will develop a GUI for that application
  that uses the terminal input" (root 2) and "the GUI and agents will use the adaptor"
  (root 18), acsess being both. Conflicts: "[The project ACS has] six
  submodules" (root 21) would lose its count, ACS-view being a seventh; asked.
- **69.** Verbatim: [ACSESS-GUI and OntoDirectory:] The ACS-view organizes ACS based databases; OntoDirectory is a
  special version that organizes files with ACS. [In replacing entry 23 "[Is the GUI OntoDirectory?]
  Yes, but needs improvements.":] they both are GUI applications. *(2026-10-22)*
- **70.** Verbatim: ACS-alHikmet: The agents that use the terminal to build the RAG from many resources; not my
  job, I gave it to a few brilliant newbies. *(2026-10-22)*
- **71.** Verbatim: ACSAH-viewer: The GUI for viewing a RAG; the last project, and it waits for the
  ACS-alHikmet. *(2026-10-22)*
- **72.** Verbatim: ACS-view: A simple GUI for ACSES; it allows editing and keeps the queried data in memory.
  *(2026-10-22)*
- **73.** Verbatim: OntoDirectory: Right now only a Set Hierarchy (instead of folders) method for sorting files;
  later we can integrate more of the ACS. *(2026-10-22)*
- **74.** Verbatim: I want you to plan a session that will complete the acs-view, but prior to that rename it to
  acsess-gui, since it is what it is. [Where it builds, picked among Claude's options:] (Q2: a) [it joins
  the root pom's modules]. *(2026-10-23)*
  Commons: (root 68) already names the module `acsess-gui`; (root 20) a parent project compiling all the
  others.
  Complements: (root 21) six submodules; acsess-gui becomes a seventh in the root pom (root 68).
- **76.** Verbatim: "Important detail is ACS only a mathematical entity, implementation related optimization is ACSE.
  Act accordingly."; asked whether those in acs move to acse: "Yes, they stay." *(2026-10-24)*
  Meaning: ACS is mathematical and the optimizations are ACSE's; the ones in acs stay there.
  Commons: "The ACS is a system, as it names; ACSE is its implementation" (root 28).
  Conflicts, in acs today: `LongChunkInt`, the range reads `copy…Into`, `getEstimatedBytesPerIdentity`,
  `ensureCapacity` and `truncate`, `getAsRoaringBitset`, and the storage forms the sets' and catalogs'
  package-infos describe; the iui-run method "because this is faster" (`acs/arity/sets`, unreviewed).

### Details

*(Old names: see the note under Layers and names.)*

- **32.** [The prefixes:] [The] package becomes acse but the interfaces start with ACS, except the core,
  journal and most importantly the persistence manager. ACS is the idea, engine is the database
  side. *(2026-10-14)*
- **63.** The interfaces in `acs` start with `ACS_`, the core, journal, persistence manager and description
  manager included. *(Author, 2026-10-18: "Hereby interfaces in the acs start by ACS_ instead.")*
  Conflicts: root 32 [The prefixes:] (2026-10-14) would lose "except the core,
  journal and most importantly the persistence manager". Commons: "do it as "OE_ -> ACS_""
  (root 29). Complements: "ACS: The contract interfaces" (root 22). acse 10's
  "make an "ACSE_PersistenceManager" class" keeps the name it had.
- **65.** ACSES passes requests to the core; those commands are planned in another session. *(Author,
  2026-10-19: "as the goal it gives commands to the Core with the request comment and returns what
  Core returns."; "I am planning them in another session.")* Commons: "Later, in `acses`: commands
  that interact with the arities" (`acses`, unreviewed).
- **66.** A request is described by a line in a small string grammar, written by reflection from the methods the
  implementation marks, so no name is a literal; acsed gathers a database's lines into `acse-language.txt`
  at its root, which acsess reads. *(Author, 2026-10-20: "Go with (b), the string grammar"; "Only
  problem with b is, we need to automatically read the method names from the code instead of string
  literals."; "The implementation meant to define the requestables. I suggest a
  "getRequestables(DataOutputStream dos)" that describes the available request options."; "The file
  that describes commands can be in the database root named as "acse-language.*" or alike.")*

## Licence

- **33.** I would like a licence that is like a bomb and forces every one responsible to open source their
  code.
- **34.** I want people to be allowed to develop open source using this, but it must be commercial for the
  commercial use.
- **35.** [What is the cost of not using a well-known licence?] If it is near nothing, go with AGPL-3.0-only
  with a commercial licence. *(Claude's comparison and the result: `inferred-decisions.md` → The
  licence, in git at `6f32a25`.)*
- **36.** [AGPL-3.0-only with the Universal FOSS Exception, plus a commercial licence:] Good, I want to use
  the first suggestion of you.
- **37.** The exception is Oracle's Universal FOSS Exception, Version 1.0, referred to by name and by Oracle's
  link, which Oracle allows when referring to it, rather than an equivalent of our own: `LICENSE`
  holds the AGPL text alone, `NOTICE` the rest. *(Claude's wording; the author: "Do what you want,
  likely you know the legal side better than me.")* *(2026-10-13)*

## Process

- **38.** This is the plan: we code the classes to design; later we will add the boilerplate, like bulk
  access to columns in many types (int, long, double).
- **39.** [How to organize `decisions.md`: general entries stay in `decisions.md`, which is always read;
  niche ones go to `decisions/<topic>.md`, read when working on that topic:] I prefer that for
  clustering. *(2026-10-15)* [Since then: one Citable file, see `CLAUDE.md` → Decisions.]
- **40.** I want the inferred decisions and [the] rest of the decisions to stay under the same path
  [`markdowns/decisions/`]. *(2026-10-18)*
- **41.** For this project, urgent things are not urgent by time; they are urgent by being the requirements
  to other processes. They can become obsolete by decisions.

## Architecture

- **42.** No more "Module", we already settled to "Component", so "Modular" becomes "Composable"; just be
  clear what composable means. *(2026-10-13)*
- **43.** Composable is the system's vocabulary; plugin based is an architecture we left because [the] core
  is too thin for calling those components as plugin[s]. *(2026-10-15)*
- **44.** Right now the core is almost redundant since arities and structures can work on their own, but for
  the future purposes core stays; it also makes the code more organized. The ACS doesn't have a core,
  the ACSE has the core. *(2026-10-15)*
- **45.** The former version's missing features were critical for the application. I need the modularity and
  "Logging, Undo-Redo, ..." at the same time.

### Details

- **46.** [Under `acs_beta`,] I plan on making a separate file for every top tier interface, ArityComponent
  variants and StructureComponent variants, and identitySet [`IdentitiesSet`]. Then packages for
  implementations. I prefer packages of "identifiers", "arity", "structures.<x>ary".

## Journal and undo
- **64.** During an inspection (the rollback `inspectAt`) the journal refuses a write loudly; a command
  waiting for the return answers with the current activity index and the count of activities after
  it. *(Author, 2026-10-19: "Refuse write loudly, and add a command to "wait" for returning to the
  former version, the command returns the current activity index and how many activities are
  pending."; pending picked among Claude's options: "Activities after the current point".)*
  Complements: "break undo history" (acs 41), which an inspection cannot move past.
- **67.** acsed's own commands are not journaled and do not go through `ACS_Actor`. *(Author, 2026-10-20: "ACSED
  will not be journalled."; "ACSED commands do not need to go through the ACS_Actor, right?")*

### Details

- **47.** The comment will be given by the client, which is "acses" [the requester], which can only request;
  steps are the application's responsibility. *(The author wrote "asrc"; renamed to "acses" at the
  author's request.)*

## Persistence

- **48.** I think we will not change this database once it is done, I mean since I am not planning to provide
  this as a database library in near time, I guess we can handle everything about the class updates
  with remembering to bump the FORMAT; after all, if we will support former versions we will need to
  store all of the former version classes, which is hard for version control.
- **49.** Let's include Roaring as a dependency.
- **50.** When requested to load a database, acses will use only classes from the database; acses only knows
  the contracts, so [the] database is the authority about implementation.
- **75.** Verbatim: "For acses make it put the presistence manager first and hanle the rest with load class, which
  is handle by persistence manager, at least hereby (if suits)." *(2026-10-24)*
  Meaning: at least here, if it suits: acses writes only the persistence manager into a new folder, and
  everything else, classes and their jar, goes through the manager's class loading, not a copy in acses.
  Commons: "ACSES creates … the acse-persistence.jar file, loads it" (root 52).

### Details

- **51.** We can make a persistence manager contract, and the persistence manager class will [be] in the root
  folder.
- **52.** [The two cases:]
  > Acses given a task to create a database in some path: ACSES creates (by copying from the device
  > config or equals) the acse-persistence.jar file, loads it. Then [it] loads the base implementation
  > classes from [the] device config file (or whatever it is called). Then [it] creates an empty core,
  > then saves everything with persistence.
  >
  > Acses given a task to load a database: it loads the acse-persistence.jar file on the target path.
  > [The] rest is handled by the persistence manager instance found there: the persistence manager
  > finds the current folder from the acse-persistence.data (hereby, for less confusion). It loads the
  > jar on the target folder, and creates the core instance. [The] core instance loads all the
  > necessary instances using the static load methods of the classes in the jar. [The] persistence
  > manager returns the core object. The acses service records the core with a name; if [the] request
  > didn't give it with "--calledBy <name>", the name of the folder that contains the
  > acse-persistence.jar file is the name.

## Words

Claude's definitions, mostly from `GOALS.md`. *(Claude proposed putting "the defined words and the
load-bearing assumptions" in `decisions.md`; the author answered: "Proposed split for goal is
accepted.")*

- **53.** **Ontology** — the philosophical sense: any classification or representation of elements as beings.
  Not OWL, RDF, or a reasoner.
- **54.** **Arity** (noun) — one identity space: the occupied set plus its columns. As a number, how many
  identity spaces a structure spans.
- **55.** **Catalog** — one value per identity; a column. Total: an unwritten slot reads as zero, not absent.
- **56.** **Structure** — data about relations, over one arity or several, under any rule.
- **57.** **Component** — an arity, structure or procedure the core holds (`ArityComponent`,
  `StructureComponent`).
- **58.** The identity is [the] element identity; it is represented by an int named value, [and] its index
  is [the] number of identities that have less value than its. We no longer use the words "identity"
  or "index", since they are not distinctive enough; [the] names are "Identity's uniqueness index"
  (iui for short) and "Identity's memory value" (imv for short). To memorize them easily we record
  the notation "iui <= imv". Structures use the imv since it is stable, and sets map the iui to imv
  since iui is the actual identity. Vacuum
  (hereby compacten) makes all the iui equal their imv. *(2026-10-15)* *(The author wrote "compress"
  and corrected it to "compacten".)*
- **59.** Hereby we use "compacten" (even if illegal grammatically), which stands for removing unnecessary
  space consumption without changing the structure of data. *(2026-10-15)*

## Target use case

Claude's wording, from `GOALS.md`; same source as Words.

- **60.** **iui are dense and imv are local to an arity**, minted from 0, so their values stay small.
- **62.** **Modularity the fullest** The engine must be able to express anything.

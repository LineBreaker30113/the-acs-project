# acs: citable

## Architecture

- **1.** Arities are the first type of element.
- **2.** The "Structure" interface meant to be the second type of elements (the relations
  around sorts of elements (arity)) in the "OntologyEngineCore".
- **3.** The core handles the data base side; even though the components are able to delegate themselves,
  they must pass it through the core for the future plans. *(2026-10-15)*
- **4.** The core handles creation-addition of components, delegations, journal (logging + undo/redo),
  persistence, filtering + analysis, also system analytics like how much RAM is used by/where, etc.
  *(The author wrote "plugins"; renamed to "components" at the author's request.)*
- **5.** [Structures referring to their arities by ints, which the core resolves:] this has its own
  results. We cannot "insert" arities or structures, they become somewhat independent of core (any
  other core with different structures can accept it), using object references makes them less
  dependent on the core. So I am open to suggestions, but for now ints are enough.
- **6.** One idea raises: the components can use object pointers to each other instead of ints. We do not go
  with that idea because the journalling side must be handled with the core, but more importantly, in
  the future we may want to add features that reason about the multiple of arity, structure and
  procedure instances. *(2026-10-15)*
- **7.** [The old plan's "growth is propagated directly from the arity to everything attached" is
  declined:] "different arity" == "different thing".
- **8.** Nothing narrows silently: conversion is exact or throws. `UnsupportedOperationException` = wrong
  type asked; `ArithmeticException` = value does not fit. Floating narrowing rounds instead. *(Old
  plan's wording, confirmed by the author.)*
- **9.** The structures ensure their rules themselves. *(2026-10-10)*
- **10.** Most importantly, I plan on adding a third type of components, the "procedures", which are
  functions about ACS that benefit being close to the system core.
- **11.** Procedures may and will change catalog data; it is like their goal, because they are meant to be
  close to the ACS. We need a way to store changes on catalog data, which is not hard, just extending
  the arity operations list.
- **12.** [Procedures, only for changes or also for reads:] it must allow a way of reasoning, or at least
  state they do not otherwise.
- **13.** The procedures must be so clear they can be automatically reasoned with our ACS system.
  *(2026-10-15)*
- **61.** A structure holds only the connections, never data of its elements: whatever a connection means
  beyond the connection is an entity, so it gets an identity in an arity, which is why
  `ACS_NodeEdgeComponentBase` exists. A unary structure is a directed graph on one arity's identities,
  each kind a restriction of it. *(Author, 2026-10-19: "structures meant to hold only structure data,
  not the data of the elements of a structure. And the ACS assumes every entity we care about belongs
  to one single identity and if the connections represent anything else then the structre, they must
  belong to an Arity, that's why the ACS_NodeEdgeComponentBase is created."; "About the unary
  structures, they are always a graph, only restrictions can exist, for an example a sequence is a
  directed graph with the limitation of each node having a "from" and a "to".")* Complements:
  "Structure — data about relations, over one arity or several, under any rule" (root 56)
  gains what a structure may not hold; "recording the relation is enough" (storing a structure, acs 27) and
  "optimally for memory and time" (acs 18) agree. No conflict found in the summaries.
- **64.** No CQRS (Command Query Responsibility Segregation: writes and reads on separate paths) for now,
  since every action returns a result. *(Author, 2026-10-19: "Every action returns a result, so we do
  not need CQRS right now.")* No related entry found in the summaries.
- **65.** An actor's `call` takes a `DataInputStream`: objects cost too much on a database of 2^25 or more
  records, and a method takes any number of inputs of any type. The author is open to a better
  solution. *(Author, 2026-10-19: "About the call function, make it take a "DataInputStream"."; "Objects
  introduce overhead, for a 2^25+ record data base this is unacceptable. The method require any number
  of inputs of any types, formalizing this would be hard. The DataInputStream is well designed for
  that."; "you know the saying "If you find a good solution, find a better solution" and I am open to
  suggestions.")* No related entry found in the summaries.
- **71.** Verbatim: "(1: a) [the table of actor types lives in `ACS_Actor`], (2: yes) [a `getActor(int type, int
  slot)` on `ACS_Core`], for the type a byte seem to be enough, (3: type << 28 | slot is sufficent for all
  the purposes, where the journal is 8 only), (4: a) [`ACS_Core` gets procedure slots now, beside the
  arities and the structures]" *(2026-10-24)*
  Meaning: one table of actor types, bytes, in `ACS_Actor`: core 0, arity 1, structure 2, procedure 4,
  journal 8, row 9 (arity + 8); the core resolves a type and a slot to its actor, and holds procedures.
  Commons: "Components refer to each other by ints the core resolves" (acs 5); a row reached through its
  arity's request, type 9 being arity 1 plus 8 (`acs/arity`, unreviewed).
  Conflicts: "-128 means journal" (acses 23): the journal is 8.
- **76.** Verbatim: "Yes, core+journal + all three types are actors." [procedures too are the journal's actors]
  *(2026-10-24)*
  Commons: "Every component is an actor" (acs 62); procedure 4 in the table of actor types (above).
- **77.** Verbatim: "(b)" [one string encoding in acs, not two]; "b" [UTF-8 with its byte length, the request
  form] *(2026-10-24)*
  Meaning: `ACS_Actor` and `ACS_PersistenceManager` write strings as UTF-8 with an int byte length; no
  null, and an unpaired surrogate is not kept.
- **90.** Verbatim: "Use `[ACS-NULL!!]` instead." [of a length of -1, for a null string in the UTF-8 form]
  *(2026-10-24)*
  Meaning: a null string is written as the string `[ACS-NULL!!]`, and reads back as null.
  Complements: one string encoding, UTF-8 with its byte length (above).
- **91.** Verbatim: "a" [the part format and the class loader stay inside `ACS_PersistenceManager`, one contract of
  persistence] *(2026-10-24)*
  Commons: "acses only knows the contracts, so [the] database is the authority about implementation"
  (root 50).
- **94.** Verbatim: "Let's shrink them, enumarate: desciption core journal arity, row, structure, <gap>, procedure."
  [the actor types] *(2026-10-24)*
  Meaning: the actor types count from 0: description 0, core 1, journal 2, arity 3, row 4, structure 5,
  6 left free, procedure 7.
  Conflicts: the table of actor types above (core 0, arity 1, structure 2, procedure 4, journal 8, row 9),
  this one being newer; "type 9 stays, arity 1 plus 8" (`acs/arity`, unreviewed): the row is 4;
  "0 means core, -128 means journal, 1 is for arity, 2 for structures, 4 for procedures, 9 for arity
  rows" (acses 23).

### Details

- **14.** The core must also have contracts about delegation; we can (right now) declare an interface [for
  them].
- **15.** [The] Structure contract must allow a way of storing and loading the data via data streams; the
  core can manage files and alike.

## Identities and removal

- **16.** Core is the only caller of removal requests (from outside), and arity is the one removing, that also
  delegates the message through the core. *(2026-10-15)*
- **17.** The component will depend less on the core; arities remove their elements on their own.
  *(2026-10-15)*
- **18.** Structures are responsible for representing the data optimally for memory and time, while the imv
  values can be removed or structures around them change. *(2026-10-15)*
- **19.** Journalling removed the necessity of separating forgetting from removal, so hereby data is
  forgotten (but journalled) the moment an element is removed. *(2026-10-15)*
- **86.** Verbatim: "The core delegates remove request from outside, while arity does the removing and delegating
  forgetting back through the core." *(2026-10-24)*
  Commons: "Core is the only caller of removal requests (from outside), and arity is the one removing, that
  also delegates the message through the core" (acs 16); "Arity removes the identity and calls the core
  forget" (acs 25). Code against this entry: `ACSE_CoreBasic.delegateRemoval` runs the forgetting itself
  after `removeIf`, and `ACS_Core` has no forget for the arity to call.
- **87.** Verbatim: "a" [`ACS_Core` takes and gives identities as int for now, since long imv is out of scope]
  *(2026-10-24)*
  Commons: "Identities can be long; long imv is out of scope" (acs/arity/sets 3).
- **88.** Verbatim: "reject" [removing an arity while a structure spans it] *(2026-10-24)*
  Meaning: `removeArity` refuses while any structure spans the arity.

### Details

*("vacuum" in this section is renamed to "compacten" at the author's request, 2026-10-15.)*

- **20.** When execution (through the multi-arity structures, and maybe few other cases I do not remember
  right now) reaches identities, they check if they exist, when necessary.
- **21.** [On the above:] the important detail is that the structures are also better off not shifting data,
  so they point at the index rather than the identity; by "they check when necessary" I mean we need
  to handle this phenomenon.
- **22.** [Index there is the memory address where the record's data sits, which does not shift on removal:]
  Yes, it is [that], but [it] would not cause any problem; the functions just need to transform the
  identity number before using it.
- **23.** I have a belief the structures do not need to check if an Identity exists.
- **24.** Structures, if they need to remove an identity on another arity, need to call ArityComponent's
  remove on that identity; that is the recursive step: 0 original arity -> 1 related structures -> 2
  start from (0) on each identity needed to [be] removed.
- **25.** Arity removes the identity and calls the core forget for delegating it. *(2026-10-15)*
- **26.** Forget needs to be undoable.
- **27.** [Storing a structure:] recording the relation is enough. Also at the core level storing can be
  done by temporarily storing, then compacten, then final storage. [That] is not bound to be the only
  [sequence]; allow [the] option of direct storing.
- **28.** [Calculated arities:] the calculated arities must be dependent on a structure; this is better
  since being directly dependent [on] another Arity can actually raise errors.
- **29.** Structures are not responsible for the element count; it is just the structure add-on.
- **30.** I think the compacten will be a rare call, on storing to disk and reloading from it.
- **31.** We must make a "forget" call that removes the associated data (like the connections on the indexer)
  but doesn't shift all the identities. Delegations over that call will be handled by the core.
- **32.** No structure compactens a dead identity it reaches on its own.
- **33.** We need to allow client to skip the validation of identities, when they likely know it.
- **34.** The automatic existence check validates a reference exactly when it leaves the structure holding
  it: returned to a caller, or used to address another arity. If checking is requested by client.
- **35.** [Undoing a remove after a compacten is] out of scope right now; compacten [is] planned to be the
  horizon of undo.

## Journal and undo

- **36.** I recently decided the "check and call checkless functions" pattern. Simply, there will be
  functions that resolve [a] client request into private reckless functions of unit operations.
- **37.** Cross-cutting concerns come from a journal: undo/redo replay it, logging reads it; one lock at the
  transaction boundary; multithreading as a single writer with readers on a snapshot. *(Old plan's
  wording, confirmed by the author.)*
- **38.** For every request about ACS, it must be able to store a comment, like the git commit messages, but
  for every action, because every action is made from a resource and later may be audited, which
  will require context.
- **62.** Every component is an actor: a separate interface, named like `ACS_Actor`, replaces
  `TraceableForUndoingRedoing`, and exposes requests to the outside through `call` and its bindings,
  steps to the journal through `doTraceableStep`. *(Author, 2026-10-19: "Looks like the interface
  contracts for components must allow a way to sign their allowed methods with bindings, and a call
  method. Because if you think about it, the ACS contracts are for the management of ACS not its data.
  So if a component allows things outside the responsibilities of ACS it must allow it through a "call"
  like method. This likely fixes all."; "The "doTraceableStep" is similar to what we want. The
  difference is, instead of steps the requests are registered. Then the class name better become
  "TraceableActor" since the classes that implement it are actors that expose some methods to outside
  and some other methods to Journal. This is elegant, we need to make the `TraceableForUndoingRedoing`
  a separate interface with its own name like "ACS_Actor""; "The `doTraceableStep` will be part of
  ACS_Actor")* Commons: "the "check and call checkless functions" pattern" and "can only request; steps are
  the application's responsibility" (acs 36, root 47). Complements: "registers its op codes
  and a codec" gains registered requests beside the op codes. No conflict found in the summaries.
- **63.** The journal is an actor too: its `call` likely holds the inspection refusal, and its
  `doTraceableStep` throws an illegal-action error saying the feature is out of MVP. Being an actor
  allows a multi-branch timeline journal later; that, and the journal's movement handling, are out of
  scope. *(Author, 2026-10-19: "even the journal can implement it, since it exposes some methods to the
  user."; "The journal is movement handling is about multi branching and right now out of scope.";
  "The Journal becoming an actor which is trackable is an abstraction that means we can develop a multi
  branch timeline journal, but not necessary right now, completely out of scope."; "The refuse because
  of "inspection" will likely also be handled inside of the journal's call method"; "the Journal will
  simply make it return an illegal action error stating (This feature is out of MVP).")* No related
  entry found in the summaries.
- **66.** Requests do the checks and call checkless methods; for now the public methods keep their checks.
  *(Author, 2026-10-19: "One important detail, there must be checkless methods and requests must do the
  checks and call those methods."; 2026-10-20: "keep the check in the public methods for now, in
  development extra error check doesn't harm.")* Commons: "the "check and call checkless
  functions" pattern" (acs 36).
- **68.** Verbatim: "Name [of the actor's method taking requests from outside] is "request"." *(2026-10-24)*
  Commons: the code's `ACS_Actor.request`, and every caller of it.
  Conflicts: "exposes requests to the outside through `call`" (acs 62), "its `call` likely holds the
  inspection refusal" (acs 63), "An actor's `call` takes a `DataInputStream`" (acs 65): only the name
  changes, `call` becoming `request`; the rest of each entry stands.
- **69.** Verbatim: "until we had "0" as "Not undoable", and the one left open was "-128", what about we adding a "Not
  undoable, can be ignored."?"; asked whether to add it: "I confirm."; "(a) [add the -128 step]"
  *(2026-10-24)*
  Meaning: step pair -128 is not undoable but can be ignored: undo and redo skip it, and the history stays
  unbroken, where 0 breaks it.
  Complements: "Some actions are not undoable, … we need to allow "break undo history"" (acs 41): 0 keeps
  breaking it; -128 is the not-undoable step that does not.
- **70.** Verbatim: "Cool, go with it." [the inspection refusal staying in the journal's `beginRecordingActivity`,
  which every change goes through] *(2026-10-24)*
  Meaning: during an inspection, the journal refuses a write where it is recorded, so every actor's write is
  refused, not only the journal's own requests.
  Commons: "During an inspection, the rollback `inspectAt`, the journal refuses a write loudly" (root 64).
  Conflicts: "its `call` likely holds the inspection refusal" (acs 63): only the refusal's place changes;
  the rest of the entry stands.
- **72.** Verbatim: "A step should never depend on order." *(2026-10-24)*
  Commons: "we are recording only relations" (acs 47); the `ORDERED` bit removed (`acs/structure`,
  unreviewed); `ACS_Actor`'s step, whose order "doesn't matter".
- **73.** Verbatim: "(b)" [the contract fixes which methods are requests: marks on the interface, so every
  implementation of one contract shares codes] *(2026-10-24)*
  Meaning: request marks sit on the contract's methods; `writeRequestables` must read them from the
  interface, since Java does not carry them to the implementing method.
  Conflicts: "The implementation meant to define the requestables" (root 66): the contract defines them; the
  leaf wins on this detail.
- **74.** Verbatim: "(a) [query recording stays in each actor's `request`], the results are given through the DOS
  [the `DataOutputStream`], so it can state a way for response format." *(2026-10-24)*
  Commons: "A read through a request is journaled as a query" (acs 67).
- **75.** Verbatim: "Yes, comments are good." [reads through a request take a comment too] *(2026-10-24)*
  Commons: "For every request about ACS, it must be able to store a comment" (acs 38).
- **78.** Verbatim: "Steps are not loggable, since they would take to much space, the journal is responsible of
  translating them in case they meant to be written." *(2026-10-24)*
  Meaning: the log holds requests, not steps; a step written anywhere is the journal's translation, and no
  structure registers a codec.
  Conflicts: "A structure registers its op codes and a codec for its journal records" (acs 45): the codec
  goes, the journal translating; "An operation declares its own logging mode" (acs 46): no step is logged,
  so a logging mode is the request's at most. `ACSE_JournalBasic.storeAll` writes each request's steps
  (:326), code against this entry.
- **79.** Verbatim: "1b" [each actor states how its steps read, for the log] *(2026-10-24)*
  Meaning: an actor describes its steps; the journal translates them with that description when they are
  written.
  Complements: "the journal is responsible of translating them in case they meant to be written" (above).
- **80.** Verbatim: "2a" [keep `recordBoolean` and `getBoolean` for the steps to come] *(2026-10-24)*
  Commons: "For the boolean data of the journal we need to use bitset or roaring bitset" (acse 3).
- **81.** Verbatim: "3b" [the journal's methods say "Request" instead of "Activity", in a rename session]
  *(2026-10-24)*
  Meaning: "Activity" leaves the journal's method names for "Request"; the new names are the author's.
- **82.** Verbatim: "I guess the logging mode is canceled, and an artifact of a malfunction." *(2026-10-24)*
  Meaning: I guess no operation declares a logging mode; replacing acs 46 loses the logging mode.
  Conflicts: "An operation declares its own logging mode; the client does not" (acs 46).
- **83.** Verbatim: "Each class must only inherit one contract." [answering how request codes stay apart when a class
  inherits several contracts] *(2026-10-24)*
  Conflicts: "Make a formal "node-cluster" super interface for the node-edge interface, it is simply a
  connector which has graph rules for the primary arity" (acs/structure 6): `ACS_NodeClusterComponentBase`
  extends both `ACS_ConnectorComponentBase` and `ACS_GraphComponentBase`; `ACSE_CoreBasic` implements
  `ACS_Core` and `ACS_DescriptionManager`.
- **84.** Verbatim: "The log should write through each actor's description." [`storeAll` writing each step through
  its actor's description, not as raw ints] *(2026-10-24)*
  Commons: "1b" (above), each actor stating how its steps read. `ACSE_JournalBasic.storeAll` writes raw
  ints (:326), code to change.
- **85.** Verbatim: "Make the core + description manager exception. Infact the classes must not inherit two different
  interfaces in the same goal, describing and being a core in different things." *(2026-10-24)*
  Meaning: a class may inherit two interfaces only when their goals differ; one goal, one contract.
  Complements: "Each class must only inherit one contract" (above), narrowed to one contract per goal.
  Conflicts: `ACS_NodeClusterComponentBase` extends `ACS_ConnectorComponentBase` and
  `ACS_GraphComponentBase`, two structure contracts (acs/structure 6).
- **89.** Verbatim: "a" [structures and arities are added by their full class name, as rows are] *(2026-10-24)*
  Commons: "`addCatalogRow` takes a row class by its full name" (acs 67).

### Details

- **39.** Some actions have an inverse that is among the other actions. Call it "invertible".
- **40.** Some actions recording the change is enough for calculating the undoing.
  Call it "inverse calculatable".
- **41.** Some actions are not undoable, snapshot taking is expensive se we need to allow "break undo history".
- **42.** "Persistence", saving to files, is just writing some data; no need of journal overhead.
- **43.** Sometimes a cluster of operations is [the] steps of a client request, and undoing may take a
  different approach. The example was: adding a parent may cause many remove parent calls, but
  requesting undo on those prior to undoing the add parent is auto rejected. We have two options:
  we can have request resolver functions that call the atomic operations after recording the
  beginning of the operations to the journal, or making them tell the inverse. [The] first option is
  likely better, so we are separating the client side of jobs from inside operations.
- **44.** [The old plan's write barrier is declined; it] needs more details, explained here: for journal
  actions with checks and multi steps, a record at the beginning and end must be done, [and it]
  must contain a name for logging and alike; for steps, each must just contain parameters, itself
  and [its] inverse. More details can be added.
- **45.** A structure registers its op codes and a codec for its journal records. *(Old plan's wording,
  confirmed by the author.)* [replaced by: acs 78]
- **46.** An operation declares its own logging mode; the client does not. *(Old plan's wording, confirmed
  by the author.)* [replaced by: acs 82]
- **47.** [Recording positions, so undo restores the order of parents:] no need, we are recording only
  relations.
- **48.** I plan on making the journal contract alongside the core contract.
- **49.** While your [Claude's] decision of throwing [when undo is called] during a request is sound, we also
  need to add an abort option.
- **50.** [Adding identities:] Yes, journal adding identities; I forgot to tell because it is assumable.
- **67.** A read through a request is recorded in the journal as a query, and `addCatalogRow` takes a row class
  by its full name. *(Author, 2026-10-19, on reads written to the journal as queries: "Yes, record
  them"; picking Claude's option "Full class name" for how `addCatalogRow` picks its row class.)*

## Descriptions

- **51.** [The description:] it must describe each instance of [a] component of the core using a string value;
  it must be able to be read and set. It is there so the agent can state the purpose of each
  component. *(2026-10-11)*
- **52.** Also classes can have a description. For an example, there can be many different implementations
  of mathematically [the] same indexing logic (say two arities, one element from arity one is
  connected to one element from another arity or not) but different ways of representing them,
  performant for different cases. The agent [is] meant to choose between them; also the agent can
  decompile the class files to make its own descriptions. *(2026-10-14)*
- **92.** Verbatim: "b" [the description manager becomes an actor with its own type in the table of actor types];
  "The description itself is another contract and responsibility, while the primary use case are for the
  agents, it means a lot for the data base and it can be moved to persistence package since it has it's
  own persistence related stuff." *(2026-10-24)*
  Meaning: descriptions are an actor and a contract of their own, reached by their own type; they can
  move to the persistence package.
  Complements: the table of actor types (above), which gains the description manager.
- **93.** Verbatim: "It needs to record the changes on the journal, it is not a huge amount of data and it takes a
  small amount of space." [the description changes] *(2026-10-24)*
  Commons: "Descriptions are few strings, and do not take much space" (acse 8).
  Conflicts: "It [the description] is somewhat of a journal itself, so no need of journalling" (acs 53):
  description changes are journaled; creating can still take a description parameter. Code against this
  entry: `ACSE_CoreBasic` keeps descriptions outside the journal, "undo leaves them alone".
- **95.** Verbatim: "Own object" [the description manager is an object of its own, which the core holds as it holds
  the journal] *(2026-10-24)*
  Meaning: `ACSE_CoreBasic` stops implementing the description manager, ending the core + description
  manager exception.
  Conflicts: "Make the core + description manager exception" (above).

### Details

- **53.** It [the description] is somewhat of a journal itself, so no need of journalling, except while
  creating and adding we can require the requests take another string parameter for the description. [replaced by: acs 93]

## Persistence

### Details

- **54.** I want you to properly handle the persistence using methods in the core (and contracts where
  necessary) that take a target folder and store everything in their files. *(2026-10-11)*
- **55.** We can make the persistence manager also variable. *(2026-10-13)*

## Identity sets

### Details

- **56.** I think we need to find ways to define arity sizes; for example, [in] the "cartesian" instance I
  told, the arity that represents the cartesians can have its size calculated.

## Filtering

- **57.** The filtering service is meant to have logical combinations of filters (AND, OR, SUBTRACT, NOT,
  NAND, NOR, XOR, IFOF, IMPLIES, NIMPLIES, CONTRADICTS, NCONTRADICTS), but also things that depend on
  catalog or structure data, like "namedLike" or "childOf". Also this is essentially a query service.

### Details

- **58.** Right now the filtering service can only provide the logical operators, and filters on data depend
  on the client. This is somewhat intended, since we allowed the data to vary, but some
  filtering modules to simplify the client's experience must be done.
- **59.** Cool names for "OR" variants like "IMPLIES", all are redundant. Keep logic minimal right now.
- **60.** They [the OR variants] are boilerplate, meant to be handled last.

# acs: entries to review

# Unreviewed

- Verbatim: "Name [of the actor's method taking requests from outside] is "request"." *(2026-10-24)*
  Commons: the code's `ACS_Actor.request`, and every caller of it.
  Conflicts: "exposes requests to the outside through `call`" (acs 62), "its `call` likely holds the
  inspection refusal" (acs 63), "An actor's `call` takes a `DataInputStream`" (acs 65): only the name
  changes, `call` becoming `request`; the rest of each entry stands.
  Section: Journal and undo
- Verbatim: "until we had "0" as "Not undoable", and the one left open was "-128", what about we adding a "Not
  undoable, can be ignored."?"; asked whether to add it: "I confirm."; "(a) [add the -128 step]"
  *(2026-10-24)*
  Meaning: step pair -128 is not undoable but can be ignored: undo and redo skip it, and the history stays
  unbroken, where 0 breaks it.
  Complements: "Some actions are not undoable, … we need to allow "break undo history"" (acs 41): 0 keeps
  breaking it; -128 is the not-undoable step that does not.
  Section: Journal and undo
- Verbatim: "Cool, go with it." [the inspection refusal staying in the journal's `beginRecordingActivity`,
  which every change goes through] *(2026-10-24)*
  Meaning: during an inspection, the journal refuses a write where it is recorded, so every actor's write is
  refused, not only the journal's own requests.
  Commons: "During an inspection, the rollback `inspectAt`, the journal refuses a write loudly" (root 64).
  Conflicts: "its `call` likely holds the inspection refusal" (acs 63): only the refusal's place changes;
  the rest of the entry stands.
  Section: Journal and undo
- Verbatim: "(1: a) [the table of actor types lives in `ACS_Actor`], (2: yes) [a `getActor(int type, int
  slot)` on `ACS_Core`], for the type a byte seem to be enough, (3: type << 28 | slot is sufficent for all
  the purposes, where the journal is 8 only), (4: a) [`ACS_Core` gets procedure slots now, beside the
  arities and the structures]" *(2026-10-24)*
  Meaning: one table of actor types, bytes, in `ACS_Actor`: core 0, arity 1, structure 2, procedure 4,
  journal 8, row 9 (arity + 8); the core resolves a type and a slot to its actor, and holds procedures.
  Commons: "Components refer to each other by ints the core resolves" (acs 5); a row reached through its
  arity's request, type 9 being arity 1 plus 8 (`acs/arity`, unreviewed).
  Conflicts: "-128 means journal" (acses 23): the journal is 8.
  Section: Architecture
- Verbatim: "A step should never depend on order." *(2026-10-24)*
  Commons: "we are recording only relations" (acs 47); the `ORDERED` bit removed (`acs/structure`,
  unreviewed); `ACS_Actor`'s step, whose order "doesn't matter".
  Section: Journal and undo
- Verbatim: "(b)" [the contract fixes which methods are requests: marks on the interface, so every
  implementation of one contract shares codes] *(2026-10-24)*
  Meaning: request marks sit on the contract's methods; `writeRequestables` must read them from the
  interface, since Java does not carry them to the implementing method.
  Conflicts: "The implementation meant to define the requestables" (root 66): the contract defines them; the
  leaf wins on this detail.
  Section: Journal and undo
- Verbatim: "(a) [query recording stays in each actor's `request`], the results are given through the DOS
  [the `DataOutputStream`], so it can state a way for response format." *(2026-10-24)*
  Commons: "A read through a request is journaled as a query" (acs 67).
  Section: Journal and undo
- Verbatim: "Yes, comments are good." [reads through a request take a comment too] *(2026-10-24)*
  Commons: "For every request about ACS, it must be able to store a comment" (acs 38).
  Section: Journal and undo
- Verbatim: "Yes, core+journal + all three types are actors." [procedures too are the journal's actors]
  *(2026-10-24)*
  Commons: "Every component is an actor" (acs 62); procedure 4 in the table of actor types (above).
  Section: Architecture
- Verbatim: "(b)" [one string encoding in acs, not two]; "b" [UTF-8 with its byte length, the request
  form] *(2026-10-24)*
  Meaning: `ACS_Actor` and `ACS_PersistenceManager` write strings as UTF-8 with an int byte length; no
  null, and an unpaired surrogate is not kept.
  Section: Architecture
- Verbatim: "Steps are not loggable, since they would take to much space, the journal is responsible of
  translating them in case they meant to be written." *(2026-10-24)*
  Meaning: the log holds requests, not steps; a step written anywhere is the journal's translation, and no
  structure registers a codec.
  Conflicts: "A structure registers its op codes and a codec for its journal records" (acs 45): the codec
  goes, the journal translating; "An operation declares its own logging mode" (acs 46): no step is logged,
  so a logging mode is the request's at most. `ACSE_JournalBasic.storeAll` writes each request's steps
  (:326), code against this entry.
  Section: Journal and undo
  Replaces: acs 45, losing the structure's codec.
- Verbatim: "1b" [each actor states how its steps read, for the log] *(2026-10-24)*
  Meaning: an actor describes its steps; the journal translates them with that description when they are
  written.
  Complements: "the journal is responsible of translating them in case they meant to be written" (above).
  Section: Journal and undo
- Verbatim: "2a" [keep `recordBoolean` and `getBoolean` for the steps to come] *(2026-10-24)*
  Commons: "For the boolean data of the journal we need to use bitset or roaring bitset" (acse 3).
  Section: Journal and undo
- Verbatim: "3b" [the journal's methods say "Request" instead of "Activity", in a rename session]
  *(2026-10-24)*
  Meaning: "Activity" leaves the journal's method names for "Request"; the new names are the author's.
  Section: Journal and undo
- Verbatim: "I guess the logging mode is canceled, and an artifact of a malfunction." *(2026-10-24)*
  Meaning: I guess no operation declares a logging mode.
  Conflicts: "An operation declares its own logging mode; the client does not" (acs 46).
  Section: Journal and undo
  Replaces: acs 46, losing the logging mode.
- Verbatim: "Each class must only inherit one contract." [answering how request codes stay apart when a class
  inherits several contracts] *(2026-10-24)*
  Conflicts: "Make a formal "node-cluster" super interface for the node-edge interface, it is simply a
  connector which has graph rules for the primary arity" (acs/structure 6): `ACS_NodeClusterComponentBase`
  extends both `ACS_ConnectorComponentBase` and `ACS_GraphComponentBase`; `ACSE_CoreBasic` implements
  `ACS_Core` and `ACS_DescriptionManager`.
  Section: Journal and undo
- Verbatim: "The log should write through each actor's description." [`storeAll` writing each step through
  its actor's description, not as raw ints] *(2026-10-24)*
  Commons: "1b" (above), each actor stating how its steps read. `ACSE_JournalBasic.storeAll` writes raw
  ints (:326), code to change.
  Section: Journal and undo
- Verbatim: "Make the core + description manager exception. Infact the classes must not inherit two different
  interfaces in the same goal, describing and being a core in different things." *(2026-10-24)*
  Meaning: a class may inherit two interfaces only when their goals differ; one goal, one contract.
  Complements: "Each class must only inherit one contract" (above), narrowed to one contract per goal.
  Conflicts: `ACS_NodeClusterComponentBase` extends `ACS_ConnectorComponentBase` and
  `ACS_GraphComponentBase`, two structure contracts (acs/structure 6).
  Section: Journal and undo
- Verbatim: "The core delegates remove request from outside, while arity does the removing and delegating
  forgetting back through the core." *(2026-10-24)*
  Commons: "Core is the only caller of removal requests (from outside), and arity is the one removing, that
  also delegates the message through the core" (acs 16); "Arity removes the identity and calls the core
  forget" (acs 25). Code against this entry: `ACSE_CoreBasic.delegateRemoval` runs the forgetting itself
  after `removeIf`, and `ACS_Core` has no forget for the arity to call.
  Section: Identities and removal
- Verbatim: "a" [`ACS_Core` takes and gives identities as int for now, since long imv is out of scope]
  *(2026-10-24)*
  Commons: "Identities can be long; long imv is out of scope" (acs/arity/sets 3).
  Section: Identities and removal
- Verbatim: "reject" [removing an arity while a structure spans it] *(2026-10-24)*
  Meaning: `removeArity` refuses while any structure spans the arity.
  Section: Identities and removal
- Verbatim: "a" [structures and arities are added by their full class name, as rows are] *(2026-10-24)*
  Commons: "`addCatalogRow` takes a row class by its full name" (acs 67).
  Section: Journal and undo
- Verbatim: "Use `[ACS-NULL!!]` instead." [of a length of -1, for a null string in the UTF-8 form]
  *(2026-10-24)*
  Meaning: a null string is written as the string `[ACS-NULL!!]`, and reads back as null.
  Complements: one string encoding, UTF-8 with its byte length (above).
  Section: Architecture
- Verbatim: "a" [the part format and the class loader stay inside `ACS_PersistenceManager`, one contract of
  persistence] *(2026-10-24)*
  Commons: "acses only knows the contracts, so [the] database is the authority about implementation"
  (root 50).
  Section: Architecture
- Verbatim: "b" [the description manager becomes an actor with its own type in the table of actor types];
  "The description itself is another contract and responsibility, while the primary use case are for the
  agents, it means a lot for the data base and it can be moved to persistence package since it has it's
  own persistence related stuff." *(2026-10-24)*
  Meaning: descriptions are an actor and a contract of their own, reached by their own type; they can
  move to the persistence package.
  Complements: the table of actor types (above), which gains the description manager.
  Section: Descriptions
- Verbatim: "It needs to record the changes on the journal, it is not a huge amount of data and it takes a
  small amount of space." [the description changes] *(2026-10-24)*
  Commons: "Descriptions are few strings, and do not take much space" (acse 8).
  Conflicts: "It [the description] is somewhat of a journal itself, so no need of journalling" (acs 53):
  description changes are journaled; creating can still take a description parameter. Code against this
  entry: `ACSE_CoreBasic` keeps descriptions outside the journal, "undo leaves them alone".
  Section: Descriptions
  Replaces: acs 53, losing "no need of journalling".
- Verbatim: "Let's shrink them, enumarate: desciption core journal arity, row, structure, <gap>, procedure."
  [the actor types] *(2026-10-24)*
  Meaning: the actor types count from 0: description 0, core 1, journal 2, arity 3, row 4, structure 5,
  6 left free, procedure 7.
  Conflicts: the table of actor types above (core 0, arity 1, structure 2, procedure 4, journal 8, row 9),
  this one being newer; "type 9 stays, arity 1 plus 8" (`acs/arity`, unreviewed): the row is 4;
  "0 means core, -128 means journal, 1 is for arity, 2 for structures, 4 for procedures, 9 for arity
  rows" (acses 23).
  Section: Architecture
- Verbatim: "Own object" [the description manager is an object of its own, which the core holds as it holds
  the journal] *(2026-10-24)*
  Meaning: `ACSE_CoreBasic` stops implementing the description manager, ending the core + description
  manager exception.
  Conflicts: "Make the core + description manager exception" (above).
  Section: Descriptions

# acs

- The element types: arities first, structures second, procedures third (1, 2, 10).
- Structures ensure their own rules and represent data optimally, while imv values can be removed (9,
  18); they are not responsible for the element count (29).
- A structure holds only the connections, never data of its elements: whatever a connection means
  beyond the connection is an entity with an identity in an arity, hence `ACS_NodeEdgeComponentBase`;
  a unary structure is a directed graph on one arity's identities, each kind a restriction of it (61).
- Procedures may and will change catalog data, logged by extending the arity operations list, not hard;
  they must allow reasoning, or at least state they do not, and be clear enough that they can be
  reasoned with automatically (11, 12, 13).
- A different arity is a different thing (7). Nothing narrows silently: conversion is exact, or throws
  when a type or value does not fit; floating narrowing rounds (8).
- The core handles the database side: creation, delegation, journal, persistence, filtering, analytics
  (4); components must delegate through it (3); its delegation contracts must exist and can be declared
  right now (14).
- Components refer to each other by ints the core resolves, for now enough: they cannot be inserted, any
  other core can accept them, and object pointers are not used, since journalling must go through the
  core and future features may reason over many instances; the author is open to suggestions (5, 6).
- The structure contract must allow storing and loading via data streams; the core can manage the files
  (15).
- The core is the only outside caller of removal; the arity removes on its own and calls the core's
  forget, which must remove the associated data but not shift all the identities, and needs to be
  undoable; data is forgotten, but journalled, at removal (16, 17, 19, 25, 26, 31). A structure removing
  an identity elsewhere calls that arity's remove, recursively (24).
- Execution reaching identities checks they exist when necessary, maybe in cases not remembered right
  now (20); structures do not shift data and point at the index, transformed first, which would not
  cause any problem (21, 22); the author believes structures do not need to check (23).
- The client may skip validation when they likely know it (33); the automatic check, if requested,
  validates a reference when it leaves its structure (34); no structure compactens a dead identity on
  its own (32).
- Storing a structure records its relations; the core can store through a compacten, not the only
  sequence (27). The author thinks compacten will be rare (30), and undo past one is out of scope right
  now (35).
- Calculated arities must depend on a structure, since depending on an arity can raise errors (28); the
  author thinks arity sizes need defining, as a cartesian's can be calculated (56).
- Requests do the checks, then call checkless unit operations; for now the public methods keep their
  checks (36, 66). No CQRS, separate write and read paths, for now, since every action returns a
  result (64). The journal carries undo, redo and logging,
  one lock per transaction, a single writer (37); every request must be able to store a comment, since
  every action may later be audited (38).
- Every component is an actor: a separate interface, named like `ACS_Actor`, replaces
  `TraceableForUndoingRedoing`, exposing requests to the outside through `call` and its bindings, and
  steps to the journal through `doTraceableStep` (62). `call` takes a `DataInputStream`: objects cost
  too much on 2^25 or more records, and a method takes any number of inputs of any type; the author is
  open to a better solution (65).
- The journal is an actor too: its `call` likely holds the inspection refusal; its `doTraceableStep`
  throws an illegal-action error, the feature being out of MVP; being an actor allows a multi-branch
  timeline journal later, which, like the journal's movement handling, is out of scope (63).
- Actions are invertible, inverse calculatable, or not undoable, which needs "break undo history" (39,
  40, 41); persistence needs no journal (42).
- For multi-step requests, resolvers can record the beginning, then call the atomic operations, likely
  better, as undo may differ (43); begin and end records must carry a name, each step must carry its
  parameters and inverse, and more can be added (44).
- Structures register op codes and a codec (45); operations, not the client, declare logging modes (46);
  only relations are recorded, no positions (47); adding identities is journalled (50); the journal
  contract comes with the core's (48); an abort option is needed beside throwing (49). A read through a
  request is journaled as a query, and `addCatalogRow` takes a row class by its full name (67).
- Each component instance has a description that must be readable and settable, stating its purpose to
  the agent (51); classes can have one, so the agent can choose among implementations of one logic, say
  whether elements connect or not, or write its own (52); no journalling, except that creating can
  require a description parameter (53).
- Persistence goes through core methods taking a target folder (54); the persistence manager can be
  variable (55).
- Filtering is meant as a query service: logical combinations (AND, OR, NOT and more) and data filters
  like childOf (57). Right now it can only provide the logical operators and the client filters data,
  but some filtering modules must be done (58); the OR variants are all redundant boilerplate, last, and
  logic stays minimal right now (59, 60).
- `arity/CLAUDE.md`: an arity's catalogs and identities; `arity/sets/` holds the identities set.
- `structure/CLAUDE.md`: the structure kinds and the structure contract.

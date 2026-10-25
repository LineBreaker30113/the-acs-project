# acses

- The service must have a way to be managed, while requests can go through the requester (1); it loads a
  lot into memory, so it is likely open for a time, not run per command (2).
- ACSES splits into two packages: `acsed`, the daemon, getting raw data on the input channels, such as
  sockets, that its boot parameters open, maybe only one; and `acsess`, the command line tool, which
  converts the CLI to raw data, owns the communication and boots acsed, so it chooses the channels and
  uses them (21). Separate projects for ACSESS and ACSED are a goal (26). The daemon must be made
  Windows compatible (27).
- Sensitive commands (`acquire`, `waitInspectionReturn`, `inspectAt`) can take `timeout=<ms>`, where
  every request that can be timed out can specify the timeout length so the daemon is not stuck with
  a dead process; boot sets the default or forbids it, and if none is set the command must give one (28).
- It must accept requests in many ways and be modular, loading .class files with ease; loading and
  using a class are separate, so folder- and class-loading methods must exist (3, 4).
- Right now there is not a privilege system, so management and loading requests come through acses (5).
- `boot` must launch a separate process, as its command line cannot be used for anything else (6); the
  engine launches if and only if boot is the command and none runs, otherwise acses only bridges client
  and engine (7); not booted and no boot asked, it fails loud and fast (8).
- Later: commands that interact with the arities and that query data, and custom class files handled
  properly, with an option mechanism beside a list function (13).
- acsess translates names into codes, and acsed reads no name to pick a method; a probe answers where
  an actor's class sits in the file, rewritten only when a new class comes (22).
- A request's address is plain ints: the subject, a database's index or acsed; within a database the
  type, 0 the core, -128 the journal, 1 an arity, 2 a structure, 4 a procedure, 9 an arity row (23).
- A database is an index, listed with its name and folder in a file acsed writes; acsess turns a name
  into its index; a database's name holds no colon (24). The core and the journal are addressed by the
  database's name, `main:`, and also as `main:core` and `main:journal` (25).
- Switches take the shape `+Name`, valued flags `name=value`, so no name is mistaken for a flag (20).
- `beginSession` sends nothing until `Commit`, which sends the queue together; the first refusal stops
  the rest unless `+continueOnRefusal`; values may be quoted four ways, the two triple ones across
  lines (17).
- `acquire` gives a key that every request it covers must give as its first argument, for operational
  safety, held in the daemon's memory only; its scope and `+changesOnly` are flags; it ends with
  `release`, or `release +force` without the key (18).
- `loadDB` of a folder another name holds needs `+Duplicate`; where nothing else holds the folder, it
  only warns (19).
- The expected use won't have several programs: one service, the rest requests (9).
- `storeDB toPath=` stores a copy elsewhere and is never refused for an inspection; a plain `storeDB`
  is refused during one unless `+ignoreInspection` (14).
- `migrate` moves a database to a new folder, with `newName=`, `+WithFormerVersions`, `+WithOtherFiles`,
  every other entry at the root, and `+deleteOld`; it is refused during an inspection unless
  `+ignoreInspection`, and the database then uses the core loaded back from the new folder (15).
- The rollback is `inspectAt`; the wait splits in two: `inspectionState` probes at once,
  `waitInspectionReturn` holds until the return (16).
- A user can just pick a different class instead of changing base classes (10); acses provides the
  base class files only once, at a database's creation, from the initial classes stored for it; a
  "config" folder is not obligatory (11, 12).

# Current state

What exists now, checked against the repo on the date given, and the former handoff. Rules: `CLAUDE.md` →
Markdown files.

## Layers — checked 2026-10-13; paths, names, format and acses 2026-10-18; acses commands 2026-10-19; acsed and acsess 2026-10-20

The live work is the `acs` module (`org.halim.acs`, the contracts) and the `acse` module
(`org.halim.acse`, the classes); `deprecatedCode/` holds `org.halim.ontology`, outside the build.

| Layer | Package | State |
|---|---|---|
| Identity sets | `arity/sets/`, in `acs` and `acse` | Negative at byte/short/int/long, positive at byte/short, Roaring. `lowestFree()`: fast on `NegativeInt`, a counting default elsewhere. |
| Catalog rows | `arity/catalog/` in `acs`; `memory/`, `file/`, `calculated/` under it in `acse` | Row contract `ACS_ArityCatalogRowBase`; stored integer and floating rows, chunked; calculated rows `FilePathCatalogRow` and `HashedFilePathCatalogRow`. Stored rows take `setLongFor` and `setDoubleFor`, exact or refused, never growing; calculated rows refuse. |
| Arity | `ACS_ArityComponentBase`, `ACSE_ComposableArityCatalog` | Occupied set plus rows; adding and removing rows and identities is journaled, and so is a write (`setLongFor`, `setDoubleFor`), as `old ^ new` over the stored bits, within the arity's reach. `removeIf` and `registerIf` are for the core only. |
| Structures | `structure/unary/`, in `acse` | `TightSetHierarchyDirectedAcyclicIntGraphComponent` (always reduced) and `FolderHierarchyDirectedAcyclicIntGraphComponent` (single parent). Both journal their steps and save the relation as (element, parent) pairs. |
| Core | `ACS_Core`, `ACSE_CoreBasic` | Components by slot, a removed slot left empty; `delegateRemoval` with the forget recursion; `captureIdentity`; `store` and `load` of a folder, between requests. Every request takes a comment. `ArityComponentSystem_Core_Deprecated`, an older interface nothing uses, is now in `deprecatedCode/`. |
| Journal | `ACS_Journal`, `ACSE_JournalBasic` | Transactions of nested requests; undo, redo, waypoints, `checkout`, abort; `storeAll` writes JSON Lines. The core builds its journal from a factory, the basic one by default. An aborted client request still loses the undone transactions its start dropped. |
| Persistence | `ACS_PersistenceManager`, `ACSE_PersistenceManagerBasic` | A store writes a file per arity and per structure, the descriptions, the journal's log, `indexer.bin` naming the core's class, the journal's, and every class used, and `classes.jar` holding them; a load builds that core and journal, then each part through its class's static `loadFrom`, taking the classes from the folder's jar. Format 0. The basic manager stores into numbered generations and points `acse-persistence.data` at the current one. |
| Descriptions | `ACS_DescriptionManager`, on `ACSE_CoreBasic` | A string for the core, each arity, catalog row and structure, and each class by name; outside the journal; stored in `core.bin`. |
| Service | `acses`: `org.halim.acses.acsess` (the `acsess` program, JDK only) and `org.halim.acses.acsed` (the daemon) | `acsess boot` starts acsed as a separate process, under `setsid` or else `nohup`, with `socket=<path>`: `$XDG_RUNTIME_DIR/acses/acsed.sock` unless `ACSED_SOCKET` names another, with the lock, the log, `acsed-language.txt` and `acsed.databases` beside it; it serves one connection of frames at a time. acsed's commands: `boot`, `createDB`, `loadDB [+Duplicate]`, `databases`, `acquire`, `release`, `shutdown`; per database `components`, `storeDB [toPath=] [+ignoreInspection]`, `migrate`, `unloadDB`, `acquire`, `release`, `probe`, `inspectionState`, `waitInspectionReturn`. Everything else is an actor's request, `acsess <database>:[core\|journal\|arity-<k>[.row-<j>]] <request>`, translated by the database's `acse-language.txt`. `beginSession` runs in `acsess` and sends its frames together at `Commit`. Flags are `+switch` or `name=value`. Paths must be absolute. `mvn package` lays the program out in `acses/target/acsess/`. |
| Procedures, filtering | — | Do not exist. |

## Legacy features not yet in incubation — checked 2026-10-11

Locking, filtering, analytics, in-place walking, and the service layer. Undo/redo and logging now
exist as the journal; storing and restoring, as folder persistence (2026-10-13).

## Tests — measured 2026-10-18

`mvn -o test` runs no tests: the 19 test files (182 tests, passing before the move) are in
`deprecatedCode/`, outside the build. Claude's throwaway checks live outside the repo (see Claude's
memory).

## Repository — checked 2026-10-14

On `incubation/int-set-algebra`, with `main` at the same commit, `a856b45`. `main` is pushed to
`origin`, https://github.com/LineBreaker30113/the-acs-project.git (renamed from `arity-component-system`
on 2026-10-19): public, `main` its default branch, and GitHub detects the licence as AGPL-3.0 from `LICENSE`. The other local branches are not
pushed. The pre-purge bundle was deleted on 2026-10-13, so no copy of the history before the rewrite
is kept.

`markdowns/novelty-study.md` is published since `a856b45` (2026-10-14) and linked from the README.
Its working files are in `reference/novelty/`, gitignored: `novelty-study-2.md` (the later passes),
`checkpoints.md`, `search-log.md` and `novelty-handoff.md`.

Scan of the tracked tree and of all 44 commits, every one on `main`: no email, home path or secret in
any line ever added, and every commit carries the GitHub noreply address. The only home-style paths
are `~/main` and `~/db3`, examples inside the author's commands in `markdowns/agents-citable.md` (root 1) and
`acses/agents-citable.md` (2). Tracked but not project work: `.mcp.json`, Claude Code's local link to IntelliJ, and
`src/main/java/Main.java`, IntelliJ's template.

## Handoff — decided, not done yet

Claude's wording unless quoted; facts checked 2026-10-18. *(Author: "The handoffs are things that are
decided to be done and urgent.")* The last five, and the reading below, were confirmed on 2026-10-18.
*(Author: "I read and confirm it.")*

- DAG tests in `acse/src/test/` (the author writes them): encoding transitions, root list after every
  phase, cycle refusal, slot reuse, reduction preserving reachability.
- Tests for the catalog row implementations in `acse/arity/catalog/`. *(Author, 2026-10-27: "we need
  test for those implementations")*
- An abstract class for the calculated rows (`FilePathCatalogRow`, `HashedFilePathCatalogRow`), so a new
  one only supplies its values. *(Author, 2026-10-27: "a way to formalize the calculated, I mean an
  abstract class that makes it easier to produce calculated values")* What varies between calculated
  rows is not decided.
- Test the README's "2^28 files of records … a few GiB" claim; nothing measures it. *(Author: "Record
  it, we need to test it.")*
- Later: the procedure contract, and more structure components, the cartesian (ternary) and the
  transitions arities among them (`acs/structure/agents-citable.md` 1).
- The commercial licence's text, drafted by a lawyer before it is sold, and a contributor agreement
  before the first outside contribution.
- Delete `reference/msgfilter.py` once the author says so; it is gitignored.

## Handoff done

Items of the handoff above found done, kept as the record.


Repository state, 2026-09-18: committed through `10aa948`, working tree clean, no remote, nothing
pushed. History was rewritten this day — the study inventory and a third-party paper were purged from
every commit, and three commit messages that named the studied projects were reworded. The only copy
of the pre-purge history is that bundle.

Repository state, 2026-10-11: committed through `3df5498` on `incubation/int-set-algebra`, no remote,
nothing pushed; `.idea/inspectionProfiles/` and `.idea/prettier.xml` untracked.

# Cleanup: the log

Each node's choices with their alternative, skipped hard stops, gaps, and invariants. The author reviews
this file and reverts what they reject.

## Gaps known before the run (2026-10-27)

- acs-gui: the Kernel type ACS-City is to be renamed acs-plane (`KernelPanel.java:25`, the manual, the
  Kernel entry); the author's decision is in `acs-gui/agents-unreviewed.md`.
- acs-gui: the acs-plane drawing rule (green circles, blue lines and arcs, red arrows, a physics
  simulation) is for later; not built.
- acs-gui: `NOTICE` lacks the RSyntaxTextArea BSD notice the merged jar must ship with.
- acs-gui: leftovers of the acsess-gui rename (package `org.halim.acsessgui` names in docs, the
  `markdowns/CLAUDE.md` lines) to check.
- acs: the move of the core and its kin (persistence, journal, descriptor) into a `database` package is
  the author's, done by hand; the cleanup does not touch it. Stored databases name classes by package
  (`ACSE_PersistenceManagerBasic`); if a stored database still names an old package after the move,
  log it.

## Verification setup (2026-10-27)

- Baseline on `structure-merge` before any change: `mvn -q compile` passes; the GUI opens.
- The memory's smoke check (`acses-smoke/run.sh`) fails 22 of 204 there: three helpers import
  `org.halim.acs.ACS_Core`, `ACS_Journal`, `ACS_Actor`, `org.halim.acs.persistence.ACS_PersistenceManager`
  and `org.halim.acse.ACSE_CoreBasic`, and two expectations name `org.halim.acse.ACSE_*`, all from before
  the `database` move. It is outside the repo, so not edited: each node ran a scratchpad copy with only
  those names changed to their `database` packages and its results kept in the scratchpad; baseline 217
  passed, 0 failed.
- The GUI started by `scripts/launch-acs-gui` with `ACSED_SOCKET` on a scratch socket, then stopped with
  its acsed, so the author's running acsed (serving `~/_ustable/acs/db1`) was never reached. `mvn package`
  replaced the jars under `acses/target/acsess/` while that acsed runs; it keeps reading its open copies.
- The dry run of `agents_review_move.py` and the smoke check write their records to
  `~/mein/claude/results/`, as the request asked them run; nothing else outside the repo is written.

## root

Purpose (root unreviewed, 2026-10-27): ACS as Polyadic FCA for a database architecture, used for one
personal knowledge base; the children: ACS the contracts, ACSE optimized and tested implementations,
ACSES the daemon and requester, acs-gui the view of acsess, ACS-alHikmet, ACSAH Viewer, OntoDirectory.
Must-not: no composability killed for speed or memory; no detail optimizing, redundant input checks
included, before ACSAH-Viewer is complete; nothing changed outside the folder. The node's own files:
`pom.xml`, `scripts/`, `README.md`, `CLAUDE.md`, `markdowns/` but the citable, `NOTICE`, `LICENSE`,
`CITATION.cff`, `.mcp.json`, `.idea/`.

Done:
- Deleted `javac.20261021_081457.args`: a javac argfile naming an earlier session's removed scratchpad,
  committed with `5c50fc0`; nothing reads it, and it holds home paths, which the pre-publish scan forbids.
  Not taken: keeping it.
- `markdowns/CLAUDE.md`'s file list: `acsess-gui/CLAUDE.md` → `acs-gui/CLAUDE.md`, and `plan.md` and
  `naming-fix.md` dropped, all three as `487f5bf` left them; `response.md`, present and unlisted, added.
  Not taken: waiting for the next summary rebuild. Its lines on root 68 and 74 still say `acsess-gui`:
  they carry those entries' words, kept. `agents_md_check.py`: ok.

Proposed, not applied (README and pom are not javadoc, the only docs the plan lets the cleanup fix):
- README: "The GUI is OntoDirectory, once improved" and "#### OntoDirectory — The GUI." follow root 23,
  replaced by root 69 ("they both are GUI applications"); "The products" lists no acs-gui (root 68, 72,
  74).
- `pom.xml`'s description lists no acs-gui: incomplete, not false.
- `.idea/compiler.xml` still names a module `acsess-gui`: IntelliJ writes that file; left to it.

Kept, checked: `scripts/launch-acs-gui` and `compile-launch-acs-gui` (the plan's verification uses the
first; the second builds acses and acs-gui, then calls it); `.mcp.json` (Claude Code's link to IntelliJ);
the pom's ontodirectory comment (it still imports `org.halim.acs.ACS_PersistenceManager`, true).

Citable length: read root 1 to 76. The filler-only cuts save a few words each ("I mean" in root 48);
what makes entries long is provenance in italics and Claude's labels, which the quote test does not
count as filler. None proposed.

Reach table: it spans the children; each layer pair is logged under the node on its receiving side.

Gaps:
- The merged acs-gui jar keeps one `META-INF/LICENSE` of its four libraries' (flatlaf, flatlaf-extras,
  rsyntaxtextarea, vineflower), on top of the RSyntaxTextArea notice `NOTICE` lacks (known above).

Untested: no module in the build has a test, so every line of every node is untested; the nodes below
list only what their invariants cover.

Invariants:
- `mvn -q compile` at the root builds acs, acse, acses, acs-alhikmet, acsah-viewer and acs-gui;
  ontodirectory is not built.
- acs depends on fastutil and RoaringBitmap only; acse on acs and those two; acses on acse; acs-gui on no
  project module (it runs acsess as a program), only flatlaf, flatlaf-extras, rsyntaxtextarea and
  vineflower.
- `scripts/launch-acs-gui` runs the last-built acs-gui jar from the project root, arguments passed on.
- Without `acsessPath=`, acs-gui runs `acses/target/acsess/bin/acsess`, or `../acses/…`, from its
  working folder, and refuses any other argument.

Questions:
- Port `~/mein/claude/scripts/acses-smoke/` to the `database` package names (the five lines above)?
  Outside the repo, so not done.

## acs

Purpose (acs unreviewed, 2026-10-27): the mathematics as interfaces for a database implementation of
Polyadic FCA: arity, catalog row, structure, procedure, database (persistence, core, journal, descriptor).
Uses: the JDK, fastutil, Roaring. Must-not: no implementation in acs. 2481 lines before; 131 deleted, 20
added (5%).

Done:
- `arity/sets/package-info.java` and `arity/catalog/package-info.java` were word for word the acse ones
  (both made by the split, `a1c3b86`). acs keeps the contract's lines that no acs javadoc already says
  (the two addressing schemes, the four families and why); the representations and storage forms stay
  in acse's copy, which root 76 backs ("implementation related optimization is ACSE"). Not taken:
  keeping both, or moving everything to acse and leaving acs without a package doc.
- `FloatingValueCatalog`: a javadoc attached to nothing, left by a removed float subtype, deleted; its one
  claim said nowhere else, about seven decimal digits against sixteen, moved into acse's catalog
  package-info, where the float rounding is. "the float variants below" lost "below": they are acse's.
  Not taken: keeping the orphan as part of the class javadoc.
- `ACS_ProcedureComponentBase`: "an arity row is 9, a structure 2" → "a row is 4, a structure 5", as
  `ACSED_Request` numbers them since acs 94.
- `structure/package-info.java`: a list line left over from the removed node-cluster item deleted.
- `IdentitiesSet`: "against `contains` and `get`" → `identityAt` (there is no `get`); "the algebra
  methods above test for a shared representation first" → the overriding representation does (the
  defaults do not; `IdentitiesSetRoaring` does).
- `ACS_Core.addCatalogRow`: the `{@link}` into acse, unresolvable from acs, became `{@code}`; the
  claim holds (`ACSE_CoreBasic.composable` refuses other arities). acs now has 0 unresolved links, was 2.

Proposed, not applied (hard stop: public acs contract members; acs is the author's interfaces):
- Never called in acs, acse, acses or acs-gui: `LongChunkInt.identityOf` (only its javadoc's round-trip
  rule names it), `ACS_BinaryStructure.getPrimaryArity`/`getComplementArity`,
  `ACS_TernaryStructure.getPrimaryArity`/`getRelationArity`/`getComplementArity`/`takenCount`/
  `givenCount`, `ACS_QuaternaryStructure.getUnitArity`, `ACS_UnaryStructure.isAcyclic`,
  `IdentitiesSet.getIntList`, the six `ACS_ProcedureComponentBase.write…` methods (no procedure exists),
  and the constants `STEP_RELATE_TO`/`STEP_UNRELATE_TO` in `ACS_UnaryStructure` and
  `ACS_BinaryStructure`.

Misplaced, not deleted (must-not "no implementation in acs", unreviewed; acs 91 and root 76 keep some):
- `ACS_Actor`'s dispatch, grammar and string form; `ACS_PersistenceManager`'s `open`, `loadCore` and
  `StoredClassLoader` (acs 91 keeps part format and class loader there); the graph analyses as default
  bodies in `ACS_UnaryStructure`; the merge and move defaults in the binary, ternary and quaternary
  contracts; `IdentitiesSet`'s algebra defaults; `LongChunkInt`'s two classes; the exact conversions in
  `ACS_ArityComponentBase` (root 76: "they stay").

Kept, checked: `BinaryRestrictions` and `UnaryRestrictions` (acse storages use them); every marked
request (reached by code through `ACS_Actor.dispatch`); the `ACS_PersistenceManager` string delegates
(acse calls them; one encoding, acs 77 and 90).

Not changed, logged:
- `ACS_ArityCatalogRowBase` is indented with four spaces and its members sit one level in, against the
  tabs-at-column-0 convention; formatting is no allowed action.
- Not checked: `ACS_Core.load`'s "a failed load leaves the core as it was" (an acse claim).

Citable length: read acs 1 to 95's longest entries; each is Claude's sentence plus the author's full
quotes and labels, and a replacing entry must keep the quotes, so none gets shorter by filler alone.
None proposed.

Reach: acs holds the marks most requests share: core 0-7, journal 0-8, arity 0-11, structure 0-4, unary
graph 5-30, binary 5-17, ternary 5-19, quaternary 5-12, procedure 0; the table is under acse.

Questions:
- `ACS_Core.addArity(ACS_ArityComponentBase acseArityComponent, …)` and `addStructure(ACS_StructureBase
  acseArityComponent, …)`: the second's parameter names an arity. Rename? (A name is yours.)
- `ACS_DescriptionManager`: the row, structure and class setters name their parameter `arityDescription`;
  `getClassName(int)` gives a `Class`. Rename?

Invariants:
- `dispatch`: a code outside 0 to count-1 answers false and "<class> has no request <code>".
- A request whose method throws answers false and its message, or the exception's class name.
- A request with a result and no parameter named `comment` records a query and its answer on the
  journal, "refused: <reason>" when it throws; one with a `comment` records no query.
- `requestsOf` refuses a class whose codes repeat or skip; a mark on the interface counts for the class.
- `writeRequestables` refuses `char`, an object other than String and a method compiled without
  `-parameters`, before writing anything.
- `writeString(null)` reads back as null; a negative string length is an IOException; a short stream an
  EOFException.
- `readHeader` refuses another magic and another format; `open` refuses a folder without
  `acse-persistence.data` and a class that is no persistence manager.
- `StoredClassLoader` takes a class its jar holds before the program's, never a `java.` one.
- `writeClass` outside a store and `readPart` outside a load throw IllegalArgumentException; part -1
  reads back as null.
- Every accessor an arity or row does not answer throws UnsupportedOperationException naming the held
  type; a lossy conversion throws ArithmeticException: `exactShort(32768)`, `exactByte(128)`,
  `exactInt(1.5)`, `exactLong(9.223372036854776E18)`, `exactDouble(2^53 + 1)`.
- `copy…Into` default: `dest[offset + k] == get…For(from + k)` for k in 0 to to-from-1.
- `IntSizedIntegerValueCatalog.getIntFor` never throws; `IntegerValueCatalog.setDoubleFor(1.5)` and
  `FloatingValueCatalog.setLongFor(2^53 + 1)` throw ArithmeticException.
- `LongChunkInt`: `identityOf(chunkOf(i), offsetOf(i)) == i`; `offsetOf(i)` in 0 to `fullLength()`-1;
  `chunkOf` non-decreasing; `chunkCount(-1) == 0`; the tail's `lengthOf` is `offsetOf(last) + 1`;
  power of two refuses bits under 1 or over 30, uniform a size under 1.
- `IdentitiesSet`: `lowestFree` is the least non-negative identity not held; `intersectionCount ==
  intersect().size()`; `intersect` and `difference` ascending; `retainAll`, `removeAll`, `addAll` answer
  whether the set changed; `saveTo` then `loadInto` gives the same set; `getAsRoaringBitset` holds the
  same identities.
- Unary graph: `reaches(a, b)` iff `pathLength(a, b) >= 0`; `distance(a, a) == 0`; a root's `depth` is 0;
  `topologicalOrderAmong` refuses a directionless graph (UnsupportedOperationException) and a cycle
  (IllegalStateException); `mergeUnary` makes no self-loop; `moveUnary` leaves the source unlinked;
  `partsAmong` is count, then each part's size and sorted elements; `connectivityAmong` is -1 when no
  pair connects.
- Binary: `mergeBinary` with an arity index not 0 or 1 throws IllegalArgumentException;
  `getComplementsOf(p).length == complementCount(p)`.
- Ternary: `isRelatedToTernary` iff the primary is taken and the complement given; `moveTernary` leaves
  the source with nothing. Quaternary: `mergeQuaternary` adds no triple the target holds;
  `moveQuaternary` leaves the source with no triple.

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

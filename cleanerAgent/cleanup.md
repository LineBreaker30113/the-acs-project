# Cleanup: the plan

The plan for the cleanup session, settled with the author on 2026-10-27. The session works on its own,
logs every choice in `cleanerAgent/log.md`, and the author reviews the log and reverts what they reject.

## The state

- The project works and the GUI works. At most about 20% of the code is more than necessary: a few
  redundant lines, few errors, few missing features, many missing tests.
- An error is lost reach on delegation: acsess or acs-gui not able to use some functionality of acse.

## Start

Only after the author's move of the core, persistence, journal and descriptor into a `database`
package is committed; the cleanup never redoes or changes that move.

## Scope

- Nodes, in cascade order, a parent before its children: root, `acs`, `acse`, `acses/acsed`,
  `acses/acsess`, `acs-gui`.
- Skipped: `acs-alhikmet`, `acsah-viewer`, `ontodirectory`, `deprecatedCode/`, `reference/`.
- Each node's purpose, uses and must-not are in its folder's `agents-unreviewed.md` (2026-10-27) and its
  `CLAUDE.md`; a parent's purpose says what its children offer.
- Root must-not: no composability killed for speed or memory; no optimizing of details, redundant input
  checks included, before ACSAH-Viewer is complete; no change outside the repo unless the author asks.

## Per node

1. Read the node's purpose, uses and must-not.
2. Five tests per line, against that purpose: **used** (something reaches it); **said once** (no
   duplicate); **decided** (follows from a citable entry or a pattern the author wrote); **true docs**
   (the javadoc says what the code does now); **checked** (a test fails if it breaks). Tests come later,
   from the author, so "checked" only lists the untested lines; it writes no test.
3. The reach table, per layer pair: acse marks -> `acse-language.txt` -> acsed -> acsess -> acs-gui; each
   functionality offered, used, or a gap. A gap is logged, not fixed: a fix is a feature.
4. Allowed actions: delete what is unused or duplicated; merge a duplicate into the author's copy or the
   one a citable backs; fix javadoc to what the code does; **shorten comments and javadoc**, keeping
   the register (invariants and trade-offs) and no claim dropped.
5. Citable length: never edit a citable. A shorter form goes as an unreviewed entry in the same folder,
   with a `Replaces:` line per entry it shortens, under the quote test (only filler removed).
6. A line breaking a must-not is misplaced, not unused: log it, don't delete it.
7. The 20% bound: past about 20% of a node's lines deleted, stop deleting in that node and log it.
8. Write the node's invariants as plain lines in the log, as input for the author's tests.
9. Verify: `mvn -q compile`, the ACSES smoke check, start the GUI (`scripts/launch-acs-gui`). A failing
   node is reverted and logged.
10. One commit per node on a `cleanup` branch made from `structure-merge`.

## Hard stops

Skipped, logged as "proposed, not applied" with the reason; the session goes on.

- Deleting a public `acs` contract method.
- Changing a request code.
- Changing the journal or a file format.
- Dropping any feature `acs-gui` shows.

## Not allowed

New features, new interfaces, a choice that is a unique part (log it as a question), anything outside
the repo, editing a citable.

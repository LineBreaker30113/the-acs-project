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

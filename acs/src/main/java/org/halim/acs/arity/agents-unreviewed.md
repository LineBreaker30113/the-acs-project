# acs/arity: entries to review

# Unreviewed

- Verbatim: "ArityRowBase [`ACS_ArityCatalogRowBase`] is not an actor, the Arity itself meant to call it.";
  "Actually catalog [`ACS_ArityComponentBase`] is the actual contract for ACS and the Catalog Row is the
  contract for the implementation named modular catalog [`ACSE_ComposableArityCatalog`], So we need to
  allow the arity request method to allow calling row's methods."; on keeping type 9: "(a) [acsed
  routes it to the row's arity], in terms of bits it is arity + 8, a shortcut" *(2026-10-24)*
  Meaning: a catalog row is no actor; the arity's catalog is the ACS contract, the row only the composable
  arity's, and the arity's request reaches its rows' methods.
  Commons: "the client must be able to define [the] table itself or use a modular table" (arity 1).
  Complements: "Every component is an actor" (acs 62): a row is no component, so it is no actor.
  Complements: "9 for arity rows" (acses 23): type 9 stays, arity 1 plus 8, a shortcut acsed routes
  to the row's arity, whose request reaches the row.
  Section: Architecture
- Verbatim: "[Rows] are not actors of the ACS, so the acs requests make the arity table change data in a row,
  so they can move to the ACSE side, they are in ACS only for convenience."; asked whether to move them:
  "Do not move." *(2026-10-24)*
  Meaning: the catalog row contracts, `acs/arity/catalog`, stay in acs for convenience, though they are
  the composable arity's and no actors.
  Complements: the entry above, a row being no actor and reached through its arity's request.
  Section: Architecture
- Verbatim: "(b) [the arity's request reaches only the table accessors, as now], but it is concern, some rows
  allow additional management above just data, we can make the rows to extend actor interface. This goes
  against the former claim, but for a different reason, so both can stay." *(2026-10-24)*
  Meaning: the rows can extend `ACS_Actor` for management above their data, while they stay no actors of
  the ACS itself.
  Conflicts: "ArityRowBase [`ACS_ArityCatalogRowBase`] is not an actor" (above): both stay, for different
  reasons.
  Section: Architecture
- Verbatim: "since the client is not supposed to see imv, only their iui [record if not already] the range must
  skip."; "I meant only occupied identities." [what a range read gives] *(2026-10-24)*
  Meaning: the client sees only iui, never imv; a range read gives the occupied identities only, so the
  removed ones are skipped.
  Commons: "sets map the iui to imv since iui is the actual identity" (root 58); "Bulk catalog reads
  intersect the range with the occupied set once" (arity 6).
  Conflicts: the procedure contract's "the imv it touches … The imv, not the iui, since it is stable"
  (`ACS_ProcedureComponentBase` javadoc); the requests take and `captureIdentity` gives the imv. Code
  against this entry: `copy…Into` copies every identity in the range.
  Section: Identities and removal
- Verbatim: "We also need to add functionality of setting and changing identity sets." [an arity's identities
  set] *(2026-10-24)*
  Meaning: an arity's identities set can be set and replaced by another; a representation being ACSE's
  (root, unreviewed: "ACS only a mathematical entity"), the replacing is ACSE's and not journaled.
  Commons: "a set can be swapped for a cheaper form as its density or its size changes, and nothing above
  notices" (`acs/arity/sets/package-info.java`); "the client can implement a specific identity set if it
  wants" (acse/arity/sets 3).
  Section: Identities and removal

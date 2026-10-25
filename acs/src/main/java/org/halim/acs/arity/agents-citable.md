# acs/arity: citable

## Architecture
- **10.** Verbatim: "ArityRowBase [`ACS_ArityCatalogRowBase`] is not an actor, the Arity itself meant to call it.";
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
- **11.** Verbatim: "[Rows] are not actors of the ACS, so the acs requests make the arity table change data in a row,
  so they can move to the ACSE side, they are in ACS only for convenience."; asked whether to move them:
  "Do not move." *(2026-10-24)*
  Meaning: the catalog row contracts, `acs/arity/catalog`, stay in acs for convenience, though they are
  the composable arity's and no actors.
  Complements: the entry above, a row being no actor and reached through its arity's request.

### Details

- **1.** [Catalogs as components, `CatalogComponent`:] I accept it, but we must allow the client to decide
  to define catalogs without modules. I mean for each Arity we will have a table, and the client
  must be able to define [the] table itself or use a modular table. Also, for clarity, we will need
  to name them like "ArityCatalogTable" or "ArityModularCatalogTable" or "ArityCatalogRowComponent".
  [Since then: "Modular" becomes "Composable".]
- **2.** [Gather, and resolving a catalog's name to its slot:] "scattered" is unlikely for the assumption,
  and [the] naming method is not necessary right now.
- **3.** Not all the catalogs are meant to be pure array lists; they can be calculations using the identity
  in the arity.

## Identities and removal
- **9.** A client gets a new record's index by capturing it; `registerIf` is no request. *(Author, 2026-10-20:
  "`registerIf` must go, and client must call capture which returns an index.")* Commons: "Core is the
  only caller of removal requests (from outside)" (acs 16).
- **12.** Verbatim: "since the client is not supposed to see imv, only their iui [record if not already] the range must
  skip."; "I meant only occupied identities." [what a range read gives] *(2026-10-24)*
  Meaning: the client sees only iui, never imv; a range read gives the occupied identities only, so the
  removed ones are skipped.
  Commons: "sets map the iui to imv since iui is the actual identity" (root 58); "Bulk catalog reads
  intersect the range with the occupied set once" (arity 6).
  Conflicts: the procedure contract's "the imv it touches … The imv, not the iui, since it is stable"
  (`ACS_ProcedureComponentBase` javadoc); the requests take and `captureIdentity` gives the imv. Code
  against this entry: `copy…Into` copies every identity in the range.
- **13.** Verbatim: "We also need to add functionality of setting and changing identity sets." [an arity's identities
  set] *(2026-10-24)*
  Meaning: an arity's identities set can be set and replaced by another; a representation being ACSE's
  (root, unreviewed: "ACS only a mathematical entity"), the replacing is ACSE's and not journaled.
  Commons: "a set can be swapped for a cheaper form as its density or its size changes, and nothing above
  notices" (`acs/arity/sets/package-info.java`); "the client can implement a specific identity set if it
  wants" (acse/arity/sets 3).

### Details

*("vacuum" in this section is renamed to "compacten" at the author's request, 2026-10-15.)*

- **4.** If the identity number rather than the index is given (which is handled by the identities set of
  the ArityComponent), it auto skips the removed ones; we just need to specify that the intended
  behavior is using the arity component like that.
- **5.** We will not need a function like "swap" or "captureNotForget" because of what an arity represents;
  more on that later.
- **6.** Bulk catalog reads intersect the range with the occupied set once, then read, instead of checking
  each element.
- **7.** I think we do not need to make pending removals; the identity sets handle it.
- **8.** We do not need to make a "truncate" right now. But later we can make this. Also, the compacten (may
  rename to "compress") actually can simply swap the last few entries with the dead ones and
  truncate out the dead ones.

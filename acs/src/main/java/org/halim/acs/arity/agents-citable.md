# acs/arity: citable

## Architecture

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

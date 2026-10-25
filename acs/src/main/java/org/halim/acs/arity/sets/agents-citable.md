# acs/arity/sets: citable

## Identities and removal
- **5.** Verbatim: "a" [`occupied()` gives a read-only view, and changes stay inside the arity] *(2026-10-24)*
  Commons: "Core is the only caller of removal requests (from outside), and arity is the one removing"
  (acs 16).

### Details

*("vacuum" in this section is renamed to "compacten" at the author's request, 2026-10-15.)*

- **1.** I plan on using the compacten idea where the index of an identity is converted to identity at the
  ArityModule's identitiesSet.
- **2.** [Capturing back:] identity set must allow a way of capturing the lowest positive
  excluded value. I mean there is an IntArrayList of excluded [identities]: [its] first element if
  the list is not empty, or just increasing the borders. So the identities set contract is
  responsible.

## Identity sets
- **4.** Verbatim: "yes" [the identities set's names say iui and imv, not index and identity, in a rename session,
  the names being the author's] *(2026-10-24)*
  Commons: "An identity has its iui, the count of lesser identities, and its imv, its stable value";
  "'identity' and 'index' are no longer used" (root 58).
- **6.** Verbatim: "Also about the identities set, they must have an "iterate" like method that maps a continuos list
  of iui their imv, because this is faster." *(2026-10-24)*
  Meaning: the identities set gives the imv of a run of consecutive iui in one call, beside `identityAt`
  one at a time; the name is the author's.
  Commons: "the index of an identity is converted to identity at the ArityModule's identitiesSet"
  (arity/sets 1); the client seeing only iui (`acs/arity`, unreviewed).

### Details

- **3.** [May identities be long?] Long imv is out of scope, but yes they can. *(2026-10-15)*

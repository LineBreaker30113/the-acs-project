# acs/arity/sets: entries to review

# Unreviewed

- Verbatim: "yes" [the identities set's names say iui and imv, not index and identity, in a rename session,
  the names being the author's] *(2026-10-24)*
  Commons: "An identity has its iui, the count of lesser identities, and its imv, its stable value";
  "'identity' and 'index' are no longer used" (root 58).
  Section: Identity sets
- Verbatim: "a" [`occupied()` gives a read-only view, and changes stay inside the arity] *(2026-10-24)*
  Commons: "Core is the only caller of removal requests (from outside), and arity is the one removing"
  (acs 16).
  Section: Identities and removal
- Verbatim: "Also about the identities set, they must have an "iterate" like method that maps a continuos list
  of iui their imv, because this is faster." *(2026-10-24)*
  Meaning: the identities set gives the imv of a run of consecutive iui in one call, beside `identityAt`
  one at a time; the name is the author's.
  Commons: "the index of an identity is converted to identity at the ArityModule's identitiesSet"
  (arity/sets 1); the client seeing only iui (`acs/arity`, unreviewed).
  Section: Identity sets

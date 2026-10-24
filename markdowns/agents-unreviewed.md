# root: entries to review

# Unreviewed

- Verbatim: I want you to plan a session that will complete the acs-view, but prior to that rename it to
  acsess-gui, since it is what it is. [Where it builds, picked among Claude's options:] (Q2: a) [it joins
  the root pom's modules]. *(2026-10-23)*
  Commons: (root 68) already names the module `acsess-gui`; (root 20) a parent project compiling all the
  others.
  Complements: (root 21) six submodules; acsess-gui becomes a seventh in the root pom (root 68).
  Section: Layers and names
- Verbatim: "For acses make a it put the presistence manager first and hanle the rest with load class, which
  is handle by persistence manager, at least hereby (if suits)." *(2026-10-24)*
  Meaning: at least here, if it suits: acses writes only the persistence manager into a new folder, and
  everything else, classes and their jar, goes through the manager's class loading, not a copy in acses.
  Commons: "ACSES creates … the acse-persistence.jar file, loads it" (root 52).
  Section: Persistence
- Verbatim: "Important detail is ACS only a mathematical entity, implementation related optimization is ACSE.
  Act accordingly."; asked whether those in acs move to acse: "Yes, they stay." *(2026-10-24)*
  Meaning: ACS is mathematical and the optimizations are ACSE's; the ones in acs stay there.
  Commons: "The ACS is a system, as it names; ACSE is its implementation" (root 28).
  Conflicts, in acs today: `LongChunkInt`, the range reads `copy…Into`, `getEstimatedBytesPerIdentity`,
  `ensureCapacity` and `truncate`, `getAsRoaringBitset`, and the storage forms the sets' and catalogs'
  package-infos describe; the iui-run method "because this is faster" (`acs/arity/sets`, unreviewed).
  Section: Layers and names

# acs: entries to review

# Unreviewed
- Verbatim: "in the ACS contracts we use the "int getClassCount(); Class getClass(int index);" format
  for avoiding object overhead." *(2026-10-25)*
  Commons: "Objects cost too much on 2^25 or more records" (acs 65); `ACS_DescriptionManager`'s
  `getClassNameCount` and `getClassName(int index)`. No conflict found in the summaries.
  Section: Architecture

- Verbatim: There will be logical filters, structural (like `descendantOf`), data based (like
  `greaterThan`). It must result in all the identities that satisfy the claim one way. *(2026-10-26)*
  Meaning: the filters come in three families, and a filter's result is the set of all identities
  satisfying its claim in at least one way, as a DAG identity with several parents is a `descendantOf`
  if any path leads to it; the engine ships the structural and data-based ones, not only the logical.
  Commons: (acs 57) logical combinations, and data or structure filters like "childOf"; (acs 4) the core
  handles filtering.
  Complements: (acs 59, 60) the OR variants stay redundant boilerplate, last.
  Conflicts: (acs 58) filters on data depend on the client, and only the logical operators exist right now.
  Section: Filtering

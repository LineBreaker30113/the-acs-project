# acs/arity/catalog: entries to review

# Unreviewed

- Verbatim: "never shrinks." [a row's `ensureCapacity` asked for less than it reaches] *(2026-10-24)*
  Meaning: lowering is `truncate`'s alone. Code against this entry: `ChunkedCatalogBase.ensureCapacity`
  truncates when asked for less (:116).
  Section: Architecture

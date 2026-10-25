# acs/arity/catalog: citable

## Architecture

- **1.** Verbatim: "never shrinks." [a row's `ensureCapacity` asked for less than it reaches] *(2026-10-24)*
  Meaning: lowering is `truncate`'s alone. Code against this entry: `ChunkedCatalogBase.ensureCapacity`
  truncates when asked for less (:116).

# acse/arity/sets: citable

## Identities and removal

### Details

*("vacuum" in this section is renamed to "compacten" at the author's request, 2026-10-15.)*

- **1.** The removal and binary search [over the hole list] likely will never get that slow.

## Identity sets
- **5.** Verbatim: "Also we need to make a short array list based identity set, because some arities may be very
  small."; on the short positive set already holding that: "Good, do not remove then." *(2026-10-24)*
  Commons: `IdentitiesSetPositiveShort`, which holds the identities sorted in a short array, the form its
  javadoc gives for "sparse or simply small" sets.
  Conflicts: "Add [Positive]Int, also deleting the narrow variants, to the plan" (acse/arity/sets 2): the
  short positive set stays.

### Details

- **2.** Add [Positive]Int, also deleting the narrow variants, to the plan.
- **3.** Some arities may have elements up to a certain number easily computed, like say we have a single
  layer fully [connected] perceptron: its nodes are one arity, its weights are a different arity,
  and its count and range can be easily computed, so the client can implement a specific identity
  set if it wants.
- **4.** Right now the engine accepts such a thing with ease, so we just need to document it. Later we may
  need to make a few additions for it to work.

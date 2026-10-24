# acse: entries to review

# Unreviewed

- Verbatim: "(3: type << 28 | slot is sufficent for all the purposes, where the journal is 8 only)"
  [answering whether the journal keeps an actor's type and slot as two ints] *(2026-10-24)*
  Meaning: the journal keeps an actor as one int, `type << 28 | slot`, the type from `ACS_Actor`'s table,
  replacing `ACSE_JournalBasic`'s `type << 24 | slot`.
  Commons: the table of actor types in `ACS_Actor` (`acs`, unreviewed).
  Section: Journal and undo

# acs/arity

- A client must be able to define an arity's catalog table without components, or use a composable
  one (1).
- Not all catalogs are arrays; they can be calculations on the identity (3).
- Scattered gathering is unlikely; a name-to-slot method is not necessary right now (2).
- A client gets a new record's index by capturing it; `registerIf` is no request (9).
- Given an identity number, the arity skips removed ones via its identities set (4); bulk reads
  intersect the range with the occupied set once (6).
- The author thinks pending removals are not needed, the sets handle them (7); swap and
  captureNotForget are not needed either, more later (5).
- A truncate is not needed right now but can come later; compacten, which may become "compress", can
  swap the dead entries with the last ones and truncate (8).
- A catalog row is not an actor; the arity's request calls its rows' methods, and rows stay in ACS
  only for convenience so do not move them, though they can move to ACSE (10, 11).
- The client is not supposed to see imv, only their iui, never imv, so a range read must skip removed
  ones and yield only occupied identities (12).
- An arity's identities set can be set and changed; since ACS is only a mathematical entity, replacing
  is not journaled (13).
- `catalog/CLAUDE.md`: catalog rows and capacity.
- `sets/CLAUDE.md`: the identities set.

# acs/arity/sets

- Planned: the identities set maps an index to its identity (1); names say iui and imv, not index and
  identity (4).
- Its contract must capture back the lowest excluded value: the list's first if not empty, else the
  next border (2).
- Identities can be long; long imv is out of scope (3).
- `occupied()` gives a read-only view, keeping changes inside the arity (5).
- The identities set must have an iterate-like method that maps a continuous list of iui to their imv
  because this is faster (6).

# acse/arity/sets

- Hole-list removal and binary search likely will never get that slow (1).
- Planned: PositiveInt, the narrow variants deleted (2).
- An arity whose size may be easily computed can get a client-made identity set; right now that is
  accepted and needs documenting, later it may need additions (3, 4).
- A short array list based identity set stays because some arities may be very small, so do not remove
  the short positive set (5).

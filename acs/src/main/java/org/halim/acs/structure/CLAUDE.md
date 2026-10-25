# acs/structure

- Many structure components are needed; ternary examples would be an arity as the cartesian of two
  others, or as transitions between arities (1).
- The contract holds its arities, removal notification and per-element read and write of its state (2).
- Two-arity structures vary; a one-arity structure is always a graph (3).
- Structures are just connections; the number of arities only changes how many approaches they can
  have (6). Every binary structure is a connector, each kind a restriction of it: node-edge always
  connects two nodes of one arity to one element of the second at once; from-via-to is a connector
  whose connections matter, an order higher, as node-edge is to the unary graph (5).
- Node-cluster is a connector with graph rules on its primary arity, node-edge one of its kinds; a
  connector may force both sides to be graphs; the most formal ternary connects a few elements from
  each of three arities, any of which may be forced to be a graph (6).
- The most formal forms are not clusters: unary is graph, binary is connector (elements-clusters), and
  ternary is primary-relation-complement; every kind is a restriction of them; former methods stay
  while new methods suit these claims (8, 10).
- Remove the ORDERED bit: structures hold structure only, and ORDERED is not related to structure but
  how it is stored (7).
- Each request is a link without direction; for directed graphs, the first points to the last (9).
- For each arity a boolean flag marks whether it is caused or not, so the core refuses client capture
  only on caused arities (11).
- Under isTight, `addParent` cannot be inverted by undoing all in reverse, but can by undoing all in
  order or by closing isTight for a while (4).
- `theorems.md`: mathematical theorems formalizing structure contracts from unary to quaternary.
- `binary/CLAUDE.md`: the synapse structure, and clusters' direction.

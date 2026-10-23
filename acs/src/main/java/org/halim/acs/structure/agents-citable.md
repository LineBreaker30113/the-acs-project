# acs/structure: citable

## Architecture

- **1.** We need to define many Structure Components. A ternary example would be assuming an arity is [the]
  cartesian [product] of two other arities; another would be assuming an arity is some transitions
  from the one arity to another.

### Details

- **2.** The `Structure` contract holds which arities it spans; removal notification and alike; per-element
  read and write of its own state. *(Old plan's wording, confirmed by the author.)*
- **3.** There [are] many ways for two arity structures to exist, where a single arity structure is always a
  type of graph.
- **5.** Every binary structure is a connector, each kind a restriction of it: node-edge is a connector whose
  every edge connects to two nodes of one arity at once, and from-via-to is a connector whose connections
  matter, an order higher as node-edge is to the unary graph. *(Author, 2026-10-21: "every binary
  structure is a connector, and the node-edge is the restriction of always two elements from the same
  arity connecting to one in the second arity at the same time."; "You see we have the "unary graph" and
  a second order for "node-edge". Same logic with the "from-via-to", it is a "Connector" where
  connections matter. So same thing with Connectors. If we didn't record there can be many types of
  connectors based on the restrictions.")* Commons: "two elements from a single arity [are] matched to a
  single element on the second arity" ([The synapse structure:], acs/structure/binary 1).
  Complements: "There [are] many ways for two arity structures to exist" (acs/structure 3)
  gains what those ways are; "some transitions from the one arity to another" (acs/structure 1) is from-via-to; the 2026-10-19 entry in `acs`, unreviewed, on a structure holding only connections,
  gives the order step. No conflict found in the Citable; the connector contract's "no order, no
  weight, no count" is code, asked in the session.
- **6.** Structures are just connections, and the number of arities only changes how many approaches they can
  have: node-cluster is a connector with graph rules on its primary arity, node-edge one of its kinds; a
  connector may force both sides to be graphs, and the most formal ternary structure connects a few
  elements from each of three arities, any of which may be forced to be a graph. *(Author, 2026-10-21:
  "Structures are just connections, number of arities only change how many approaches they can have. We
  can also make a connector that forces both sides to be a graph."; "Make a formal "node-cluster" super
  interface for the node-edge interface, it is simply a connector which has graph rules for the primary
  arity."; "About the ternary, in its most formal form it connects few elements from any three, we can
  force any of the arities to be graphs, not necessary right now just shows the general shapes of
  structures.")* Commons: the 2026-10-21 entry above, "every binary structure is a connector"; the
  2026-10-19 entry in `acs`, unreviewed, a structure holding only the connections. Complements: "a single arity structure is
  always a type of graph" (acs/structure 3) is the rule a connector may force on a side; "some
  transitions from the one arity to another" (acs/structure 1) is one ternary shape. No
  conflict found in the Citable.

## Journal and undo

### Details

- **4.** "addParent" under isTight cannot be inverted by undoing all in reversed order; it can be reverted
  by undoing all in the same order; also, it can be undone by closing the isTight temporarily.

# acs/structure: citable

## Architecture

- **1.** We need to define many Structure Components. A ternary example would be assuming an arity is [the]
  cartesian [product] of two other arities; another would be assuming an arity is some transitions
  from the one arity to another.
- **7.** Verbatim: "Remove the "ORDERED" bit [of the connector and of-by-to contracts], the structures meant to hold
  the structure only, ORDERED is not related structure but how it is stored." *(2026-10-24)*
  Commons: "we are recording only relations" (acs 47); "Structures are responsible for representing the
  data optimally" (acs 18): an order is the storage's, not the relation's.
  Complements: the step `ACS_Actor` describes, whose order "doesn't matter", holds for every structure
  once no list keeps places.
- **11.** Verbatim: "(b) [a method on the structure base gives the arities it causes, so the core can refuse a
  client's capture there], for each arity let's keep boolean flag for if it is caused or not."
  *(2026-10-24)*
  Meaning: the structure answers, per arity it spans, whether it causes it; read as strictly caused, the
  structure having made it, since only there the core refuses a client's capture.
  Complements: the base's javadoc, an arity caused but not strictly, or strictly caused; "the calculated
  arities must be dependent on a structure" (acs 28).

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
- **8.** Verbatim: "I decided the most formal forms are not clusters. So for unary it is [the] graph, for binary it is
  [the] connector, and for ternary it [is] primary-relation-complement (like of-by-to but has less
  restrictions)." "Connector is equal to "elements-clusters"." *(2026-10-24)*
  Meaning: one most formal form per arity count, every kind a restriction of it: graph, connector (that is,
  elements-clusters), primary-relation-complement; node-cluster and of-by-to are kinds, not forms.
  Urgent: a requirement of the structure requests in acse (root 41).
  Commons: "Every binary structure is a connector, each kind a restriction of it" (acs/structure 5); "a
  single arity structure is always a type of graph" (acs/structure 3).
  Complements: "the most formal ternary structure connects a few elements from each of three arities"
  (acs/structure 6) gains its name; "node-cluster is a connector with graph rules on its primary arity"
  (acs/structure 6) stays a kind of the connector, not a form.
- **9.** Verbatim: "Each request is a link without direction, for directed graphs, first points to the last."
  *(2026-10-24)*
  Urgent: a requirement of the structure requests in acse (root 41).
  Commons: "Clusters are symmetric by nature, and direction is a restriction on them" (acs/structure/binary
  2).
  Conflicts: "A unary structure is a directed graph on one arity's identities" (acs 61), and "unary stays
  directed at 0 with symmetric as its restriction" (acs/structure/binary 2): a link without direction
  makes every graph symmetric at 0.
  Complements: the graph contract's `addParent(element, parent)` gives its last, `parent`, as the start; the
  next entry keeps it beside new methods that follow this one.
- **10.** Verbatim: "Keep the former methods, but we will make new methods (with a name naming approach) that suit
  my new claims. I am open to suggestions about the naming." *(2026-10-24)*
  Urgent: a requirement of the structure requests in acse (root 41).
  Complements: the two entries above, which the new methods follow.

## Journal and undo

### Details

- **4.** "addParent" under isTight cannot be inverted by undoing all in reversed order; it can be reverted
  by undoing all in the same order; also, it can be undone by closing the isTight temporarily.

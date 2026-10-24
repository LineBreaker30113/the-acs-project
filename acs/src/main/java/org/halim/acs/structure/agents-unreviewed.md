# acs/structure: entries to review

# Unreviewed

- Verbatim: "Remove the "ORDERED" bit [of the connector and of-by-to contracts], the structures meant to hold
  the structure only, ORDERED is not related structure but how it is stored." *(2026-10-24)*
  Commons: "we are recording only relations" (acs 47); "Structures are responsible for representing the
  data optimally" (acs 18): an order is the storage's, not the relation's.
  Complements: the step `ACS_Actor` describes, whose order "doesn't matter", holds for every structure
  once no list keeps places.
  Section: Architecture
- Verbatim: "I decided the most formal forms are not clusters. So for unary it is [the] graph, for binary it is
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
  Section: Architecture / Details
- Verbatim: "Each request is a link without direction, for directed graphs, first points to the last."
  *(2026-10-24)*
  Urgent: a requirement of the structure requests in acse (root 41).
  Commons: "Clusters are symmetric by nature, and direction is a restriction on them" (acs/structure/binary
  2).
  Conflicts: "A unary structure is a directed graph on one arity's identities" (acs 61), and "unary stays
  directed at 0 with symmetric as its restriction" (acs/structure/binary 2): a link without direction
  makes every graph symmetric at 0.
  Complements: the graph contract's `addParent(element, parent)` gives its last, `parent`, as the start; the
  next entry keeps it beside new methods that follow this one.
  Section: Architecture / Details
- Verbatim: "Keep the former methods, but we will make new methods (with a name naming approach) that suit
  my new claims. I am open to suggestions about the naming." *(2026-10-24)*
  Urgent: a requirement of the structure requests in acse (root 41).
  Complements: the two entries above, which the new methods follow.
  Section: Architecture / Details
- Verbatim: "(b) [a method on the structure base gives the arities it causes, so the core can refuse a
  client's capture there], for each arity let's keep boolean flag for if it is caused or not."
  *(2026-10-24)*
  Meaning: the structure answers, per arity it spans, whether it causes it; read as strictly caused, the
  structure having made it, since only there the core refuses a client's capture.
  Complements: the base's javadoc, an arity caused but not strictly, or strictly caused; "the calculated
  arities must be dependent on a structure" (acs 28).
  Section: Architecture

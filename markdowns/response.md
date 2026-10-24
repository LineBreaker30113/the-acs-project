# `ACS_GraphComponentBase` — 11 of the walk

Read 2026-10-24, working tree, including your uncommitted edits. "(new)" means an entry recorded today
and still unreviewed.

## What it is for

- A one-arity structure is always a graph (structure 3).
- Node-cluster is a connector with graph rules on its primary arity, and node-edge is one of its
  kinds (structure 6).
- Clusters are symmetric by nature, and direction is a restriction on them (binary 2). Your new edit
  extends that to every graph kind: symmetric at 0, and `DIRECTED` is a bit.
- Under isTight, undoing `addParent` works in order, not in reverse (structure 4).

## Duties (`acs/src/main/java/org/halim/acs/structure/ACS_GraphComponentBase.java`)

| Duty | Members |
|---|---|
| Kind | `getRestrictions()`: bits from `1 << 8` up, above a connector's. `NO_LOOPS`, `ACYCLIC`, `AT_MOST_ONE_FROM`, `AT_MOST_ONE_TO`, `ROOTED` (element 0, a fallback, not an edge), `NO_IMPLIED_EDGES`, `FULLY_REACHABLE`, `ALL_CONNECTED`, `DIRECTED`; one `is…` per bit |
| Edits | `addParent`, `removeParent`, `setParent` (one change, never refused for a second parent) |
| Reads | `parentCount`, `copyParentsInto`, `childCount`, `copyChildrenInto`, `reaches` |
| Analyses, defaults over the reads | ancestors, descendants, `depth`, nearest common ancestors and descendants (join and meet), `distance`, `pathLength`; over a set: topological order, `hasCycle`, roots, leaves, parts, `connectivity`, `reachesAll`, `connectsAll` |

- **acse:** only the node-edge storages implement it, through `NodeEdgeGraphBase`, and they do not
  compile yet (`breakEdges`, `ensureConnection`). No unary class implements it.
- **Callers:** none outside the contract.

## Where code and decisions part

1. **Directed or symmetric at 0.**
   - Decision: "A unary structure is a directed graph on one arity's identities, each kind a
     restriction of it" (acs 61).
   - Your edit: "symmetric in every kind, since clusters are symmetric by nature … until `DIRECTED`".
   - The code is newer.
2. **`NO_PARALLEL_EDGES = 1 << 18` is defined twice,** in the unary and the node-edge contracts, with
   one meaning. It reads as a graph bit.
3. **The analyses cannot be requests.** They take an `IdentitiesSet`, which the request grammar lacks,
   and request marks now sit on the contract (new).
4. **The root under iui.** `ROOTED` says "element 0 is the root". The client sees only iui (new), and
   iui 0 is whatever the lowest live identity is. Nothing says whether the root can be removed.

## Open

1. **acs 61.** (a) Your edit replaces "directed" with "symmetric until `DIRECTED`". (b) Keep acs 61,
   and the edit changes back.
2. **`NO_PARALLEL_EDGES`.** (a) Move it to the graph contract. (b) Keep one per kind.
3. **Analyses by request.**
   - (a) They take an `int[]` of iui in place of the set.
   - (b) A request form runs over the whole arity only.
   - (c) They stay Java-only.
4. **The root.** (a) It is imv 0, which cannot be removed while `ROOTED`. (b) It is iui 0, the lowest
   live identity.

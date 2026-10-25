# Mathematical Theorems of ACS Structure Contracts

These theorems formalize the mathematical foundation of structures in the ACS architecture, connecting abstract mathematics to engineering benefits for the ACS ecosystem and the ACS-alHikmet paragraph knowledge engine.

---

### Theorem 1: The Fundamental Theorem of Structures
> **"Structures always and only connect some elements to one."**

- A structure is an asymmetric incidence mapping from a single target element ("the one") to a set of related source elements ("the some"): $x \mapsto \{y_1, y_2, \dots, y_k\}$.
- **Corollaries**:
  - *Unary ($n=1$)*: In a DAG / SetHierarchy, each node $x$ maps to its set of parents (some elements to one).
  - *Unary clusters prohibition*: Clusters (hyperedges connecting $>2$ elements without a target identity) cannot exist in a unary structure. Grouping elements into a unit imparts identity and state to the relation itself; by the ACS entity axiom (acs 61), any relation carrying data or independent identity is a separate entity belonging to another arity.
  - *Binary ($n=2$)*: The connector (elements-clusters) maps an element in arity 1 ("the one") to a subset of elements in arity 0 ("the some").

---

### Theorem 2: Arity Reification
> **"An $(n+1)$-ary structure makes one arity representative of structure units in an $n$-ary structure for the rest."**

- Structural units of an $n$-ary structure (which cannot hold internal data without violating the entity axiom) are promoted to first-class identities in the $(n+1)$-th arity.
- **Corollaries**:
  - *1 to 2 (Unary to Binary)*: The anonymous edges of a unary graph on arity $A$ become identities in arity $B$. In node-edge, each identity in $B$ represents an edge unit connecting nodes in $A$.
  - *2 to 3 (Binary to Ternary)*: The connections of a binary connector across $(A, B)$ become identities in arity $C$. In primary-relation-complement / from-via-to, each identity in $C$ represents a transition unit linking $A$ and $B$.
  - *3 to 4 (Ternary to Quaternary)*: Ternary relation tuples across $(A, B, C)$ become identities in arity $D$ (e.g., provenance, context, or assertion units over triples).

---

### Theorem 3: Canonical Formal Forms
> **"The most formal version of an $(n+1)$-ary structure connects elements of one arity to the relations of an $n$-ary structure over the other arities."**

- The canonical hierarchy is defined inductively by $A_{n+1} \to \mathcal{R}_n(A_1, \dots, A_n)$:
  - **0-ary**: The arity itself (connects elements across zero external arities; refined later).
  - **1-ary (Unary)**: Graph (`linkUnary`) — internal relations over a single arity.
  - **2-ary (Binary)**: Connector (`linkBinary`, elements-clusters) — identities in $B$ indexing relations over $A$.
  - **3-ary (Ternary)**: Primary-relation-complement (`linkTernary`) — identities in $C$ indexing relations over $A \times B$.
  - **4-ary (Quaternary)**: Context-relation-incidence (`linkQuaternary`) — identities in $D$ indexing ternary relations over $A \times B \times C$.
- Higher arities eliminate combinatorial hypercube explosions by nesting relations rather than maintaining flat $k$-dimensional matrices.

---

### Theorem 4: Scope Boundary
> **"Structures strictly work on only the elements of arities."**

- Current contracts operate exclusively over primitive `int` element identities within arity sets.
- Component-level structures (structures operating over core components, actors, or procedures) are explicitly out of scope for the MVP and deferred to future work.

---

### Theorem 5: Specialization by Restriction
> **"Restrictions on formal structures yield specialized structures that are semantically precise and resource-efficient."**

- **Semantic precision**: Enforcing constraints (directed, acyclic, trees, sequences) eliminates ambiguities so higher-level algorithms and procedures operate without defensive runtime checks.
- **Implementation efficiency**: Narrowing possibilities removes storage overhead and allows domain assumptions:
  - An unconstrained graph of $2^{13}$ nodes requires up to $2^{26}$ bits ($N \times N$ matrix).
  - A forest/tree restriction guarantees at most one parent per node, reducing storage to a flat array $O(N)$ of
    integer array ($2^{13}$ entries each about few integers of size yielding a total of $O(n)$) with $O(log(n))$ lookups.

---

### Theorem 6: Structural Causation
> **"An $(n+1)$-ary structure can cause its representative arity, and recursive causation yields structures where every arity is caused."**

- Because the $(n+1)$-th arity reifies the relations of the lower structure, its identities can be derived and managed entirely by the structure without external client capture.
- Repeating this derivation yields fully caused systems (e.g. concept lattices, product spaces).
- Calculated tables for caused arities are out of scope for now and will be planned later; contracts currently expose whether a spanned arity is caused (`isCaused`) to refuse external client capture.

---

### Theorem 7: Structural Containment and Combination Interfaces
> **"An $(n+x)$-ary structure contains $n$-ary structures and can enforce their rules over subsets of arities."**

- An $(n+1)$-ary structure embeds the lower-order relation space over its constituent arities.
- **Combination interfaces**: Specialized interfaces where an $(n+x)$-ary structure enforces the invariants of an $n$-ary structure on a subset of its arities:
  - *Node-cluster*: A binary connector enforcing unary graph invariants (e.g. acyclicity) on its primary arity.
  - *Node-edge*: A node-cluster restricted so every cluster/edge connects exactly two nodes.
  - *Ternary combination*: A ternary structure enforcing binary connector or unary graph rules on its primary, relation, or complement arities.
  - *Quaternary combination*: A 4-ary structure enforcing ternary relation or graph constraints on participating arities.

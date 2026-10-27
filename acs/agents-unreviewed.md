# acs: entries to review

# Unreviewed
- Verbatim: "in the ACS contracts we use the "int getClassCount(); Class getClass(int index);" format
  for avoiding object overhead." *(2026-10-25)*
  Commons: "Objects cost too much on 2^25 or more records" (acs 65); `ACS_DescriptionManager`'s
  `getClassNameCount` and `getClassName(int index)`. No conflict found in the summaries.
  Section: Architecture

- Verbatim: There will be logical filters, structural (like `descendantOf`), data based (like
  `greaterThan`). It must result in all the identities that satisfy the claim one way. *(2026-10-26)*
  Meaning: the filters come in three families, and a filter's result is the set of all identities
  satisfying its claim in at least one way, as a DAG identity with several parents is a `descendantOf`
  if any path leads to it; the engine ships the structural and data-based ones, not only the logical.
  Commons: (acs 57) logical combinations, and data or structure filters like "childOf"; (acs 4) the core
  handles filtering.
  Complements: (acs 59, 60) the OR variants stay redundant boilerplate, last.
  Conflicts: (acs 58) filters on data depend on the client, and only the logical operators exist right now.
  Section: Filtering

- Verbatim: Yes, uses are right; must not: no implementation in acs. ACS was being cleared, and it is
  clear enough. We just need to add procedure and filtering, which is not necessary right now.
  *(2026-10-27)*
  Meaning: acs uses only the JDK, fastutil and Roaring, holds no implementation, and is clear enough;
  procedure and filtering come later.
  Commons: (root 28) ACS is only a mathematical entity.
  Labels not checked against every folder's CLAUDE.md.
  Section: Architecture

- Verbatim: The mathematics represented as interfaces for database implementation of the Polyadic Formal
  Concept Analysis. Check the studyTopic.md [`acs/structure/studyTopic.md`]. The Arity is the table of
  records of the database or table of structure nodes to build a structure above and reason about the
  records. The CatalogRow is not an actor of the ACS itself as it meant to be only a section of Arity,
  not an object on its own, but it is an actor of the database since it can have hyper parameters, for
  now we believe those hyper parameters are not mutable and do not need to be, so it won't be an actor,
  but its constructor requires a DIS to read its hyper parameters. The Structure is a list of relation
  among tables of any numbers, they can cause table as a reasoning product, check the theorems.md
  [`acs/structure/theorems.md`]. They may have restrictions. The Procedure is a well defined action on
  the database, defined so clear automatically be reasoned about. The Database ("persistence"
  subpackage, rename to "database") is the representation of the mathematics on the computer, must have
  a persistence, management (core), journal, and optionally a descriptor (for agents). It must not
  implement the interfaces. *(2026-10-27)*
  Meaning: acs's purpose and its five parts; the `persistence` subpackage is renamed `database`, not
  applied yet; acs holds no implementation of its interfaces.
  Commons: (root 9, 54, 56) arity, structure, procedure; (root 28) ACS is only a mathematical entity.
  Labels not checked against every folder's CLAUDE.md.
  Section: Architecture

# Questions
- **Which data-dependent filters does the engine ship?**
  - Claude: structural leaves — catalog comparisons (`eq`, `lt`, `between`, …) and reachability
    (`childOf`, `descendantOf`, …). Interpreting leaves (`namedLike`) stay the client's.
  - Author: We can ship many leaves, the "equals" for integer like ones or "near" for floating types are cool. Is there a concern with making "String" filtering ways provided by the engine?
  Section: Filtering
  Answer: I plan on making the filter, which will help the procedure instances, which will also have regex like things to process strings. *(2026-10-27)*
  Answer: About the filter, it is reasoning, the procedures can use and create filters, and they will come with many base instances like: logical (AND, OR), numerical (lessThan), structural (descendantOf). *(2026-10-27)*
  Answer: So filters are three types: logical, arity (last response numerical), structure. But the arity based ones being able accept mathematical functions would been nice. [The function takes] one catalog value. *(2026-10-27)*

- **How to restructure AND / OR / NOT trees for the fastest evaluation?**
  - Author: "Say we give the client the "AND", "OR", "NOT": can we find a way to better restructure
    those claims for the fastest computation just by looking at them, other than the
    "isEnumeratePreferred" workaround in the legacy?"
  - Claude: normalise, plan, execute — push NOT to the leaves, flatten to n-ary, merge ranges on the
    same catalog, order by estimated size and cost, push candidate sets down (needs gather).
  - Author: Your suggestions are cool, please better define them as plan, since they are not urgent.
    I do think we can make few things, like if there is a "Not" inside an "And" the other leave can be enumerated then the ones satisfying not are removed.
    But that is a topic with a lot of things that can be done. One of most important is that we need to make a temporal "isEnumeratePrefered" method that calculates it using recursion on each filter node. That was the former design in the legace, read and bring it to me for us to discuss.
  Section: Filtering

- **Old plan, author declines: bulk over per-element everywhere; structures must also support range
  reads.** (!(What does it even mean?)!)
  - Author: Keep this, out of scope, but if there will be a bulk function there needs to be a reason.
  Section: Architecture

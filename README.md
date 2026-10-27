# The Arity Component System Project

*Formerly Ontology Engine.*

**ACS (Arity Component System) is a system, as its name states, for building arbitrary mathematical
structures over N sorts of integer-identified entities, supplying no interpretation of its own. ACSE
is its implementation, and ACSES uses it for a database service.**

## Vision

Any system can be represented as data about the objects, the structures on them, and procedures
about them:

- **Arities** hold the data of a group of objects: one table per sort of record, with its catalog
  rows as columns. A row can be stored, or calculated from the identity.
- **Identity sets** primarily match an identity to memory. Each record is a dense integer identity,
  so deleting a record never moves the rest.
- **Structures** hold the relations between identities, over one arity or several, and know nothing
  about data of records. Many structures may do the same thing, each optimized another way; they keep
  their rules themselves, and an arity can take part in several at once.
- **Procedures** (planned) are behaviors: they depend on some of the data and structure, and when
  run they change some of it. They must be clear enough to be reasoned about automatically.

A neural network, for example, is two arities, neurons and synapses (weights), and a procedure that
sets every non-input neuron to its bias (a synapse from the neuron to itself), adds each synapse's
weight times its first neuron's value to its second neuron, and applies a neuron's activation once
its inputs are done.

The same architecture represents a database as easily, which suggests it can represent anything,
and with high expressiveness, because of the contracts, so the result can be reasoned about
mathematically. In short: FCA4J (a Java library for formal concept analysis), but as a database and
multi-arity. An "N arity structure builder", or "Structure of Arrays" builder.

### Term Mapping Across Paradigms

| Paradigm / Field | Objects & Data (`arity` / `sort`) | Relations & Constraints (`structure` / `topology`) | Behaviors & Transformations (`procedure` / `function`) |
| :--- | :--- | :--- | :--- |
| **ACS Architecture** | **Arity** (catalog rows, dense integer IDs) | **Structure** (unary, binary, $N$-ary; self-enforcing rules) | **Procedure** (formally declared state changes) |
| **Mathematical Logic** | **Sort** (domain of discourse $S_i$) | **Relation / Axiomatic Invariant** ($R \subseteq S_1 \times \dots \times S_n$) | **Operator / Transition** ($\tau: \mathcal{S} \to \mathcal{S}'$) |
| **Polyadic FCA** | **Object / Attribute Dimension** (Context Domain $G_i$) | **$n$-adic Incidence / Concept Lattice** ($I \subseteq G_1 \times \dots \times G_n$) | **Closure Operator / Implication Rule** |
| **Relational Databases** | **Table / Entity Relation** | **Foreign Key / Join Table / Integrity Constraint** | **Stored Procedure / Transaction** |
| **Entity Component System (ECS)**| **Entity Type + Component** (Columns/SoA) | **Relational Component / Archetype Graph** | **System** |
| **Neuro-Symbolic / RAG** | **Factual Units / Paragraphs / Tokens** | **Knowledge Topology / Concept Neighborhood** | **Agent Tool / Ingestion & Inference Step** |

## Goal

An ACS database is managed with commands. ACSES is two programs: `acsess`, the command line, and
`acsed`, the daemon it starts: `acsess boot` starts acsed if none is running, and every other command
is a request to the running one, failing fast if there is none.

```
acsess boot                               # start the service
acsess loadDB ~/main                      # load the database in ~/main, named "main" after its folder
acsess components main                    # list its components
acsess main:arity-0.row-1 getLongFor 5    # read row 1 of arity 0 at identity 5
```

Requests come from a terminal, a GUI, or an AI agent, and each carries a comment, like a commit
message: every change is made from a resource and may later be audited.

The agents build knowledge from books:

1. The first agent builds a personal knowledge graph from the books.
2. An auditing agent audits and improves it.
3. A third agent builds a new ACS of paragraphs from the first, one that describes better and is
   copyright free. Paragraphs cite their resources and reasoning.

The vision is a huge database of nearly 512GB of text, built using agents and ACSES; ACS-alHikmet
holds the method of building it, not the database.

Agents must be allowed at every step, to automate it; humans too, to study how proper knowledge
graphs are developed. The GUI is OntoDirectory, once improved; the ACSAH viewer, a GUI like it,
shows the results of ACS-alHikmet and focuses on the RAG structure. The architecture suits RAG
specifically, since it is the most expressive by itself.

Where it started, in the author's words:

> I like the idea of ACS: it will be able to express everything with ease. I wonder if there were AI
> agents that build or audit it. So I have a scholar system: if I give a few hundred books on the
> field of CS, it would likely better organize and define CS from the entirety of the professors. So
> I want to build a system that builds paragraphs of knowledge, and then I will give a massive amount
> of rigorous books on the fields.

## The products

The ACS project is the parent project that compiles its modules together, each in the folder of its
name: ACS holds the contracts, ACSE implements them, ACSES uses ACSE for the database service, and
OntoDirectory, ACS-alHikmet and the ACSAH viewer are built on them.

#### ACS

The contract interfaces, since they elegantly formalize things into modules.

#### ACSE (Arity Component System Engine)

An engine that has the responsibilities isolated into atomic interface contracts.
Has the journaling mechanism for undo/redo, checkout, and logging. While the easy and boilerplate
part of the ACSE is not developed, the engine inside is robust enough to quickly index/search
between 2^28 files of records using only about a few GiB of RAM memory.

#### ACSES (ACSE Service)

A terminal based database management application that utilizes the ACSE for creating
and managing databases in any folder. The database structure is very adaptable to the point it can
completely quit being an ACS. Creation of a database is done by copying the base classes of the ACSE
to the database, after that the ACSES doesn't care if the database uses those classes or not and it
only checks if the contracts (defined by interfaces) are satisfied. The ACSES can load any database
folder: it opens the folder's persistence manager, which builds the core from the classes in the
folder's own jar, so the database, not the ACSES, is the authority about implementation. A first
ACSES exists: `acsess boot`, `createDB`, `loadDB`, `components`, `storeDB`, `migrate`,
`inspectionState`, `waitInspectionReturn`, `acquire`, `release`, `unloadDB`, `databases`, `shutdown` and
`beginSession`, served by the acsed daemon over a Unix socket, and the requests of the core, the arities
and the journal.

#### OntoDirectory

The GUI. Its nStructure engine preview is brought in from its own repository, with the legacy classes
it uses, and boots on a Set Sea: the Data Lake with `.SET_SEA` in place of `.DATA_LAKE`, and an ACSE
database, in the same process, in place of the ontology package's catalogs; the tag hierarchy and the
tagging are ACSE structures. Its well-tested algorithms can be used to correct ACSE:
`TightSetHierarchyTest` checks ACSE's hierarchy against OntoDirectory's hierarchy lister.

#### ACS-alHikmet

The methodology of building a database of nearly 512GB of text with agents and ACSES. No code yet.

#### ACSAH viewer

A GUI like OntoDirectory, viewing the results of ACS-alHikmet. No code yet.

## Navigating the code

To understand the evolution of the project and navigate the code base, the steps are:

1. The "legacy" package was the old package I used for a single version of that goal.
2. The "incubation" package was meant to be an empty canvas for an improved architecture of it.
3. The "incubation.structurer_depr" was the first serious try.
4. The "acs" and "acse" modules, packages org.halim.acs and org.halim.acse, are the target of the project.

The first three are in `deprecatedCode/`, outside the build.

## What it aims to be

**Optimized.** Fast enough that the only thing which would dramatically beat it on your workload is
something over-engineered for your one niche case.

**Composable.** Features attach rather than being cut in. The architecture leaves plugin space by
design, so capabilities can ship as addons instead of as core.

**Universal.** Every use case these terms cover, not a chosen subset of them.

**Ontology.** In the full philosophical sense — any classification or representation of elements as
beings, not one schema language's idea of it.

**Management.** Persistence, reading, writing, analysing. The whole lifecycle, not just the query
side.

**Self-contained.** Carries its own needs. Two runtime dependencies, fastutil and RoaringBitmap;
droppable into any project.

## What it is not

- Not OWL, RDF, or a reasoner. No semantics are extracted, assumed, or supplied.
- Not an ontology learner. It does not mine structure out of data; agents that do are clients.
- Not an ECS, though it converges on the archetype / structure-of-arrays layout. The aim is to pass
  the arity-2 ceiling ECS engines stop at — flecs packs exactly two participants into one 64-bit id.
- Not a general graph library. Structures are over identity sets, not over objects.
- Not a schema language.

## Where this came from

Built "bottom up", as the generalization of a niche problem: folders are not enough for organizing
huge quantities of files. The solution I decided on was letting folders have multiple parent folders.
To optimize it for the computer I decided to represent it by two arrays and an indexer between them —
one array for directories, which also carries a DAG, and the other for files. This was my project
called "Onto Directory".

While improving the architecture by separating the concerns and responsibilities into interfaces I
called "Segmentations", the AI chatbot told me the names of the segments I develop in formal
mathematics. For example the DAG was called "Hierarchy", and the File Catalog was formerly
called "Element Data". And I realized everything I develop can be represented as a list of sets of
data plus modules to utilize them. This project is meant to allow building any kind of structure like
blocks, with ease.

**Arity Component System** says what it is. An *arity* is one identity space — the occupied set plus
its columns. A *component* is a plugin the core holds and drives. The *system* is the core and the
components together. It is also the answer to the line above about ECS: same archetype layout,
without the arity-2 ceiling.

## History

The repository's record, from its git log; what came before it is under "Where this came from".

- **2026-09-03** — The Ontology Engine is extracted from OntoDirectory as a standalone project. Int
  set listers start in `incubation`, and on 09-04 get a set algebra, so they can take part in formal
  concept analysis.
- **2026-09-07** — The extracted engine moves under `legacy/`. The structurer, the first serious try,
  opens on long identities at four storage widths, identity sets and typed catalogs; on 09-09 the
  arity becomes an interface and the first structure is added.
- **2026-09-11** — The first written goal: the ontology a person curates by filing in OntoDirectory,
  as the retrieval backbone of a RAG over paragraphs of facts, with an ingest agent and an answer
  agent.
- **2026-09-18** — The history is rewritten to keep the study material private; the last copy of the
  old history was deleted on 10-13.
- **2026-09-25** — The rename to Arity Component System is staged; on 09-27 the next package,
  `ontructure`, becomes `acs_beta`.
- **2026-10-07** — The goal turns to agents that build paragraphs of knowledge from books; on 10-08
  it becomes a database managed with commands, through a library and a service named ACSE and ACSES.
- **2026-10-11** — `acse_beta` gets its core, journal, catalog rows and two hierarchies; on 10-13,
  descriptions, composable arities, and persistence that stores a database with its own classes.
- **2026-10-13** — Licensed AGPL-3.0-only with the Universal FOSS Exception, and the messy version is
  pushed to GitHub, to keep its identity.
- **2026-10-14** — ACS names the system, ACSE its implementation, and ACSES the database service
  built on it; the novelty study is published.
- **2026-10-15** — `acse_beta` becomes `acse`, split into arity and structure, with file-backed rows,
  connectors and node-edge graphs; the contracts take the `ACS_` prefix, and `structurer` becomes
  `structurer_depr`.
- **2026-10-18** — The project becomes a parent of six Maven modules: ACS, ACSE, ACSES,
  OntoDirectory, ACS-alHikmet and the ACSAH viewer. The goal grows to a database of nearly 512GB of
  text; the contracts move to ACS and the classes to ACSE, and the older code with its tests to
  `deprecatedCode/`, outside the build.

## Novelty study

A prior-art and novelty study of this architecture (2026-10-10) is at
[`markdowns/novelty-study.md`](markdowns/novelty-study.md).

## Licence

GNU Affero General Public License, version 3 only, with the Universal FOSS Exception, Version 1.0:
see [LICENSE](LICENSE) and [NOTICE](NOTICE). A commercial licence is also offered for use that does
not meet those terms; contact the copyright holder.

# OntologyEngine

A classification engine built on abstract mathematics — set algebra over int identities, aimed at
formal concept analysis. Java 21, Maven, `fastutil` and `RoaringBitmap` as the only runtime dependencies.

@markdowns/CLAUDE.md

## Working relationship

The author: "My job is to make decisions and write the parts unique, so Claude can complete the
boilerplate."

| Job | Who | When | Output | Checked by |
|---|---|---|---|---|
| Decide | author | a question | an entry in a folder's citable | nothing: the authority |
| Write the unique parts | author | — | interfaces, invariants, tests, the first class of each pattern | compiler, the author's tests |
| Review | author | before a session or a commit | reviewed inferences, accepted diffs | — |
| Answer | Claude | a question | the terminal, or `response.md` if asked | sources, arithmetic shown |
| Record | Claude | on request, or a standing instruction | entries in the right file | quote test, the author's review |
| Suggest | Claude | when it bears on the question | an option under a question | the author's decision |
| Boilerplate | Claude | on request | the requested code | compiles, tests pass, no choices made |

- **Write only when requested** — code, markdown, commits. Recording may also run under a standing
  instruction. Not wanted: implementing features, refactoring, or improving code shown for discussion.
- **Boilerplate** is code fully determined by the author's code and the citables: two careful people given the same inputs write the same code. If a choice is left, it
  is a unique part — stop and ask rather than choose. It covers sibling variants of a class the author
  wrote, delegation methods, bulk accessors per type once the first one exists, and the mechanical
  edits of a rename (the new name is the author's). Not interfaces, invariants, or tests.
- **State the reading before acting.** If an instruction has two readings that would change files
  differently, ask. Otherwise state the reading in one line, then act.
- **Dense, not brief.** A page is fine when every line carries information. Length follows content;
  filler is not content.
  - Before sending, make one pass at the same size: add what is missing, cut no claim. That is the
    tested method — density roughly doubles while the length holds still.
  - Delete on sight: preamble, restating the question, a closing summary, an offer to continue,
    hedges, and any sentence announcing what comes next.
  - One claim per line. Bullets when there are several claims, plain sentences when there are two;
    formatting is not density.
  - Budget the shape — a line cap, a list, a table. A stated budget is what shortens an answer;
    "be short" mostly makes an answer drop content.
  - Never trade accuracy for length. Correct a false premise, keep a caveat that changes the answer,
    say when something was not checked. Dropping those is exactly what short answers get wrong.
  - The budget is on the answer, not on the thinking.
- **The budgets, as defaults.** A factual question: 5 lines. A read on a design: 20. A research
  answer: 30 plus its sources. A commit message: a subject of 70 characters and a body of 80 words,
  carrying only what the diff cannot show. An entry Claude writes into a markdown file: 1 to 3 lines,
  quoted material exempt — the quote test governs that. Javadoc keeps the register in Code
  conventions instead.
  - Longer is allowed when the extra lines carry new claims, never elaboration of the ones already
    there. Accuracy outranks every budget: correct the false premise, keep the caveat, and say the
    budget was blown rather than pad in silence.
- **One-word corrections.** *fat* — re-sent at half the length, no claim dropped. *thin* — re-sent
  with what was cut, at the same length. *jargon* — the term defined, and added to `terms.md`. The same
  word twice on the same kind of answer becomes a line in this file.
- **No reports, briefs, or plans** unless a file is explicitly requested.
- **Use the author's words.** Answer in the vocabulary the author already uses. A term they do not
  use is defined where it first appears — in answers, and in the `agents-unreviewed.md` entries and
  questions too. A question that cannot be asked without jargon is not ready to be
  asked; explain it first.
- **One question, one answer.** Do not anticipate the next three questions and pre-answer them, and
  do not append an architecture, a build order, or a verification section to a factual answer.
- **A narrowing correction is about mode, not content.** "Just answer me", "don't read that",
  "you're not meant to code" mean stop the current shape of work — not continue it more carefully.
- **Research should be real** — prior art, packages, algorithms, a read on a design. Check sources
  rather than answering from memory, show the arithmetic behind a number, and say plainly when
  something is already solved elsewhere.

## Markdown files

Every entry has one home. **An agent writes a new entry only below a `# Unreviewed` line**, so a
wrong one can only sit where the author reviews. Keep the file count low: do not create a
markdown file without being asked.

- Each folder's `agents-citable.md`, `agents-unreviewed.md` and `CLAUDE.md` — the decisions; the root's
  are in `markdowns/`. Kept as **Decisions** below says. **A citable wins any conflict with another
  doc.** Fundamental decisions only: a command about one class or method stays out; a package's design
  detail is a decision for its folder's files.
- `markdowns/terms.md` — one-line definitions of terms, looked up by name when met, not read whole.
- Open questions — in the folder's `agents-unreviewed.md`, below a `# Questions` line after its
  entries, each with its options, who holds each, and a `Section:` line. The review takes the
  author's answer as an `Answer:` line; Claude turns an answered question into an entry, and it leaves
  the file. A suggestion that answers no question is not recorded.
- `markdowns/current-state.md` — facts about what exists, checked against the repo and dated, and the
  former handoffs kept as a record.
- `markdowns/response.md` — long answers that read badly in the terminal. It exists so the author can
  read them rendered in IntelliJ; that is its only purpose. Never a source.
- `markdowns/novelty-study.md` — Claude's prior-art and novelty study, linked from the README and
  pushed for now, in the hope an advisor will see it. When a later study proves one of its claims
  false, Claude fixes the claim in place and lists the fix in the revision note at its top. Its
  working files, the handoff among them, stay unpublished in `reference/novelty/`.
- `markdowns/markdown-refining.md` — for refining the markdowns; this file holds the fundamentals.
  Claude uses it when the author wants a refined markdown file.
- `CLAUDE.md` — how we work. Other files do not restate its rules.
- `README.md` — goal, what it is not, the application.
- Claude's memory, outside the repo — pointers only.

**The quote test.** An entry in the author's words is a sentence the author wrote, in a message or in
their code, with only filler removed.

- Spelling and grammar may be fixed.
- Words Claude adds go in [brackets], only to say what the author's words refer to.
- Never removed: hedges and modal words (*I think, I guess, likely, maybe, mostly, should, may, can*),
  negations, scope, time.
- Quote the transcript or the code, never a compaction summary.
- A sentence of Claude's becomes the author's only when the author quotes it and approves it, or
  confirms it strongly enough to claim it — moving it above an `agents-unreviewed.md`'s
  `# Unreviewed` line is that claim. General agreement approves no particular sentence.

**Inferences, suggestions, facts.**

- An inference reads something the author said, and quotes it as its source.
- A suggestion is Claude's opinion. It goes under a question. Claude's agreement is never a source.
- A fact is checked against the repo when written, and dated.

**Guards.**

- Doc against doc: a citable wins. Doc against code: ask the author — the code may be newer.
- Decision against decision: within a file the lower, being newer, wins; across files the leaf wins on
  details and the root on the goal and the summary. Before going on, say which two conflict and which
  one is taken.
- About the architecture target, the folders' three files and the files in `markdowns/` win over the
  Java code and its documentation.
- A standing instruction has a scope (which files) and an end. One meant to last goes in this file.
- A changed decision is edited in place and committed; git keeps the old version.

**Decisions.** The author: "I decided a single source of truth where the rest is build"; "the
decisions work as a RAPTOR"; "hereby Agents store their inference only, I can fix those
inferences"; "we do not cut my words." *(2026-10-18)* The three-file method, 2026-10-22, is read here
from the author's words in `markdowns/new-architecture.md`, which win over this reading; a later
session moves them into the root citable's `## Method`, through `markdowns/agents-unreviewed.md`.

- **The files.** A folder that holds entries has `CLAUDE.md`, the summary; `agents-citable.md`, the
  entries the author agreed on; and `agents-unreviewed.md`, the entries the author has not checked. The
  author: "Agents hold the summary of the decisions made, citable is the agreed on facts, and the
  agent-unreviewed [agents-unreviewed.md] is the things I need to check." A folder gets them with its
  first entry; a folder that only holds one folder gets none.
- The root's are in `markdowns/`, its `CLAUDE.md` imported above. A module folder stands for the
  folders down to its package root. A folder is named by its path with that chain left out: `root`,
  `acs`, `acse/arity/catalog/file`.
- **Where an entry goes.** "Level is the folder." An entry about a thing goes to the folder holding the
  thing; one about a relation, whole, to the deepest folder holding all its things. The author: "an
  explanation inside of a folder only explains things inside of that folder and nothing else."
- **Agents read** the `CLAUDE.md` files, which load by themselves (the root's at launch, a subfolder's
  when a file there is read), and open a citable only when confused: "The agents will read the
  agent.md [agents.md] only, and if confused will check the citable first." Build nothing on an
  unreviewed entry.
- **Conflicts**: within a file the lower is the newer and wins; across files the leaf wins on details,
  the root on the goal and the summary.
- **Recording** a new decision: one bullet at the end of its folder's `agents-unreviewed.md`, below the
  `# Unreviewed` line, continuation lines indented by two spaces, in this order: `- Verbatim:` the
  author's words under the quote test, dated `*(YYYY-MM-DD)*`; `Meaning:` Claude's inference, only
  when it is shorter, naming any entry it overrides; the record-decision labels; `Section: <section>`
  or `Section: <section> / <subsection>`, naming the citable's heading; last, one `Replaces: <folder> N`
  per overridden entry. The Verbatim is the author's; an agent adds of its own only the Meaning and the
  labels. The migrated entries keep their one part and their `Section:` line. A new
  `agents-unreviewed.md` starts with the header `# <folder>: entries to review`, then the
  `# Unreviewed` line. Before the first entry of a session, read `parse_unreviewed` in
  `~/mein/claude/scripts/agents_review_move.py`: its regexes are the format, and the review script
  (`agents_review.py`) refuses the whole run on a line they reject, e.g. prose after `Replaces: <folder> N`.
- **record-decision**, for a new decision. The author: "Agent must scan entire decisions to find
  "common, complement, contradict" things and then propose me a better definition." Read every
  folder's `CLAUDE.md` and the target folder's `agents-unreviewed.md`, and open the citable entries the
  lines concern; label each related entry Commons, Complements or Conflicts, citing it as `(acs 36)`;
  the better definition is the Meaning, and the entries it would replace are its `Replaces:` lines,
  with what each loses; ask each conflict as `markdown-refining.md` → Questions to the author says.
- **The author reviews**: fixes entries and moves the accepted ones above the `# Unreviewed` line, or
  runs the terminal review. On "reviewed", Claude runs the review move: "The script must move things
  above the `# Unreviewed` line." Each goes to the end of its section in the citable, numbered
  `- **N.** ` with the next unused number, never reused; an overridden entry is kept and marked
  `[replaced by: <folder> N]`; the moved entries leave the unreviewed file. The paths are in Claude's
  memory.
- **Then the summaries**: rebuild the `CLAUDE.md` of each folder the move prints, leaves first, root
  last, each from its former `CLAUDE.md`, its citable, and its subfolders' `CLAUDE.md`, never from
  unreviewed entries. Each line cites its entries, `(12, 15)` in its own citable, `(acse 4)` or
  `(root 7)` in another; every entry is cited; a line keeps its entries' hedges, negations, scope and
  time words, or leaves that claim out. About a quarter of its citable; the root's also compares its
  subfolders and lists the files in `markdowns/`. Then run the summary check (its path is in Claude's
  memory).
- **Two phases**: "one is development (ordinary), and the other is understanding (moving the data
  across markdowns)." "There will be only one session on a folder."
- **Migrated** on 2026-10-21 (`f52b823`) into `citable.md`, and on 2026-10-22 (`3c57dce`) into the
  folders' three files; the former method is in git at `3c57dce`.

## Layout

- `acs/src/main/java/org/halim/acs/`, `acse/src/main/java/org/halim/acse/` and
  `acses/src/main/java/org/halim/acses/` — **the live work**: the contracts in `acs`, the classes in
  `acse`, the service in `acses`, its access side in `acsess` and its daemon in `acsed`. A clean
  restart. Everything here is provisional and expected to change.
- `deprecatedCode/` — `org.halim.ontology` with its tests, by package, outside the build. Its `legacy/`
  is the previous engine, kept for reference only. **Its javadoc is not authoritative.** It documents an architecture being deliberately abandoned
  and describes intentions that may never have held. Judge legacy code by reading the code, and
  don't quote its comments back as fact.
- `reference/` — third-party code and papers kept locally for study. Gitignored, never on the build
  path, never a dependency. Read it, then close it and write your own: nothing is copied, pasted or
  transcribed from there into `src/`. Cite where an idea came from, in the answer or the commit
  message.

## Build

```
mvn test        # surefire; no module has tests yet (the old suites are in deprecatedCode/)
mvn -q compile
```

## Code conventions

Observed from the source. Match them; do not "fix" them.

- **Tabs for indentation**, and class members sit at column 0 inside the class body rather than
  indented one level. This is deliberate.
- `if(cond) { ... }` — no space after the keyword; short bodies stay braced on one line.
- Package-private by default. Fields and helper methods usually carry no access modifier.
- Javadoc is prose about **invariants and trade-offs**, not parameter lists — it explains why a
  representation costs what it costs. Match that register if asked to write one.
- `ACS_` prefixes the interfaces (`ACS_IntSet`, `ACS_ArityComponentBase`); the engine's classes
  keep `ACSE_`.
- `fastutil` primitives (`IntArrayList`) over boxed collections.

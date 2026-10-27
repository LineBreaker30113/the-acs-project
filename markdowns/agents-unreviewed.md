# root: entries to review

# Unreviewed
- Verbatim: "I feel like disk space is about 4 times less important than RAM space, what I mean is four
  times disk space is affordable for removing all the RAM consumption. My computer can have at most
  64GiB (right now 32) RAM but 8TiB (right now 2.5) drive, by ratio one GiB of RAM should be able to
  index ('finding precisely', so not just the location on the disk but also all the formal reasoning
  related data like structures) about 128GiB of data, however we do not need that much engineering right
  now since this is PoC, so if 1GiB RAM indexes 4GiB it is enough for me, even 32GiB is huge and can
  mean a lot." *(2026-10-26)*
  Meaning: RAM is the scarcer resource, 8TiB / 64GiB = 128 to 1 on the author's machine at its limit;
  using four times the data's size in disk is accepted to use no RAM (the author, 2026-10-26); the long-term aim is 128GiB of data findable per
  GiB of RAM and the PoC's is 4GiB, findable counting the structures and not only the location.
  Commons: (acs 18) structures represent the data optimally for memory and time; (acs 65) objects cost
  too much on a database of 2^25 or more records. No conflict found in the summaries.
  Section: Target use case

- Verbatim: ACS is an implementation of Polyadic Formal Concept Analysis as a Database Architecture,
  read the table from the readme.md. The ACS Project is utilizing that database for an "One and Only
  Personal Knowledge Base", it is like Operating system for knowledge. The research goal is studying the
  novelty of this project, for a small amount of data, specifically markdowns of 4-5 books about
  programming languages. Outside of the research goal must be able to function for other use cases.
  *(2026-10-27)*
  Commons: (root 2, 7) the knowledge base and its vision.
  Labels not checked against every folder's CLAUDE.md.
  Section: Goal

- Verbatim: ACS: The interface based representations of the mathematical concepts adapted for modular
  database architecture. ACSE: Optimized implementations of contracts from the ACS, for various use
  cases, and must be tested. ACSES: The daemon + requester duo for a CLI, separated because Cyber
  security side is out of scope and I wanted a room for firewall later. ACSESS-GUI: The GUI view of the
  ACSESS, for easily and simply making edits and conceptualizing the project for new comers.
  ACS-alHikmet: The Agents that use the ACSESS for The ACS Project, left to the teammates. ACSAH Viewer:
  The GUI responsible for visualizing The ACS Project, must support analysis and chatbot side.
  OntoDirectory: Where it all started from, this meant to be an improvement on its ontology package,
  now the acs project replaces its engine, but out of scope and will be done later. *(2026-10-27)*
  Complements: (root 21, 22, 68, 71, 73) the submodules; (root 24) no security in the MVP.
  Labels not checked against every folder's CLAUDE.md.
  Section: Layers and names

- Verbatim: Must not: Kill composability for speed or memory. Focus on optimizing details until
  ACSAH-Viewer is complete. Do not push change outside the folder (unless requested manually). Like the
  acsed and acsess binaries being outside this folder. [...] `Don't focus on optimizing ...` contain
  things like redundant checks on inputs (a delegating function will give a proper input, but later
  things can change). [...] I want the project to be contained. *(2026-10-27)*
  Meaning: the project must not trade composability for speed or memory, must not optimize details,
  such as removing redundant input checks, before ACSAH-Viewer is complete, and changes nothing outside
  its folder unless the author asks.
  Commons: (root 41) urgency by being a requirement to other processes.
  Labels not checked against every folder's CLAUDE.md.
  Section: Process

# Questions
- **What exactly is a "paragraph of knowledge", and how does a paragraph cite its sources and
  reasoning?**
  - Author: "We can talk about it later." The three-agent pipeline is under Goal in
    `markdowns/agents-citable.md` (root 4); the source-rows decision is in
    `acs-alhikmet/agents-unreviewed.md`.
  Section: Goal

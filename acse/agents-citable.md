# acse: citable

## Journal and undo

### Details

- **1.** [`storeAll`:] I have a small feeling it is appropriate here, but databases often use a "journal
  before" [write-ahead log] type of action; we can do it later.
- **2.** "storeAll" can be a JSON or something better, you can choose; it [is] meant to store every
  transaction as a log. Just one rule: each transaction is stored [in] its own section, not the list
  each.
- **3.** For the boolean data of the journal we need to use bitset or roaring bitset, since [an] actual boolean
  array costs about 1 byte instead of 1 bit. *(2026-10-12)*
- **4.** [Catalog setters:] it is meant to be logged, and instead of storing [the] former change with the new
  one we can simply store the difference. If the client wants to store former and new versions for
  higher clarity, they can implement the Journal themselves, or we can make a separate Journal
  implementation that stores that way. *(2026-10-13)*
- **5.** Decided to store `new ^ old` for catalog rows that do not change in the number of bits.
  *(2026-10-15)*
- **6.** [The setter shape, and the logging mode for bulk writes:] not open, simply log the change using the
  method you gave [`old ^ new`].
- **7.** We can make the journal's creation independent of the core: `indexer.bin` names the journal's class
  beside the core's, a load builds the journal from that class through the folder's jar as it builds
  the core, and the core takes that journal instead of building `ACSE_JournalBasic` itself. *(Claude's
  wording, confirmed by the author.)*

## Descriptions

### Details

- **8.** Descriptions are few strings, and do not take much space; we can represent them in the easiest way.

## Persistence

### Details

- **9.** [Instead of a no-argument constructor and fields that stop being final:] make a public static
  method, and an indexer.bin that tells each class. *(2026-10-12)*
- **10.** [The numbered folders 000001/, 000002/, …] are cool, but it shouldn't [be] the responsibility of
  this class [the core]; make an "ACSE_PersistenceManager" class that loads or stores in new folders
  each time.
- **11.** [The persistence manager:] it would be so cool if it was able to store all the classes as a jar file
  for easier loading.
- **12.** If space is not concerning, adding the jar for each save for locality of behaviour is a good idea;
  it is what Gradle does.
- **13.** [A class without a static loadFrom:] leave it out, because if it has not a method of saving, likely
  it doesn't need to persist. Just warn about it.
- **14.** [Linking unchanged files between stores:] that is version control, you sure we should do it? I
  wanted to keep the database somewhat simple.
- **15.** A row a request makes keeps its file at the database's root, `arity-<k>-row-<j>.bin`, or
  `arity-<k>-row-<j>plus.bin` for one file of several rows; a store keeps its path relative to the root,
  and a load copies the stored file to the loaded database's root. *(Author, 2026-10-19: "Use a path
  relative to database root, created automatically, calculated by arity and row index like data.";
  "Having a separate file for every type of record is better in general, but we still allowed a single
  file for multi row option, we can append its name "plus" as in `arity-<k>-row-<j>plus.bin`.";
  2026-10-20: "the file of the file row moves to the version on save, so the root also serves as a
  temporal place. Thus copy them to the database root on load.")*

## Architecture

- **16.** A removed component leaves a tombstone rather than shifting the pointers, which also allows a
  compacting load. *(Author, 2026-10-20: "in case of an Component removal, we need to either shift the
  pointers or to keep tombstones. I will go with tombstones, it also allows loading with a compacting.
  Lastly, since in the intended (for the project vision) use case there will be at most 2^20 arities (of
  2^(22 to 28) identities), it is not going to be a huge problem.")*

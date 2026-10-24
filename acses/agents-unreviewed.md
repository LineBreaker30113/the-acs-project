# acses: entries to review

# Unreviewed

- Verbatim: I need you to make the daemon Windows compatible. *(2026-10-23)*
  Complements: "ACSES splits into two packages: `acsed`, the daemon, … and `acsess`, the command line
  tool" (acses 21): the daemon and the tool that boots it. No conflict found in the summaries.
  Section: Layers and names
- Verbatim: [On the weakness Claude named, a client that connects and then stalls freezing acsed:] Fix
  the daemon wait thing, by the requester giving a timeout. [Which requests carry it:] On requests that
  need a timeout. Specifically things like lock and unlock. [On Claude's example of agent A holding the
  key and agent B calling `acsess acquire main timeout=5000`:] The agent A meant to call `acsess acquire
  main timeout=5000`; every request that can be timed out can specify the timeout length, and on boot
  the default timeout length can be set or forbidden. There will be a timeout for all the sensitive
  commands, and the default can be set during the boot or can be forbidden. [The sensitive commands,
  picked among Claude's options:] acquire, waitInspectionReturn, inspectAt. [What forbidding the default
  means, picked among Claude's options:] Must give one [: a sensitive command without `timeout=` is
  refused]. *(2026-10-23 to 2026-10-24)*
  Meaning: A sensitive command - `acquire`, `waitInspectionReturn`, `inspectAt` - can take
  `timeout=<ms>`, how long what it starts lasts: in `acquire main timeout=5000` the key lives 5 s. Boot
  sets the default for one that gives none, or forbids the default, and then such a command is refused.
  Commons: "Make all of the options available as command flags" (in acses 18) and "Switches take the
  shape `+Name` and valued flags `name=value`" (acses 20); "The ACSED will take in parameters when it is
  booted up" (in acses 21), the default being one. Complements: "it ends with `release`, or `release
  +force` without the key" (acses 18) gains a third end; "No, locks are not mandotary, if the data base
  fails mean time the lock better get reaccuired" (in acses 18) gains a lock that ends by itself;
  "`waitInspectionReturn` holds until the return" (acses 16) gains a limit; "the journal refuses a write
  loudly" during an inspection (root 64) gains an inspection that ends by itself. No conflict found in
  the summaries.
  Section: Layers and names / Details

# acs: entries to review

# Unreviewed
- Verbatim: "in the ACS contracts we use the "int getClassCount(); Class getClass(int index);" format
  for avoiding object overhead." *(2026-10-25)*
  Commons: "Objects cost too much on 2^25 or more records" (acs 65); `ACS_DescriptionManager`'s
  `getClassNameCount` and `getClassName(int index)`. No conflict found in the summaries.
  Section: Architecture

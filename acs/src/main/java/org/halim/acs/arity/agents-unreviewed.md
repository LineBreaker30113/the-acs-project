# acs/arity: entries to review

# Unreviewed

# Questions
- **How is a calculated arity's size defined, as for the cartesian arity?**
  - Author: "I think we need to find ways to define arity sizes"; "the calculated arities must be
    dependent on a structure".
  - Claude: by how full the product is. Full and fixed: identity = a·|B| + b, size |A|·|B|. Full and
    growing: a fixed stride for B in place of |B|, holes where B is short. Sparse: mint a pair
    identity only when the pair exists, with a ternary structure holding (pair, a, b).
  Section: Architecture

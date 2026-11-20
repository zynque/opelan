# CT-DESIGN.md — the core, categorically

The minimal core (`CORE-DESIGN.md`) restated in category-theoretic
terms, for readers who find them clarifying.

## One level of the tree: the signature functor

Start from a familiar idea — an algebraic data type. A language's
declarations say "a node of this type has these children". Now look at
just *one level* of a tree: a constructor tag plus some number of
child positions. Mark the positions as holes of type `X` —
placeholders for "whatever fills a child slot" — and each declaration
contributes a shape over `X`:

```
add   ->  X × X        -- two child positions
lit   ->  Leaf         -- none; a leaf
block ->  List(X)      -- any number of child positions
```

A whole language — a signature Σ of declarations — is a choice among
such shapes. Write "choice among" as `+`:

```
F(X) = X × X  +  Leaf  +  List(X)          -- for the signature above
```

`F` is the **signature functor**. "Functor" is the Category Theory name for
a type-level map that, given a type
`X`, produces a type `F(X)` — here, "one level of tree with `X`s in
the child slots" — and lifts any function `X -> Y` to `F(X) -> F(Y)`
by applying it in each slot (that's `.map`). It's called *polynomial*
because it's built only from products, sums, and lists — the same
three type shapes `CORE-DESIGN.md` describes.

## Full trees: the fixed point, a.k.a. the W-type

If `F(X)` is one level, plug it into itself: `F(F(X))` is two levels,
`F(F(F(X)))` three. A complete tree is the type `T` with `T ≅ F(T)` —
one level whose slots hold whole trees — which is exactly the
recursive type you would write by hand for an AST:

```
data Tree = MkTree (F(Tree))
```

Category theory calls this least fixed point `μF` ("mu-F"). Built by
wrapping levels starting from nothing, `μF` comes with a guarantee:
it is the **initial algebra** for `F`, meaning that for *any* way of
collapsing one level (next section) there is exactly one way to
extend it over a whole tree. For general signatures like ours, `μF`
is also called a **W-type** — the type of *well-founded* trees (no
infinite descent) over the signature.

A document's content is a term of `WΣ`. A node's data — literal or
type ref — is the constructor tag: which branch of `F` built it. And
a document is not one term but a term *store*: stable node ids let
subterms be shared, referenced, and edited by identity.

## Folds: how every interpretation works

To consume a recursive ADT, programmers familiar with recursion will
handle each constructor *given the already-computed results of its
children*. A function of that shape — "one level whose child slots
hold finished values" — is an **F-algebra**:

```
A : F(V) -> V

-- e.g.  add(v₁, v₂) -> v₁ + v₂
--       lit(s)      -> s
```

A definition in the core design *is* an algebra clause: its `eval`
field says "given the evaluated children, produce the value", and the
definitions in scope, taken together, are the whole algebra.

Given an algebra, the unique way to extend it over a tree is to walk
bottom-up — recursively fold the children, then apply the clause.
That's the **catamorphism**, better known as a **fold**:

```
fold(A) : WΣ -> V
```

You may have written folds: `List.foldRight`, a `reduce` over an AST. What
initiality adds is that the fold is *the* canonical consumer of a
tree — **every compositional interpretation of a document is a
fold.** The core's "two interpretations of one tree" is then literal:
`eval` is a fold into values, `render` a fold into views. And the
default editor is a *generic* fold, computed from the shape of `F`
alone — rows for products, a choice widget for sums, a list widget
for lists — which is why it needs no authoring. The memo table in
`eval` marks where change propagation makes the fold incremental.

## Sums are sorts, not nodes

A sum type like `Expr = Add | Mul | Lit` contributes no constructor
of its own — it's a **sort**: a name for the set of constructors
allowed to fill a position, the way an interface names the set of
classes that may fill a field. A slot typed `Expr` admits any node
whose constructor's declared result sort is `Expr`.

Membership is open-world: the variant claims the sort, so a new
dialect adds variants without editing the sort's declaration —
closer to a registry than a sealed trait. The dropdown editor asks
the inverse question: which constructors land in `Expr`?

## Derived definitions are algebras-as-data

A primitive binds its algebra clause to host code — like a native
method. A derived definition is a clause encoded in the object
language itself: its meaning term — a tree over Σ — *evaluates* to a
function. So `handler(def)` is a fold that produces a fold, the way
an interpreter written in its own language turns source into
behavior: evaluation is staged, and the fold over instances runs on
algebras obtained by folding definitions.

## Bootstrap is a self-encoding signature

Bootstrap completes when Σ can describe itself — a type language in
which declarations, shapes, meanings, and editor clauses are all
trees over Σ, editable and evaluable by the same folds. A schema
expressed in its own data format. The primitives are the algebra
clauses that can't be trees; everything else is a term.

The deferred features sit outside this picture deliberately: the
store, version pins, history, and sync are structure over the term
*store* — they change which trees exist, never what a tree means.

---

*Further reading for the curious:* the classic paper on folds and
their siblings over recursive data is Meijer, Fokkinga & Paterson,
*Functional Programming with Bananas, Lenses, Envelopes and Barbed
Wire* (1991); the whole field goes by the name **recursion schemes**.

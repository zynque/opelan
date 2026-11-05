# `foundation/view/` — design

**View trees.** A derived view is itself document data: a detached tree
in a minimal presentational vocabulary (`ViewTree.scala`) — `block`,
`flow`, `heading`, `grid`/`row`/`cell`/`hcell` containers, scalar leaves
as text runs, `GapData` as gaps, `input`, an editable text region, and
`styled:role`, an emphasized run whose role names a presentation
(`keyword`, `placeholder`, ...) the interpreter paints.
Languages emit data, never DOM; a single recovering interpreter
([`ui/view/ViewTreeView`](../../ui/view/DESIGN.md)) turns any view tree
into an fp `View`, and unrecognized structure degrades to gap chips
rather than failing. Because a view tree is ordinary document data, a
stored view tree can be inspected or edited like any other document —
and the same vocabulary is what a language document's view spec will
eventually encode.

A `SourceRef` leaf as an element's first child tags it with provenance —
the source node it came from — so "click a rendered value to select its
producer" is uniform machinery, not per-language behavior. `input`
reuses that convention with a stronger meaning: its `SourceRef` names
the node the text box *edits* (the commit path is in
[`../language/DESIGN.md`](../language/DESIGN.md)). An `input` whose
display carries `styled:` runs shows the styled text — a text box can't
hold styled runs, so the styled view is the surface and the box swaps
in on click — while `flatten` drops `styled:` tags, so a styled view still
projects to its plain text.

**`Verbosity.scala`** — the detail-level zoom axis (`Terse`/`Verbose`):
how much of *what* the content says a projection shows. It never selects
implementation — that is a separate axis (see *Zoom navigation* in the
root [`DESIGN.md`](../../DESIGN.md)).

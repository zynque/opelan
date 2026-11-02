# `ui/view/` — design

`ViewTreeView`: the single recovering interpreter that turns any
[view tree](../../foundation/view/DESIGN.md) into an
[`fp`](../fp/DESIGN.md) `View`. Languages emit data, never DOM;
unrecognized structure degrades to hole chips rather than failing.

A `SourceRef` leaf on an element tags it with the node it came from, so
"click a rendered value to select its producer" is uniform machinery —
the click is a *silent select* riding the ordinary `SyncDocument` push,
and `SelectTarget`'s focus flag lets the outline highlight the node
without taking DOM focus from the cell's edit box (see *Provenance
navigation* in the root [`DESIGN.md`](../../DESIGN.md)).

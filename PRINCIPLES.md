# Design Principles

Deeper rationale behind the terse principles in `MANIFESTO.md`. Where the
manifesto states *what* we believe, this document explains *why* — the
motivations, consequences, and tensions each principle carries. Companion to
`ADOPTION.md` (why past workbenches fell short) and `RELATED_WORK.md` (prior
art).

## Languages are applications

Programming language theory has traditionally focused on grammars, parsers,
and compilers. But a developer never interacts with a grammar — they interact
with an application: Notepad, vim, VS Code, or whatever else. The experience
that application provides shapes their productivity, and ultimately their
impression of the language itself. Syntax is a large part of that experience,
but the same syntax in Notepad and in VS Code is a very different language to
work in.

So we design from the user experience inward, not from the grammar outward.
And since a language is always experienced *through* an application,
preference need not stop at the choice of editor: different projections of
the same underlying syntax tree offer a deeper customizability — a choice of
*notation*, not merely of tool.

This principle underwrites several manifesto entries: it is why the platform
is an *Application* first (Available & Responsive, Online/offline), why
*Polysyntactic* treats notation as a user preference rather than a
language-design decision, and why usability — not grammar expressiveness —
is the bar a new language must clear. `ADOPTION.md` makes the same point from
the market's side: the moat is the user experience *around* the language, not
the language definition itself.

## Streaming, reactive, incremental by default

*Reactive* and *Incremental* are promises to the user, and they hold only if
the implementation is built that way from the start. A batch design (collect
everything, then process; recompute the whole on every change; poll for
updates) rarely becomes incremental later: its interfaces take whole inputs
and return whole outputs, and identity is lost at every rebuild, taking
references, selections, and history with it. So the default shape for any
new mechanism is a stream of changes: dependents are notified of deltas,
work is proportional to the change, and unchanged results keep their
identity. The same shape is what *Real Time Collaboration* needs, since a
collaborator's edits arrive as a stream too.

The incremental parser is the template. A full parse still exists, but as
the oracle a property test checks incremental reparses against, not as the
path the user runs. That is also the price: incremental designs carry
bookkeeping (memo tables, examined ranges, dependency tracking) and harder
correctness arguments. We accept it, and pay it down with tests that compare
the incremental result against a from-scratch one.

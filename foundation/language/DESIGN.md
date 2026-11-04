# `foundation/language/` — design

**Languages** (`Language.scala`): a `Language` gives meaning to a type
reference:

```scala
trait Language {
  def typeRef: ExternalNodeReference          // identity
  def name: String
  def views: List[String]                     // derived views, e.g. "print", "eval"
  def defaultView: Option[String]             // first view by default
  def render(view, doc): Either[String, DetachedNode[NodeData]]  // a view tree
  def parse(text): Option[DetachedNode[NodeData]]      // optional text syntax
  def parseInput(text): Option[DetachedNode[NodeData]]  // optional input grammar
  def langDoc: Document[NodeData]             // the document typeRef names
}
```

## Projected input commits

An `input` element in a [view tree](../view/DESIGN.md) carries a
`SourceRef` naming the node the text box *edits*. A commit parses the
text with the language's input grammar (`parseInput`, or the session
path below) and rewrites that node in place via
[`commitContent`](../document/typed/DESIGN.md); the node keeps its id,
so refs into it keep resolving — and unchanged children keep theirs. An
unchanged commit is a no-op, so a blur records no version. This is the
workbench's first projected editor: the view is not only derived from
the document, it writes back.

## Text surface (`DocText.scala`)

Typed documents use their language's `parse`/`print` for the content;
everything else falls back to `Outline`. `lang:` documents' language
has no `GrammarDef` — their text form is a format in
[`../langdef/doc/DefDocText.scala`](../langdef/doc/DefDocText.scala)
(meta constructs by name, `rule` nodes as `means` lines), supplied by
`DefLanguage` for any def interpreted as `lang: language`, and applied
through the same `parse` → `commitContent` path — so a text edit keeps
the ids of whatever didn't change, and `linkCommitted` then runs the
language's finishing pass (lang: relinks placeholder uses and term
constructs). Resolution is injected — the
caller decides how a type ref finds a language (the
[examples](../../examples/DESIGN.md)' store-aware resolver, for
instance). `holeCount` feeds the status bar.

## Sessions — incremental parsing state

A language offers its syntax as a `TextSyntax` (parser, lexicon, a key
identifying the grammar, how a tree becomes content, and `changedSince`
— which rules differ between syntaxes plus a migration for kept
content, e.g. term tags re-pinning a bumped def version). The parse-side
machinery (`Memo`, `ParseSession`) is in
[`../grammar/DESIGN.md`](../grammar/DESIGN.md); this package holds the
per-document sessions that ride on it:

- **The text surface runs on a session** (`TextSession`). The pane
  ([`ui/typeddoc`](../../ui/typeddoc/DESIGN.md)) keeps a `TextSession`
  and applies each new text as one prefix/suffix edit against the
  session's own text, so a session that missed a re-derived text is
  still correct. A re-decoded but unchanged `lang:` def has the same key
  and keeps the session; a changed one carries it through `changeRules`
  with the rules `changedRules` reports.
- **Projected inputs run on sessions too** (`InputSessions`): a
  `TextSession` per input node id (commits keep the input's id, so the
  key is stable), and a commit goes through `Language.commitInput`. A
  def's `InputGrammar` reads the text through the referred language's
  session when it parses incrementally, then classifies the result
  exactly as `parseInput` does (`?`-holes, blanks, label fallback,
  ref-mentioning holes); builtin input parsers still parse whole.
  Sessions of inputs that left the document are dropped on commit.
- **Stored holes retry the same way** (`HoleRetry`) — and only holes
  need to. A hole is the one node that still holds its source text;
  every other node is committed structure, re-*evaluated* by each
  render's recovering read but never re-*parsed* — rewriting committed
  structure on a language change is migration, gated by the
  [pending-update machinery](../document/store/DESIGN.md), not a render
  step. `HoleRetry` remembers, for each hole text in the document, the
  last attempt's syntax, content, and every rule the parse consulted.
  Views ask the input grammar through it (`Language.renderWith`), so a
  hole is reparsed only when the syntax changed and the change either
  touched a consulted rule or can't name its rules; otherwise the failed
  attempt stands — reused content is migrated (term tags re-pin) —
  sound because the consulted set is closed under calls, so a change
  disjoint from it replays identically. After each update the pane
  re-remembers its holes (`rememberInputHoles` in
  [`../langdef`](../langdef/DESIGN.md)) against the current store and
  drops attempts for texts no longer present. What moves a stored
  document's input grammar is a pin bump, so healing follows the
  accept-update gesture, not the dependency's save.

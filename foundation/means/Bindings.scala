package opelan.foundation.means

import opelan.foundation.document.typed.sameNode

// A placeholder's slot in its rule — its piece's position in
// `pattern.placeholderPieces`. Bound values key by slot, never by
// name or document ref: a `use`/`source`/`literal` operand's
// `binding` is the slot resolved when the rule was read
// (`resolveBindings`); an operand built in code carries none and
// resolves against the pattern at the binding site — its
// pattern-piece reference first, its authored name last.
def boundIndex(
    pieces: List[Piece.Placeholder], operand: Meaning): Option[Int] =
  operand.binding.orElse(
    operand.reference.flatMap(r =>
      pieces.indices.find(i => pieces(i).reference.exists(sameNode(_, r))))
  ).orElse(
    Meaning.nameOf(operand).flatMap(n =>
      pieces.indices.find(i => pieces(i).name == n)))

// The value an operand's slot holds — `use`/`source`/`literal`
// operands read their slot's bound value.
def bindingAt[V](bindings: Map[Int, V],
    pieces: List[Piece.Placeholder], operand: Meaning): Option[V] =
  boundIndex(pieces, operand).flatMap(bindings.get)

// Bound values by placeholder slot — pattern and values zip
// positionally, so the slot is the index.
def bindingsOf[V](pieces: List[Piece.Placeholder], vs: List[V]): Map[Int, V] =
  pieces.indices.zip(vs).toMap

// Resolve a meaning's bound operands to their pattern slots — the
// compiled form of placeholder linking: `use`/`source` operands, and
// the name terms inside `literal` (a value term like `neg(n)` reads
// bound values the same way), get their `binding`; every other term
// recurses untouched. `builtin`'s operand names a foundational
// definition, never a placeholder, and is left alone. An operand that
// resolves to no piece keeps no binding — it degrades at apply like
// any unbound placeholder (Never Broken).
def resolveBindings(pattern: Pattern, meaning: Meaning): Meaning = {
  val pieces = pattern.placeholderPieces
  def operand(a: Meaning): Meaning =
    boundIndex(pieces, a).map(i => a.copy(binding = Some(i)))
      .getOrElse(a)
  def inLiteral(m: Meaning): Meaning = m.tag match {
    case "use" | "source" => m.copy(arguments = m.arguments.map(operand))
    case _ if Meaning.isBuiltin(m.tag) => m
    case _ =>
      val bound = operand(m)
      bound.copy(arguments = bound.arguments.map(inLiteral))
  }
  def go(m: Meaning): Meaning = m.tag match {
    case "use" | "source" => m.copy(arguments = m.arguments.map(operand))
    case "literal"        => m.copy(arguments = m.arguments.map(inLiteral))
    case _                => m.copy(arguments = m.arguments.map(go))
  }
  go(meaning)
}

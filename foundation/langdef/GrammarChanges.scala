package opelan.foundation.langdef

import opelan.foundation.document.NodeData

// The named rules of a grammar, by name. When a name is defined twice
// the first definition wins, everywhere it is used.
def namedRules(top: GrammarDef): Map[String, GrammarDef] = {
  def collect(g: GrammarDef): List[(String, GrammarDef)] = g match {
    case GrammarDef.Named(n, inner)     => (n -> inner) :: collect(inner)
    case GrammarDef.Choice(alts)        => alts.flatMap(collect)
    case GrammarDef.Parens(inner)       => collect(inner)
    case GrammarDef.ChainLeft(_, atom)  => collect(atom)
    case GrammarDef.Precedence(atom, _) => collect(atom)
    case GrammarDef.Phrase(inner, _)    => collect(inner)
    case GrammarDef.Tok(_, _)           => Nil
    case GrammarDef.Signum              => Nil
    case GrammarDef.Junk(_, _)          => Nil
    case GrammarDef.Ref(_)              => Nil
    case GrammarDef.Hole                => Nil
  }
  collect(top).reverse.toMap
}

// The rules a language change touches: named rules whose definitions
// differ, plus rules present in only one grammar. A rule that merely
// *calls* a changed rule is not listed — an incremental session drops
// its results through the call dependencies it recorded.
def changedRules(before: GrammarDef, after: GrammarDef): Set[String] = {
  val (a, b) = (namedRules(before), namedRules(after))
  (a.keySet ++ b.keySet).filter(name => a.get(name) != b.get(name))
}

// The content migration between two kind-tag tables (kind name → the
// data a parse emits): a tag that changed — a construct ref re-pinning
// to a newer def version, or a kind gone from the schema — rewrites to
// what a fresh parse would emit; every other node data is untouched.
def retagBy(
    before: Map[String, NodeData],
    tag: String => NodeData): NodeData => NodeData = {
  val diff = before.iterator
    .map { case (kind, d) => d -> tag(kind) }
    .filter { case (a, b) => a != b }
    .toMap
  d => diff.getOrElse(d, d)
}

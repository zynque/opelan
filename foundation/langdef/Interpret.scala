package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar._

// Interpret a GrammarDef into a total, recovering Parser — each case is
// the data twin of a combinator in foundation/grammar. Named grammars
// are bound in a lazy environment so Ref (recursion, e.g. parens
// reaching back to the expression level) resolves at call time.
def interpret(top: GrammarDef): Parser = {

  def collect(g: GrammarDef): List[(String, GrammarDef)] = g match {
    case GrammarDef.Named(n, inner)      => (n -> inner) :: collect(inner)
    case GrammarDef.Choice(alts)         => alts.flatMap(collect)
    case GrammarDef.Parens(inner)        => collect(inner)
    case GrammarDef.ChainLeft(_, atom)   => collect(atom)
    case GrammarDef.Precedence(atom, _)  => collect(atom)
    case GrammarDef.Phrase(inner, _)     => collect(inner)
    case _                               => Nil
  }

  lazy val env: Map[String, Parser] =
    collect(top).map { case (n, g) => n -> go(g) }.toMap

  def go(g: GrammarDef): Parser = g match {
    case GrammarDef.Tok(kind, leaf) =>
      tok(kind)(t => leafData(leaf, t))
    case GrammarDef.Choice(alts) =>
      choice(alts.map(go)*)
    case GrammarDef.Parens(inner) =>
      parens(go(inner))
    case GrammarDef.Signum =>
      signum
    case GrammarDef.Junk(start, run) =>
      junk(start, run)
    case GrammarDef.ChainLeft(ops, atom) =>
      chainLeft(ops)(go(atom))
    case GrammarDef.Precedence(atom, levels) =>
      precedenceTable(go(atom), levels)
    case GrammarDef.Phrase(inner, stops) =>
      phrase(go(inner), stops)
    case GrammarDef.Named(_, inner) =>
      go(inner)
    case GrammarDef.Ref(name) =>
      (in, pos) => env(name)(in, pos)
  }

  go(top)
}

private def leafData(leaf: LeafKind, t: GTok): NodeData = leaf match {
  case LeafKind.IntLit         => NodeData.IntData(t.text.toInt)
  case LeafKind.FloatLit       => NodeData.FloatData(t.text.toDouble)
  case LeafKind.RefLit(prefix) => NodeData.InternalNodeRef(t.text.drop(prefix.length).toInt)
  case LeafKind.HoleLeaf       => NodeData.GapData("")
  case LeafKind.Text           => NodeData.StringData(t.text)
}

package opelan.foundation.langdef

import scala.collection.mutable
import opelan.foundation.document._
import opelan.foundation.grammar._

// Interpret a GrammarDef into a total, recovering Parser — each case is
// the data twin of a combinator in foundation/grammar. Named grammars
// are bound in a lazy environment so Ref (recursion, e.g. parens
// reaching back to the expression level) resolves at call time; a Ref
// to an unknown name produces a hole rather than crashing — a def doc
// can contain typos and stay Never Broken.
def interpret(top: GrammarDef): Parser = {

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

  // (name, pos) pairs currently on the call stack. Recursion is legal
  // only when it consumes input (parens reaches Ref at a later pos), so
  // a re-entry at the same pos is a non-consuming cycle — direct
  // self-reference or left-recursion — and degrades to a hole rather
  // than overflowing the stack on a malformed def.
  val inProgress = mutable.Set.empty[(String, Int)]

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
      (in, pos) =>
        if (inProgress(name -> pos)) holeHere(in, pos)
        else env.get(name) match {
          case Some(p) =>
            inProgress += (name -> pos)
            try p(in, pos) finally inProgress -= (name -> pos)
          case None => holeHere(in, pos)
        }
    case GrammarDef.Hole =>
      restHole
  }

  go(top)
}

private def leafData(leaf: LeafKind, t: GTok): NodeData = leaf match {
  case LeafKind.IntLit =>
    t.text.toIntOption.map(NodeData.IntData(_)).getOrElse(NodeData.GapData(t.text))
  case LeafKind.FloatLit =>
    t.text.toDoubleOption.map(NodeData.FloatData(_)).getOrElse(NodeData.GapData(t.text))
  case LeafKind.RefLit(prefix) =>
    t.text.drop(prefix.length).toIntOption
      .map(NodeData.InternalNodeRef(_)).getOrElse(NodeData.GapData(t.text))
  case LeafKind.HoleLeaf => NodeData.GapData("")
  case LeafKind.Text     => NodeData.StringData(t.text)
}

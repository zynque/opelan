package opelan.foundation.langdef.syntax

import scala.collection.mutable
import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef._

// Interpret a GrammarDef into a total, recovering Parser — each case is
// the data twin of a combinator in foundation/grammar. Named grammars
// are bound in a lazy environment so Ref (recursion, e.g. parens
// reaching back to the expression level) resolves at call time; a Ref
// to an unknown name produces a hole rather than crashing — a def doc
// can contain typos and stay Never Broken.
//
// Operator tables name node terms; `tag` turns a term name into the
// node data the parser emits — the construct's ref (see
// DefLanguage.termTag), so parsed trees use constructs by reference.
//
// Each named grammar is one memoized rule (foundation/grammar `rule`),
// reached the same way from its definition site and from every Ref, so
// an incremental session sees each call — what invalidating by rule
// identity relies on.
def interpret(top: GrammarDef, tag: String => NodeData): Parser = {

  // (name, pos) pairs currently on the call stack. Recursion is legal
  // only when it consumes input (parens reaches Ref at a later pos), so
  // a re-entry at the same pos is a non-consuming cycle — direct
  // self-reference or left-recursion — and degrades to a hole rather
  // than overflowing the stack on a malformed def.
  val inProgress = mutable.Set.empty[(String, Int)]

  lazy val env: Map[String, Parser] =
    namedRules(top).map { case (n, g) => n -> rule(n)(go(g)) }

  def go(g: GrammarDef): Parser = g match {
    case GrammarDef.Tok(kind, leaf) =>
      tok(kind)(t => leafData(leaf, t))
    case GrammarDef.Choice(alts) =>
      choice(alts.map(go)*)
    case GrammarDef.Parens(inner) =>
      parens(go(inner))
    case GrammarDef.Signum =>
      signum
    case GrammarDef.Unrecognized(start, run) =>
      unrecognized(start, run)
    case GrammarDef.ChainLeft(ops, atom) =>
      chainLeft(ops.view.mapValues(tag).toMap)(go(atom))
    case GrammarDef.Precedence(atom, levels) =>
      precedenceTable(go(atom), levels.map(_.view.mapValues(tag).toMap))
    case GrammarDef.Phrase(inner, stops) =>
      phrase(go(inner), stops)
    case GrammarDef.Named(name, _) =>
      (in, pos) => env(name)(in, pos)
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

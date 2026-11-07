package opelan.foundation.langdef.syntax

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import opelan.foundation.means.engine.LexicalTerm

// A named leaf production a `where` term binds to — which token
// kinds it accepts and how their text becomes leaf node data, as
// an ordered list of alternatives (first applies). This is the
// Tok pairing of the old operand atom, named so rules can name
// it: `n means n where n: number` makes number tokens operands,
// and `pick n ... where n: whole number` narrows a sequence
// placeholder to the production's tokens. Foundation builtins in
// Impls, beside the lexicons — a small fixed set. Which leaves
// stand alone as operands is authored by operand rules, not
// derived here; user-defined leaf productions are deferred past
// the means series (PLAN, *Authored leaf productions*).
case class LeafDef(productions: List[(String, LeafKind)])

// One leaf production as a token-level parser — its alternatives
// tried in order, each token mapped through its LeafKind.
def leafParser(theLeaf: LeafDef): Parser =
  choice(theLeaf.productions.map { case (kind, leaf) =>
    tok(kind)(t => leafData(leaf, t))
  }*)

// Whether a bound tree satisfies a leaf production — the `where`
// enforcement on bound operands: `number` accepts IntData and
// FloatData, `ref` an InternalNodeRef, and so on. A bound gap
// violates every production but `gap` itself.
def leafAccepts(theLeaf: LeafDef): NodeData => Boolean =
  data => theLeaf.productions.exists { case (_, leaf) =>
    dataMatches(leaf, data) }

private def dataMatches(leaf: LeafKind, data: NodeData): Boolean =
  (leaf, data) match {
    case (LeafKind.IntLit, NodeData.IntData(_))           => true
    case (LeafKind.FloatLit, NodeData.FloatData(_))       => true
    case (_: LeafKind.RefLit, _: NodeData.InternalNodeRef) => true
    case (LeafKind.GapLeaf, _: NodeData.GapData)         => true
    case (LeafKind.Text, NodeData.StringData(_))          => true
    case _                                                => false
  }

// Every leaf an Impls names as a LexicalTerm, plus the foundation's
// standard `where` terms — the `where` table compileRules binds
// constraint terms through. Operand status is not a leaf property:
// a lone-placeholder rule bound to the production (`n means n where
// n: number`) is what makes tokens operands, unless the term flags
// itself an operand as the foundation's `gap` does. An Impls leaf
// naming a standard term overrides it.
def lexicalTerms(impls: Impls): Map[String, LexicalTerm] =
  standardTerms ++ impls.leaves.map { case (name, theLeaf) =>
    name -> LexicalTerm(Some(leafParser(theLeaf)), leafAccepts(theLeaf)) }

// The workbench's gap leaf: the `gap` token reads as a gap.
private val gapLeaf = LeafDef(List("gap" -> LeafKind.GapLeaf))

// The `where` terms every language gets, foundation-provided. `gap` is
// the builtin operand: the workbench's gap token stands alone as a
// gap operand in every language with no authored rule (gaps are
// foundation machinery, not language content — a language may opt out
// later, for now none can). `value` is a check-only constraint on a
// bound tree that reads no token of its own: "a real value of the
// language" — a bound cell ref (InternalNodeRef) or provenance ref
// (SourceRef) is a workbench leaf, not a value; a bound gap
// satisfies provisionally — its value is unknown, not wrong (Never
// Broken).
val standardTerms: Map[String, LexicalTerm] =
  Map(
    "gap" -> LexicalTerm(
      Some(leafParser(gapLeaf)), leafAccepts(gapLeaf), operand = true),
    "value" -> LexicalTerm(None, {
      case _: NodeData.InternalNodeRef | _: NodeData.SourceRef => false
      case _                                                  => true
    }))

// How a token's text becomes leaf node data — the mapping a leaf
// production's alternatives use.
private[langdef] def leafData(leaf: LeafKind, t: Token): NodeData =
  leaf match {
    case LeafKind.IntLit =>
      t.text.toIntOption.map(NodeData.IntData(_))
        .getOrElse(NodeData.GapData(t.text))
    case LeafKind.FloatLit =>
      t.text.toDoubleOption.map(NodeData.FloatData(_))
        .getOrElse(NodeData.GapData(t.text))
    case LeafKind.RefLit(prefix) =>
      t.text.drop(prefix.length).toIntOption
        .map(NodeData.InternalNodeRef(_))
        .getOrElse(NodeData.GapData(t.text))
    case LeafKind.GapLeaf => NodeData.GapData(t.text)
    case LeafKind.Text     => NodeData.StringData(t.text)
  }

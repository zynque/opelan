package opelan.foundation.langdef.syntax

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import opelan.foundation.means.RuleSet
import opelan.foundation.means.engine.LexicalTerm

// A named leaf production a `where` term binds to — which token
// kinds it accepts and how their text becomes leaf node data, as
// an ordered list of alternatives (first applies). This is the
// Tok pairing of the old operand atom, named so rules can name
// it: `n means n where n: number` makes number tokens operands,
// and `pick n ... where n: whole number` narrows a sequence
// placeholder to the production's tokens. Code-side in Impls,
// like lexicons and views, until leaf productions are authored
// in the definition document (STATUS Phase A, item 2).
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
    case (LeafKind.HoleLeaf, _: NodeData.GapData)         => true
    case (LeafKind.Text, NodeData.StringData(_))          => true
    case _                                                => false
  }

// Every leaf an Impls names as a LexicalTerm — the `where` table
// compileRules binds constraint terms through. `operand` marks the
// names that stand alone as operand productions: every named leaf
// `where` doesn't bind — a bound term is narrowing inside a compound
// (`where n: number`), while a lone-placeholder rule bound to it
// (`n means n where n: t`) still makes `t` an operand through its own
// bound production.
def lexicalTerms(
    impls: Impls, set: RuleSet): Map[String, LexicalTerm] = {
  val narrowing = whereLeafNames(set)
  impls.leaves.map { case (name, theLeaf) =>
    name -> LexicalTerm(leafParser(theLeaf), !narrowing(name),
      leafAccepts(theLeaf)) }
}

// How a token's text becomes leaf node data — the shared mapping
// GrammarDef.Tok alternatives and leaf productions both use.
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
    case LeafKind.HoleLeaf => NodeData.GapData(t.text)
    case LeafKind.Text     => NodeData.StringData(t.text)
  }

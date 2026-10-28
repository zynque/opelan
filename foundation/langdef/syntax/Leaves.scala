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

// The def's leaf-term names — the vocabulary's `term(_, leaf)`
// entries — marking which named leaves are operands.
def leafTermNames(d: LanguageDef): Set[String] =
  d.vocabulary.collect { case t if t.shape == NodeShape.Leaf =>
    t.name }.toSet

// Every leaf an Impls names as a LexicalTerm — the `where` table
// compileRules binds constraint terms through. `operands` marks
// the names that stand alone as operand productions — the
// vocabulary's leaf terms (leafTermNames).
def lexicalTerms(
    impls: Impls, operands: Set[String]): Map[String, LexicalTerm] =
  impls.leaves.map { case (name, theLeaf) =>
    name -> LexicalTerm(leafParser(theLeaf), operands(name),
      leafAccepts(theLeaf)) }

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

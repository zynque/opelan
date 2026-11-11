package opelan.foundation.means.engine

import opelan.foundation.document.{ExternalNodeReference, NodeData, NodeDataText}
import opelan.foundation.document.typed.sameNode

// The first foundational definitions (GLOSSARY.md): each entry is a
// stable reference — a node carrying that ref is the signal to call
// its bound implementation — the first draft of the definition
// entries the floor gets (name, ref, impl; docs and examples to
// come). Values are leaf node data — ints stay ints, anything else
// floats — the numeric floor every language gets; effects (IO,
// randomness, rendering) join it as the substrate shrinks toward them.
case class FoundationalDef(
    name: String,
    arity: Int,
    ref: ExternalNodeReference,
    eval: List[NodeData] => Either[String, NodeData])

private def foundationRef(nodeId: Int) =
  ExternalNodeReference("opelan:foundation/arithmetic", 0, nodeId)

val foundationDefs: List[FoundationalDef] = List(
  FoundationalDef("add", 2, foundationRef(1), num2(_ + _, _ + _)),
  FoundationalDef("sub", 2, foundationRef(2), num2(_ - _, _ - _)),
  FoundationalDef("mul", 2, foundationRef(3), num2(_ * _, _ * _)),
  FoundationalDef("div", 2, foundationRef(4), numDiv),
  FoundationalDef("neg", 1, foundationRef(5), {
    case List(NodeData.IntData(v))   => Right(NodeData.IntData(-v))
    case List(NodeData.FloatData(v)) => Right(NodeData.FloatData(-v))
    case _                           => Left("neg expects a number")
  }))

// Dispatch by reference — the version in the pin is ignored, like any
// construct use (sameNode).
def foundationalOf(ref: ExternalNodeReference): Option[FoundationalDef] =
  foundationDefs.find(d => sameNode(d.ref, ref))

// name → ref — the recovery floor for content carrying bare names;
// a def doc that uses a foundational term declares it as an import
// instead (DefDocEncode's vocabulary section).
val foundationRefs: Map[String, ExternalNodeReference] =
  foundationDefs.map(d => d.name -> d.ref).toMap

private def num2(
    i: (Int, Int) => Int,
    d: (Double, Double) => Double): List[NodeData] => Either[String, NodeData] = {
  case List(NodeData.IntData(a), NodeData.IntData(b)) =>
    Right(NodeData.IntData(i(a, b)))
  case List(a, b) => (numOf(a), numOf(b)) match {
    case (Some(x), Some(y)) => Right(NodeData.FloatData(d(x, y)))
    case _                  => Left("expected numbers")
  }
  case _ => Left("expected two arguments")
}

// Division always floats and fails at zero — the caller renders the
// failure as a gap, not an error.
private def numDiv: List[NodeData] => Either[String, NodeData] = {
  case List(a, b) => (numOf(a), numOf(b)) match {
    case (Some(_), Some(0.0)) => Left("division by zero")
    case (Some(x), Some(y))   => Right(NodeData.FloatData(x / y))
    case _                    => Left("expected numbers")
  }
  case _ => Left("expected two arguments")
}

private def numOf(d: NodeData): Option[Double] = d match {
  case NodeData.IntData(v)   => Some(v.toDouble)
  case NodeData.FloatData(v) => Some(v)
  case _                     => None
}

// A value's display form: a whole float prints without a fraction
// ("3", not "3.0"), and decimals are display-rounded — stored values
// stay exact, the shown number drops float noise (Num.show's rule).
def valueText(d: NodeData): String = d match {
  case NodeData.IntData(v)   => v.toString
  case NodeData.FloatData(v) =>
    val r = if (v.isWhole) v else math.round(v * 1e10) / 1e10
    if (r.isWhole && r.abs < 1e15) r.toLong.toString else r.toString
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}

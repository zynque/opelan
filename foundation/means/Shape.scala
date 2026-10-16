package opelan.foundation.means

// A rule's shape, read off its pattern's edges — which decides how the
// rule compiles (DESIGN.md §4, *Engine*):
//
//   Leaf     a lone placeholder            n
//   Closed   literal at both edges         (a)   |a|   pi
//   Prefix   literal first, placeholder last      -a    if c then a else b
//   Postfix  placeholder first, literal last      a %
//   Infix    placeholders at both edges           a + b   n x
//
// Prefix, Postfix, and Infix are operator-shaped: an edge placeholder
// reaches as far as the binding list allows, so they join the operator
// combinator. Leaf and Closed compile to plain sequences — atoms of it.
enum Shape {
  case Leaf, Closed, Prefix, Postfix, Infix

  def isOperator: Boolean = this match {
    case Prefix | Postfix | Infix => true
    case Leaf | Closed            => false
  }
}

def shapeOf(pattern: Pattern): Shape = {
  def isPlaceholder(piece: Piece) = piece.isInstanceOf[Piece.Placeholder]
  pattern.pieces match {
    case List(Piece.Placeholder(_, _)) => Shape.Leaf
    case Nil                           => Shape.Closed
    case ps => (isPlaceholder(ps.head), isPlaceholder(ps.last)) match {
      case (true, true)   => Shape.Infix
      case (false, true)  => Shape.Prefix
      case (true, false)  => Shape.Postfix
      case (false, false) => Shape.Closed
    }
  }
}

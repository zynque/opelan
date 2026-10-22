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

// Whether a pattern is operator-shaped in the compile sense: edge
// placeholders only, literals contiguous between them —
//
//   a + b   a is not b   n x   (Infix, possibly literal-free)
//   -a      not a        (Prefix)
//   a %                  (Postfix)
//
// A keyword-led form like `if c then a else b` is Prefix-*shaped* by
// its edges but has placeholders inside, so it is not an operator: it
// compiles to a sequence and needs no binding (DESIGN.md §4).
def isOperatorPattern(pattern: Pattern): Boolean = {
  val pieces = pattern.pieces
  def placeholder(piece: Piece) = piece.isInstanceOf[Piece.Placeholder]
  def literal(piece: Piece) = piece.isInstanceOf[Piece.Literal]
  pieces.length >= 2 &&
    (placeholder(pieces.head) || placeholder(pieces.last)) &&
    pieces.drop(1).dropRight(1).forall(literal)
}

// A pattern that compiles to a sequence — an operand production:
// not operator-shaped, and starting with a literal. A leading-
// placeholder pattern like `a , b , c` would left-recurse, so it
// is skipped. These are the rules the compiler memoizes under a
// `means:` name, so this is also the classification language-change
// diffing uses to name them.
def isSequencePattern(pattern: Pattern): Boolean =
  !isOperatorPattern(pattern) &&
    pattern.pieces.headOption.exists(_.isInstanceOf[Piece.Literal])

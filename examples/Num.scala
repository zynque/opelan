package opelan.examples

// The expression language's number type: integers stay integers so
// counts and indexes read naturally, while division and float literals
// produce decimals (money). `I` + `I` stays `I`; anything else floats.
enum Num {
  case I(v: Int)
  case F(v: Double)

  def toDouble: Double = this match {
    case Num.I(v) => v.toDouble
    case Num.F(v) => v
  }
}

object Num {
  // Display form: a whole float prints without a fraction ("3", not
  // "3.0"), and decimals are display-rounded — stored values stay exact,
  // but a shown number drops float noise (16.200000000000003 -> "16.2"),
  // the way a spreadsheet's displayed value differs from the stored one.
  def show(n: Num): String = n match {
    case Num.I(v) => v.toString
    case Num.F(v) =>
      val r = if (v.isWhole) v else math.round(v * 1e10) / 1e10
      if (r.isWhole && r.abs < 1e15) r.toLong.toString else r.toString
  }

  def add(a: Num, b: Num): Num = (a, b) match {
    case (Num.I(x), Num.I(y)) => Num.I(x + y)
    case _                    => Num.F(a.toDouble + b.toDouble)
  }

  def sub(a: Num, b: Num): Num = (a, b) match {
    case (Num.I(x), Num.I(y)) => Num.I(x - y)
    case _                    => Num.F(a.toDouble - b.toDouble)
  }

  def mul(a: Num, b: Num): Num = (a, b) match {
    case (Num.I(x), Num.I(y)) => Num.I(x * y)
    case _                    => Num.F(a.toDouble * b.toDouble)
  }

  // Division always floats and is partial at zero — the caller renders
  // the failure (as a hole, not an error).
  def div(a: Num, b: Num): Option[Num] =
    if (b.toDouble == 0.0) None else Some(Num.F(a.toDouble / b.toDouble))
}

package opelan.ui.fx

import opelan.ui.fp._

// Seam component: randomness. Requests are props like the other seams;
// the generated value returns as an output tagged with the request id.
enum RandomReq {
  case Suffix(id: Int, length: Int)
}

enum RandomEv {
  case Generated(id: Int, value: String)
}

object Random extends Component[Option[RandomReq], RandomEv] {
  type State = Set[Int]

  def init: State = Set.empty

  def update(s: State, in: Option[RandomReq]): Update[State, RandomEv, Option[RandomReq]] =
    in match {
      case Some(RandomReq.Suffix(id, len)) if !s(id) =>
        val v = scala.util.Random.alphanumeric
          .take(len).mkString.toLowerCase
        Update(s + id, Vector(RandomEv.Generated(id, v)))
      case _ => Update(s)
    }

  def view(s: State): View[Option[RandomReq]] = View.Text("")
}

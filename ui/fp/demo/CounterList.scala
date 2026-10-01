package opelan.ui.fp.demo

import opelan.ui.fp._
import opelan.ui.fp.Dsl._

enum CounterListInput:
  case AddCounter
  case RemoveCounter(id: Int)
  case FromCounter(id: Int, output: CounterOutput)

enum CounterListOutput:
  case Summary(total: Int, count: Int)

case class CounterListState(
    items: Vector[Int],
    values: Map[Int, Int],
    nextId: Int)

// A dynamic parent: clicks change state, state changes children, and the
// reconciler creates/destroys Counter instances to match. Each child's
// output is routed back as FromCounter, and the child's input is its current
// value pushed down as props.
object CounterList extends Component[CounterListInput, CounterListOutput] {
  type State = CounterListState

  def init: State = CounterListState(Vector.empty, Map.empty, 0)

  def update(state: State, input: CounterListInput): Update[State, CounterListOutput] =
    input match {
      case CounterListInput.AddCounter =>
        changed(state.copy(
          items = state.items :+ state.nextId,
          nextId = state.nextId + 1))

      case CounterListInput.RemoveCounter(id) =>
        changed(state.copy(
          items = state.items.filterNot(_ == id),
          values = state.values - id))

      case CounterListInput.FromCounter(id, CounterOutput.Changed(v)) =>
        changed(state.copy(values = state.values + (id -> v)))
    }

  private def changed(state: State): Update[State, CounterListOutput] =
    Update(state, Vector(CounterListOutput.Summary(
      state.values.values.sum, state.items.length)))

  def view(state: State): View[CounterListInput] = {
    val removeButton: Vector[View[CounterListInput]] =
      if (state.items.isEmpty) Vector.empty
      else Vector(el("button",
                     style("margin-left: 4px;"),
                     events = on("click", CounterListInput.RemoveCounter(state.items.last)))(
                    text("Remove last")))
    val controls: Vector[View[CounterListInput]] =
      el("button", events = on("click", CounterListInput.AddCounter))(
        text("Add counter")) +:
      removeButton :+
      el("span", style("margin-left: 10px; color: #555;"))(
        text(s"total: ${state.values.values.sum}"))
    el("div")(
      View.Elem("div", Map.empty, Map.empty, controls),
      mount)
  }

  override def children(state: State): Vector[Child[?, ?, CounterListInput]] =
    state.items.map { id =>
      Child(
        key = id.toString,
        component = Counter,
        input = CounterInput.SetValue(state.values.getOrElse(id, 0)),
        onOutput = o => CounterListInput.FromCounter(id, o))
    }
}

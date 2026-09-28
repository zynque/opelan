package opelan.ui.fp.demo

import opelan.ui.fp._
import opelan.ui.fp.Dsl._

enum CounterInput:
  case Increment
  case Decrement
  case SetValue(n: Int)

enum CounterOutput:
  case Changed(value: Int)

// A leaf component: no children, one input channel, one output channel.
object Counter extends Component[CounterInput, CounterOutput] {
  type State = Int

  def init: State = 0

  def update(state: State, input: CounterInput): Update[State, CounterOutput] =
    input match {
      case CounterInput.Increment =>
        val next = state + 1
        Update(next, Vector(CounterOutput.Changed(next)))
      case CounterInput.Decrement =>
        val next = state - 1
        Update(next, Vector(CounterOutput.Changed(next)))
      case CounterInput.SetValue(n) =>
        Update(n, Vector(CounterOutput.Changed(n)))
    }

  def view(state: State): View[CounterInput] =
    el("div", style("display: inline-flex; align-items: center; margin: 4px;"))(
      el("button", events = on("click", CounterInput.Decrement))(text("−")),
      el("span", style("margin: 0 8px; min-width: 3ch; text-align: center;"))(
        text(state.toString)),
      el("button", events = on("click", CounterInput.Increment))(text("+")))
}

package opelan.ui.fp

// A component is a reusable definition: a stateful machine with one input
// channel and a stream of outputs. Everything that happens to a component —
// input pushed by its parent, DOM events raised by its own view, child output
// routed upward — arrives as an I. Update is pure; effects (focus, storage,
// async) travel as output values to whoever mounted the component.
trait Component[I, O] {
  type State

  def init: State

  def update(state: State, input: I): Update[State, O]

  // The desired DOM for the current state; events produce this component's I.
  // View.Mount marks where this component's children attach.
  def view(state: State): View[I]

  // The desired children for the current state. The runtime reconciles this
  // against live children: same key + same component reuses the instance,
  // new keys are created, missing keys are destroyed. Identity and lifetime
  // are reconciliation concerns, not component logic.
  def children(state: State): Vector[Child[?, ?, I]] = Vector.empty
}

// The result of processing an input: new state plus outputs to emit.
case class Update[S, O](state: S, out: Vector[O] = Vector.empty)

// Description of a child instance the parent wants to exist.
//
//   key        identity for reconciliation
//   component  what to instantiate
//   input      current props; sent on creation and whenever it changes
//   onOutput   adapts the child's output type into the parent's input type
//
// CI/CO are existential to the parent, but the triple is stored as one
// bundle in the runtime, so the types stay coherent.
case class Child[CI, CO, PI](
  key: String,
  component: Component[CI, CO],
  input: CI,
  onOutput: CO => PI)

package opelan.ui.fp

// A deferred effect: a description of work that may produce inputs back
// into the component's own input channel. The runtime runs a Cmd's body
// after update returns, handing it the instance's `emit` — which stays
// valid for the instance's lifetime, so a body may install long-lived
// callbacks (session subscriptions, future completions) as well as
// fire-and-forget sends.
//
// `run` is package-private: application code can hold and pass Cmds but
// only the runtime executes them. Constructing one is the sanctioned
// extension point — seam components live at the effect boundary and use
// it to bridge callbacks back into the input queue.
trait Cmd[+I] {
  private[fp] def run(emit: I => Unit): Unit
}

object Cmd {
  // The general constructor — seam components (storage, sync, prompts)
  // use it to lift callbacks and futures into inputs.
  def apply[I](body: (I => Unit) => Unit): Cmd[I] =
    new Cmd[I] { def run(emit: I => Unit): Unit = body(emit) }

  def emit[I](i: I): Cmd[I] = Cmd(e => e(i))

  def batch[I](cs: Iterable[Cmd[I]]): Cmd[I] =
    Cmd(emit => cs.foreach(_.run(emit)))
}

package opelan.ui.fp

import scala.collection.mutable
import org.scalajs.dom

// The runtime: owns the dispatch queue and the root instance. Everything —
// DOM events, child outputs routed upward, external sends — goes through one
// FIFO queue, so processing is never re-entrant even when an output maps
// straight back into an input. The queue is also the natural hook for a
// future input log (which, per the manifesto, could itself be a document).
final class Runtime {
  private val queue = mutable.Queue.empty[() => Unit]
  private var draining = false

  def enqueue(thunk: () => Unit): Unit = {
    queue.enqueue(thunk)
    if (!draining) {
      draining = true
      try
        while (queue.nonEmpty) queue.dequeue()()
      finally
        draining = false
    }
  }
}

// The outside handle to a mounted component: push inputs, subscribe to
// outputs. The component's in/out streams, exposed plainly.
final class Handle[I, O](
    sendInput: I => Unit,
    val outputs: Subject[O]) {
  def send(input: I): Unit = sendInput(input)
}

object Runtime {

  // Mount a component as the root of a live tree under `container`.
  def mount[I, O](container: dom.Element, component: Component[I, O]): Handle[I, O] = {
    val rt = new Runtime
    val outputs = new Subject[O]
    val root = new Instance[I, O](component, outputs.emit, rt)
    rt.enqueue(() => container.appendChild(root.mount()))
    new Handle[I, O](i => rt.enqueue(() => root.receive(i)), outputs)
  }
}

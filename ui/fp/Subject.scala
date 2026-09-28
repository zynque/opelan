package opelan.ui.fp

// A minimal broadcast channel: the input/output streams at the boundary of
// a mounted component. Later combinators (map, filter, merge) go here.
final class Subject[A] {
  private var subscribers = Vector.empty[A => Unit]

  def emit(a: A): Unit = subscribers.foreach(_(a))

  def subscribe(f: A => Unit): Unit = subscribers :+= f
}

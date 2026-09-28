package opelan.ui.fp

import org.scalajs.dom

// The desired DOM structure for a component's current state.
// Event handlers map a DOM event to an optional input — returning None
// means the event is ignored (no dispatch). Mount marks the element this
// component's children attach to (rendered as a display:contents div, so it
// doesn't affect layout).
enum View[+I] {
  case Text(value: String) extends View[Nothing]
  case Elem[I](
    tag: String,
    attrs: Map[String, String],
    events: Map[String, dom.Event => Option[I]],
    children: Vector[View[I]]) extends View[I]
  case Mount extends View[Nothing]
}

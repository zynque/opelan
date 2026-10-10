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

  // Where children attach; `slot` selects which children land here —
  // several mounts let a view place children at different positions.
  case Mount(slot: String = "") extends View[Nothing]

  // An imperative widget mounted under a managed key: Render calls
  // mount(el, emit) once when the node is created; Patch keeps the node
  // while the key is stable and rebuilds it when the key changes.
  // For widgets like CodeMirror that own their own DOM. `attrs` style the
  // wrapper element the widget mounts into.
  case Managed[I](
    key: String,
    attrs: Map[String, String] = Map.empty,
    mount: (dom.Element, I => Unit) => Unit) extends View[I]
}

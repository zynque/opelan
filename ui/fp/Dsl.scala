package opelan.ui.fp

import org.scalajs.dom

// Small helpers for building View trees.
object Dsl {

  def text(value: String): View[Nothing] = View.Text(value)

  def el[I](
      tag: String,
      attrs: Map[String, String] = Map.empty,
      events: Map[String, dom.Event => Option[I]] = Map.empty)(
      children: View[I]*): View[I] =
    View.Elem(tag, attrs, events, children.toVector)

  // el taking a computed collection of children.
  def els[I](
      tag: String,
      attrs: Map[String, String] = Map.empty,
      events: Map[String, dom.Event => Option[I]] = Map.empty)(
      children: Iterable[View[I]]): View[I] =
    View.Elem(tag, attrs, events, children.toVector)

  // Emit a fixed input on an event ("click", "keydown", ...).
  def on[I](event: String, input: I): Map[String, dom.Event => Option[I]] =
    Map(event -> (_ => Some(input)))

  // Map a DOM event to an input.
  def onEvent[I](event: String)(f: dom.Event => I): Map[String, dom.Event => Option[I]] =
    Map(event -> (e => Some(f(e))))

  // Map a DOM event to an optional input (None = ignore the event).
  def onEventOpt[I](event: String)(f: dom.Event => Option[I]): Map[String, dom.Event => Option[I]] =
    Map(event -> f)

  // Combine event maps (e.g. onclick ++ onkeydown).
  def events[I](ms: Map[String, dom.Event => Option[I]]*): Map[String, dom.Event => Option[I]] =
    ms.foldLeft(Map.empty[String, dom.Event => Option[I]])(_ ++ _)

  def style(css: String): Map[String, String] = Map("style" -> css)

  // Where this component's children render.
  val mount: View[Nothing] = View.Mount
}

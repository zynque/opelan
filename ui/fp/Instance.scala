package opelan.ui.fp

import org.scalajs.dom

// A live component instance: current state, live DOM, and live children
// produced by reconciling `children`. Instances are the nodes of the live
// component tree; the Runtime owns the dispatch queue they feed into.
final class Instance[I, O](
    val component: Component[I, O],
    outputSink: O => Unit,
    private[fp] val rt: Runtime) {

  var state: component.State = component.init

  private[fp] var node: dom.Node = null
  private var destroyed = false
  private var mountPoint: Option[dom.Element] = None
  private var currentView: View[I] = null
  private[fp] var liveChildren: Map[String, ChildEntry] = Map.empty

  private val emit: I => Unit = i => rt.enqueue(() => receive(i))

  // Apply the input to state and emit outputs, without touching the DOM.
  // Used at creation so a child's first input lands before its first render.
  private[fp] def step(input: I): Unit =
    if (!destroyed) {
      val u = component.update(state, input)
      state = u.state
      u.out.foreach(outputSink)
    }

  def receive(input: I): Unit = {
    step(input)
    if (!destroyed && node != null) refresh()
  }

  // Render (creating the root node on first call), apply post-render
  // focus/scroll hooks once the node is attached, and reconcile children.
  private def refresh(): Unit = {
    val view = component.view(state)
    val ctx = new Render.Ctx
    if (node == null)
      node = Render.render(view, emit, ctx)
    else
      Patch.patch(node, currentView, view, emit, ctx)
    currentView = view
    mountPoint = ctx.mountPoint
    ctx.focusElement.foreach((el, sel) => rt.enqueue(() => focusNow(el, sel)))
    ctx.scrollElement.foreach(el => rt.enqueue(() => Render.scrollIntoView(el)))
    mountPoint.foreach(mp => Reconcile.reconcile(this, component.children(state), mp))
  }

  private def focusNow(el: dom.Element, select: Boolean): Unit = {
    el.asInstanceOf[dom.HTMLElement].focus()
    (el, select) match {
      case (in: dom.HTMLInputElement, true) => in.select()
      case _ => ()
    }
  }

  // This instance's root DOM node, rendering on first call.
  def mount(): dom.Node = {
    if (!destroyed && node == null) refresh()
    node
  }

  def destroy(): Unit =
    if (!destroyed) {
      destroyed = true
      liveChildren.values.foreach(_.instance.destroy())
      liveChildren = Map.empty
      if (node != null) Option(node.parentNode).foreach(_.removeChild(node))
    }
}

// A live child slot. Types are erased here, but the pieces were constructed
// together from one typed Child[CI, CO, PI], so the casts stay coherent.
private[fp] final class ChildEntry(
    val key: String,
    val component: Component[?, ?],
    val instance: Instance[?, ?],
    val send: Any => Unit,
    var lastInput: Any)

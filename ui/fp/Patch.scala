package opelan.ui.fp

import org.scalajs.dom

// Patches a live DOM node to match a new view, reusing nodes where the shape
// is unchanged — which is what keeps focus and in-progress input state alive
// across re-renders. Matching is positional within each children vector;
// child *component* identity is handled separately by Reconcile.

// Patch `node` in place; replace it under its parent if the shape changed.
def patchView[I](
    node: dom.Node,
    oldView: View[I],
    newView: View[I],
    emit: I => Unit,
    ctx: RenderCtx): Unit = {
  val replacement = patchNode(node, oldView, newView, emit, ctx)
  if (replacement ne node)
    Option(node.parentNode).foreach(_.replaceChild(replacement, node))
}

// Returns the node now representing newView (same node, or a rebuilt one).
private def patchNode[I](
    node: dom.Node,
    oldView: View[I],
    newView: View[I],
    emit: I => Unit,
    ctx: RenderCtx): dom.Node =
  (oldView, newView) match {
    case (View.Text(a), View.Text(b)) =>
      if (a != b) node.textContent = b
      node

    case (View.Mount, View.Mount) =>
      ctx.mountPoint = Some(node.asInstanceOf[dom.Element])
      node

    // Same key: the mounted widget owns its DOM; leave it alone.
    case (View.Managed(k1, _), View.Managed(k2, _)) if k1 == k2 => node

    case (View.Elem(t1, a1, _, c1), View.Elem(t2, a2, e2, c2)) if t1 == t2 =>
      val el = node.asInstanceOf[dom.Element]
      patchAttrs(el, a1, a2)
      wireEvents(el, e2, emit)
      patchChildren(el, c1, c2, emit, ctx)
      if (a2.get("data-focus").contains("true"))
        ctx.focusElement = Some((el, false))
      if (a2.get("data-scroll").contains("true"))
        ctx.scrollElement = Some(el)
      node

    case _ =>
      // Text/Elem/Mount mismatch or different tag: rebuild this node.
      renderView(newView, emit, ctx)
  }

private def patchAttrs(
    el: dom.Element,
    oldA: Map[String, String],
    newA: Map[String, String]): Unit = {
  (oldA.keySet -- newA.keySet).foreach(el.removeAttribute)
  newA.foreach { (k, v) =>
    if (oldA.get(k) != Some(v)) el.setAttribute(k, v)
  }
}

private def patchChildren[I](
    el: dom.Element,
    oldC: Vector[View[I]],
    newC: Vector[View[I]],
    emit: I => Unit,
    ctx: RenderCtx): Unit = {
  val common = math.min(oldC.length, newC.length)
  (0 until common).foreach { i =>
    val child = el.childNodes(i)
    val replacement = patchNode(child, oldC(i), newC(i), emit, ctx)
    if (replacement ne child) el.replaceChild(replacement, child)
  }
  (common until newC.length).foreach { i =>
    el.appendChild(renderView(newC(i), emit, ctx))
  }
  while (el.childNodes.length > newC.length)
    el.removeChild(el.childNodes(newC.length))
}

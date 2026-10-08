package opelan.ui.fp

import org.scalajs.dom

// Reconciles a parent's desired child descriptions against its live
// children:
//   same key + same component -> reuse; send input if it changed
//   new key or different component -> create (destroying any occupant)
//   live key absent from the description -> destroy
// Then children are appended in description order, which orders them and
// re-homes them if the mount element was rebuilt.
object Reconcile {

  def reconcile[PI](
      parent: Instance[PI, ?],
      desired: Vector[Child[?, ?, PI]],
      mount: dom.Element): Unit = {
    val surplus = scala.collection.mutable.Map.from(parent.liveChildren)
    val ordered = Vector.newBuilder[ChildEntry]

    desired.foreach { child =>
      live(parent, surplus, child.key, child.component) match {
        case Some(entry) =>
          if (entry.lastInput != child.input) {
            entry.lastInput = child.input
            entry.send(child.input)
          }
          ordered += entry
        case None =>
          ordered += create(parent, child)
      }
    }

    surplus.values.foreach(_.instance.destroy())
    val entries = ordered.result()
    parent.liveChildren = entries.map(e => e.key -> e).toMap
    // Only move a child when it's actually out of position: appendChild on
    // an already-correct node still does a remove+insert, and reparenting
    // a subtree containing the focused element can blur it (e.g. killing
    // the edit input mid-typing).
    entries.zipWithIndex.foreach { case (e, i) =>
      val node = e.instance.mount()
      if (i >= mount.childNodes.length || mount.childNodes(i) != node)
        mount.insertBefore(node, mount.childNodes(i))
    }
  }

  private def live(
      parent: Instance[?, ?],
      surplus: scala.collection.mutable.Map[String, ChildEntry],
      key: String,
      component: Component[?, ?]): Option[ChildEntry] =
    surplus.remove(key) match {
      case Some(entry) if entry.component eq component => Some(entry)
      case Some(entry) => entry.instance.destroy(); None
      case None => None
    }

  private def create[CI, CO, PI](
      parent: Instance[PI, ?],
      child: Child[CI, CO, PI]): ChildEntry = {
    val inst = new Instance[CI, CO](
      child.component,
      (co: CO) => parent.rt.enqueue(() => parent.receive(child.onOutput(co))),
      parent.rt)
    inst.step(child.input)
    new ChildEntry(
      child.key,
      child.component,
      inst,
      in => parent.rt.enqueue(() => inst.receive(in.asInstanceOf[CI])),
      child.input)
  }
}

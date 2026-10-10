package opelan.ui.fp

import org.scalajs.dom
import opelan.ui.editor.{FakeDom, FakeElement}
import opelan.ui.fp.Dsl._

// Runtime-level tests: Cmd bodies feeding inputs back through the queue,
// unmount running when reconciliation removes a child, and named slots
// each holding their own children. Drives the real Runtime/Instance/
// Reconcile against the editor suite's FakeDom.
class RuntimeSuite extends munit.FunSuite {

  private def container(): dom.Element = {
    if (!FakeDom.installed) FakeDom.install()
    val c = new FakeElement("div")
    FakeDom.registerRoot(c.asInstanceOf[dom.Node])
    c.asInstanceOf[dom.Element]
  }

  // A probe that emits Done(v) either immediately (Go) or whenever the
  // stored emitter is invoked (Arm) — the long-lived callback case.
  private object Probe extends Component[Probe.In, Probe.Out] {
    enum In { case Go; case Arm; case Done(v: Int) }
    enum Out { case Final(v: Int) }
    var later: In => Unit = _

    type State = Int
    def init = 0
    def update(s: State, in: In): Update[State, Out, In] =
      in match {
        case In.Go      => Update(s, cmds = Vector(e => e(In.Done(41))))
        case In.Arm     => Update(s, cmds = Vector(e => { later = e }))
        case In.Done(v) => Update(v, Vector(Out.Final(v)))
      }
    def view(s: State): View[In] = View.Text(s.toString)
  }

  test("a Cmd's emitted input flows back through update and out") {
    val seen = scala.collection.mutable.ListBuffer.empty[Probe.Out]
    val h = Runtime.mount(container(), Probe)
    h.outputs.subscribe(seen += _)
    h.send(Probe.In.Go)
    assertEquals(seen.toList, List(Probe.Out.Final(41)))
  }

  test("a Cmd can install a callback that emits inputs later") {
    val seen = scala.collection.mutable.ListBuffer.empty[Probe.Out]
    val h = Runtime.mount(container(), Probe)
    h.outputs.subscribe(seen += _)
    h.send(Probe.In.Arm)
    assert(seen.isEmpty)
    Probe.later(Probe.In.Done(7))
    assertEquals(seen.toList, List(Probe.Out.Final(7)))
  }

  // A leaf that records every state it was unmounted with.
  private object Leaf extends Component[String, Nothing] {
    var unmounted: List[String] = Nil
    type State = String
    def init = ""
    def update(s: State, in: String): Update[State, Nothing, String] =
      Update(in)
    override def unmount(s: State): Unit = unmounted ::= s
    def view(s: State): View[String] = View.Text(s)
  }

  // One slot, child presence toggled by input.
  private object Toggle extends Component[Boolean, Nothing] {
    type State = Boolean
    def init = true
    def update(s: State, in: Boolean): Update[State, Nothing, Boolean] =
      Update(in)
    def view(s: State): View[Boolean] = el("div")(mount)
    override def children(s: State) =
      if (s) Vector(Child("leaf", Leaf, "alive", (o: Nothing) => o))
      else Vector.empty
  }

  test("unmount runs when reconciliation removes the child") {
    Leaf.unmounted = Nil
    val h = Runtime.mount(container(), Toggle)
    assertEquals(Leaf.unmounted, Nil)
    h.send(false)
    assertEquals(Leaf.unmounted, List("alive"))
    h.send(true) // remounts a fresh instance — no extra unmount
    assertEquals(Leaf.unmounted, List("alive"))
  }

  // Two named mounts, each with its own child.
  private object Slots extends Component[Boolean, Nothing] {
    type State = Boolean
    def init = false
    def update(s: State, in: Boolean): Update[State, Nothing, Boolean] =
      Update(in)
    def view(s: State): View[Boolean] =
      el("div")(
        el("div")(mountIn("left")),
        el("div")(mountIn("right")))
    override def children(s: State) = Vector(
      Child(if (s) "l2" else "l1", Leaf, s"left-$s",
        (o: Nothing) => o, slot = "left"),
      Child("r", Leaf, "right", (o: Nothing) => o, slot = "right"))
  }

  private def mountEl(container: dom.Node, which: Int): dom.Element =
    container.childNodes(0).asInstanceOf[FakeElement]
      .children(which).asInstanceOf[FakeElement]
      .children(0).asInstanceOf[dom.Element]

  test("named slots hold separate children; one slot's change is contained") {
    Leaf.unmounted = Nil
    val c = container()
    val h = Runtime.mount(c, Slots)
    assertEquals(
      mountEl(c, 0).childNodes(0).textContent, "left-false")
    assertEquals(
      mountEl(c, 1).childNodes(0).textContent, "right")
    h.send(true)
    // left's key changed: its instance was replaced; right untouched.
    assertEquals(Leaf.unmounted, List("left-false"))
    assertEquals(
      mountEl(c, 0).childNodes(0).textContent, "left-true")
    assertEquals(
      mountEl(c, 1).childNodes(0).textContent, "right")
  }
}

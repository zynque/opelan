package opelan.ui.editor

import scala.scalajs.js
import org.scalajs.dom
import opelan.foundation.document._

// A minimal fake DOM (Scala.js-defined JS classes, so method names stay
// unmangled for the fp runtime's raw property calls) implementing
// focus/blur semantics: focusing an element blurs the previous one, and
// detaching a focused element blurs it too. That lets the suite drive
// the real Runtime/Patch/Instance through the synced-Enter path — insert,
// then the echo SyncDocument push — and verify the inline editor stays
// mounted and focused.
class FakeNode extends js.Object {
  var parentNode: dom.Node = _
  var textContent: String = _

  // Connected iff the ancestor chain reaches a root registered with
  // FakeDom (the container passed to Runtime.mount).
  def isConnected: Boolean = {
    var n: FakeNode = this
    while (n != null && n.parentNode != null)
      n = n.parentNode.asInstanceOf[FakeNode]
    n != null && FakeDom.roots.contains(n.asInstanceOf[dom.Node])
  }
}

class FakeElement(val tag: String) extends FakeNode {
  val attrs = js.Dictionary.empty[String]
  val children = js.Array[dom.Node]()
  val listeners = js.Dictionary.empty[js.Function1[dom.Event, Any]]
  var value = ""
  var id = ""

  def setAttribute(k: String, v: String): Unit = {
    attrs(k) = v
    if (k == "id") id = v
    if (k == "value") value = v
  }
  def removeAttribute(k: String): Unit = { attrs -= k; if (k == "id") id = "" }

  def appendChild(n: dom.Node): dom.Node = {
    insertBefore(n, null)
    n
  }
  def insertBefore(n: dom.Node, ref: dom.Node): dom.Node = {
    if (n != null && n.parentNode != null)
      n.parentNode.asInstanceOf[FakeElement].detach(n)
    if (n != null)
      n.asInstanceOf[FakeNode].parentNode = this.asInstanceOf[dom.Node]
    val i = if (ref == null) -1 else children.indexOf(ref)
    if (i < 0) children.push(n) else children.splice(i, 0, n)
    n
  }
  def removeChild(n: dom.Node): dom.Node = { detach(n); n }
  def replaceChild(n: dom.Node, old: dom.Node): dom.Node = {
    val i = indexOf(this.asInstanceOf[dom.Element], old)
    detach(old)
    n.asInstanceOf[FakeNode].parentNode = this.asInstanceOf[dom.Node]
    children(i) = n
    old
  }
  def childNodes: js.Array[dom.Node] = children
  def addEventListener(n: String, f: js.Function1[dom.Event, Any]): Unit =
    listeners(n) = f
  def dispatch(n: String, e: dom.Event): Unit =
    listeners.get(n).foreach(_(e))
  def focus(): Unit = FakeDom.activate(this.asInstanceOf[dom.HTMLElement])
  def select(): Unit = ()
  def scrollIntoView(x: Any): Unit = ()

  private def indexOf(parent: dom.Node, n: dom.Node): Int =
    parent.asInstanceOf[FakeElement].children.indexOf(n)

  def detach(n: dom.Node): Unit = {
    children.splice(children.indexOf(n), 1)
    n.asInstanceOf[FakeNode].parentNode = null
    // Blur fires if the detached subtree contains the active element —
    // browsers focus the body when the focused node leaves the document,
    // which is what a reparenting move looks like mid-flight.
    if (FakeDom.active != null && contains(n, FakeDom.active)) {
      val prev = FakeDom.active
      FakeDom.active = null
      prev.asInstanceOf[FakeElement].dispatch(
        "blur", FakeDom.blurEvent(prev.asInstanceOf[FakeElement]))
    }
  }

  private def contains(root: dom.Node, n: dom.Node): Boolean =
    root == n || ((root: Any) match {
      case e: FakeElement => e.children.exists(c => contains(c, n))
      case _              => false
    })
}

class FakeText extends FakeNode

class FakeDocument extends js.Object {
  def activeElement: dom.Element = FakeDom.active.asInstanceOf[dom.Element]
  def createElement(tag: String): dom.Element =
    new FakeElement(tag).asInstanceOf[dom.Element]
  def createTextNode(t: String): dom.Node = {
    val n = new FakeText
    n.textContent = t
    n.asInstanceOf[dom.Node]
  }
}

object FakeDom {
  var active: dom.Node = _
  var installed = false
  val roots = scala.collection.mutable.Set.empty[dom.Node]

  def registerRoot(n: dom.Node): Unit = roots += n

  def activate(el: dom.HTMLElement): Unit = {
    val prev = active
    active = el
    if (prev != null && prev != el)
      prev.asInstanceOf[FakeElement].dispatch(
        "blur", blurEvent(prev.asInstanceOf[FakeElement]))
  }

  def blurEvent(el: FakeElement): dom.Event =
    js.Dynamic.literal("type" -> "blur", "target" -> el)
      .asInstanceOf[dom.Event]

  def install(): Unit = {
    js.Dynamic.global.globalThis.document = new FakeDocument()
    // focusNow pattern-matches on HTMLInputElement — provide a marker
    // instanceof so `input` elements qualify.
    js.eval(
      """globalThis.HTMLInputElement = {
        [Symbol.hasInstance](x) { return x != null && x.tag === "input"; }
      };""")
    installed = true
  }
}

// A minimal stand-in for the TypedDoc pane: pushes the latest
// SyncDocument down into a real DocumentEditor child through the real
// Reconcile path, so parent refreshes exercise the same reparent/send
// behaviour as the live workbench.
object EchoPane extends opelan.ui.fp.Component[EchoPane.Input, EditorOutput] {
  import opelan.ui.fp.{Child, Update}
  import opelan.ui.fp.Dsl._

  enum Input {
    case Push(sd: EditorInput.SyncDocument)
    case FromEditor(o: EditorOutput)
  }

  type State = EditorInput.SyncDocument

  def init: State = EditorInput.SyncDocument(EditorSample.doc)

  def update(s: State, i: Input): Update[State, EditorOutput] =
    i match {
      case Input.Push(sd)     => Update(sd)
      case Input.FromEditor(o) => Update(s, Vector(o))
    }

  def view(s: State): opelan.ui.fp.View[Input] = el("div")(mount)

  override def children(s: State) = Vector(
    Child("editor", DocumentEditor, s,
      (o: EditorOutput) => Input.FromEditor(o)))
}

class EditorDomSuite extends munit.FunSuite {

  private def keydown(el: dom.Element, k: String): Unit =
    el.asInstanceOf[FakeElement].dispatch(
      "keydown",
      js.Dynamic.literal(
        "key" -> k, "ctrlKey" -> false, "metaKey" -> false,
        "shiftKey" -> false, "target" -> el,
        "preventDefault" -> (() => ()), "stopPropagation" -> (() => ()))
        .asInstanceOf[dom.Event])

  private def findId(root: dom.Node, id: String): Option[dom.Element] = {
    def walk(n: dom.Node): Option[dom.Element] =
      (n: Any) match {
        case e: FakeElement if e.id == id => Some(e.asInstanceOf[dom.Element])
        case e: FakeElement =>
          e.children.iterator.map(walk).collectFirst { case Some(x) => x }
        case _ => None
      }
    walk(root)
  }

  test("synced insert box survives a remote push and commits once") {
    try go() catch {
      case e: js.JavaScriptException =>
        println(js.Dynamic.global.JSON.stringify(
          e.exception.asInstanceOf[js.Dynamic].stack))
        throw e
    }
  }

  private def go(): Unit = {
    if (!FakeDom.installed) FakeDom.install()
    val container = new FakeElement("div")
    FakeDom.registerRoot(container.asInstanceOf[dom.Node])
    val h = opelan.ui.fp.Runtime.mount(
      container.asInstanceOf[dom.Element], EchoPane)
    // sync is already active: a history push established syncedHead
    val d0 = EditorSample.doc
    val h0 = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "init", d0)), Vector("h1"))
    h.send(EchoPane.Input.Push(
      EditorInput.SyncDocument(d0, history = Some(h0))))
    // Enter opens the phantom edit box — the node is not created yet
    val outline = findId(container.asInstanceOf[dom.Node], "doc-outline").get
    keydown(outline, "Enter")
    val input0 = findId(container.asInstanceOf[dom.Node], "doc-edit-input")
    assert(input0.isDefined, "edit box should open on Enter")
    assert(input0.exists(_.asInstanceOf[dom.Node] == FakeDom.active),
      "edit input should be focused")
    // typing is tracked as the phantom's draft
    input0.foreach(i => i.asInstanceOf[FakeElement].value = "int")
    input0.foreach(i => i.asInstanceOf[FakeElement].dispatch(
      "input",
      js.Dynamic.literal("type" -> "input", "target" -> i)
        .asInstanceOf[dom.Event]))
    // a remote change pushed while the box is open must not close it:
    // the reparsed doc reassigns node ids and the parent refresh
    // reparents the editor subtree — neither may blur the input away
    val remote = Edit.insertNode(
      DetachedNode.leaf(NodeData.StringData("peer")),
      d0.rootId, 0, d0).toOption.get
    val pushed = Outline.parse(Outline.render(remote)).get
    val hist = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "init", d0),
             SyncedEntry("h2", Vector("h1"), "calm-otter", pushed)),
      Vector("h2"))
    h.send(EchoPane.Input.Push(
      EditorInput.SyncDocument(pushed, history = Some(hist))))
    val input1 = findId(container.asInstanceOf[dom.Node], "doc-edit-input")
    if (input1.isEmpty) println(dump(container.asInstanceOf[dom.Node]))
    assert(input1.isDefined, "edit box should survive a remote push")
    assert(input1.exists(_.asInstanceOf[dom.Node] == FakeDom.active),
      "edit input should still be focused")
    assertEquals(
      input1.map(_.asInstanceOf[FakeElement].value), Some("int"))
    // finish the name and press Enter — the node materializes once
    input1.foreach(i => i.asInstanceOf[FakeElement].value = "intro")
    input1.foreach(i => i.asInstanceOf[FakeElement].dispatch(
      "input",
      js.Dynamic.literal("type" -> "input", "target" -> i)
        .asInstanceOf[dom.Event]))
    keydown(input1.get, "Enter")
    assert(
      findId(container.asInstanceOf[dom.Node], "doc-edit-input").isEmpty,
      "edit box should close on commit")
    // StringData renders quoted
    if (!hasText(container.asInstanceOf[dom.Node], "\"intro\""))
      println(dump(container.asInstanceOf[dom.Node]))
    assert(hasText(container.asInstanceOf[dom.Node], "\"intro\""),
      "committed node should appear with its text")
  }

  private def dump(root: dom.Node, indent: String = ""): String =
    (root: Any) match {
      case e: FakeElement =>
        s"$indent<${e.tag} id=${e.id}>\n" +
          e.children.map(c => dump(c, indent + "  ")).mkString
      case t: FakeText => s"$indent'${t.textContent}'\n"
      case _           => s"$indent?\n"
    }

  private def hasText(root: dom.Node, s: String): Boolean =
    (root: Any) match {
      case t: FakeText    => t.textContent == s
      case e: FakeElement => e.children.exists(c => hasText(c, s))
      case _              => false
    }
}

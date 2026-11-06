package opelan.ui.fp

import opelan.ui.fp.Dsl._

// A minimal leaf/list pair standing in for an app component: the leaf's
// update emits its new value, and the list's desired children track its
// state, routing each child's output back as its own input.
enum ItemInput:
  case Bump
  case Set(n: Int)

enum ItemOutput:
  case Changed(value: Int)

private object Item extends Component[ItemInput, ItemOutput] {
  type State = Int
  def init: State = 0
  def update(s: State, in: ItemInput): Update[State, ItemOutput, ItemInput] =
    in match {
      case ItemInput.Bump   =>
        Update(s + 1, Vector(ItemOutput.Changed(s + 1)))
      case ItemInput.Set(n) => Update(n, Vector(ItemOutput.Changed(n)))
    }
  def view(s: State): View[ItemInput] = text(s.toString)
}

enum ListInput:
  case Add
  case Remove(id: Int)
  case FromItem(id: Int, out: ItemOutput)

enum ListOutput:
  case Summary(total: Int, count: Int)

case class ListState(items: Vector[Int], values: Map[Int, Int], nextId: Int)

private object ItemList extends Component[ListInput, ListOutput] {
  type State = ListState
  def init: State = ListState(Vector.empty, Map.empty, 0)
  def update(s: State, in: ListInput): Update[State, ListOutput, ListInput] =
    in match {
      case ListInput.Add =>
        changed(s.copy(items = s.items :+ s.nextId, nextId = s.nextId + 1))
      case ListInput.Remove(id) =>
        changed(s.copy(
          items = s.items.filterNot(_ == id), values = s.values - id))
      case ListInput.FromItem(id, ItemOutput.Changed(v)) =>
        changed(s.copy(values = s.values + (id -> v)))
    }
  private def changed(s: State) =
    Update(s, Vector(ListOutput.Summary(
      s.values.values.sum, s.items.length)))
  def view(s: State): View[ListInput] = mount
  override def children(s: State): Vector[Child[?, ?, ListInput]] =
    s.items.map { id =>
      Child(id.toString, Item, ItemInput.Set(s.values.getOrElse(id, 0)),
        o => ListInput.FromItem(id, o))
    }
}

class ComponentSuite extends munit.FunSuite {

  test("a leaf's update emits its new value") {
    val Update(next, out, _) = Item.update(3, ItemInput.Bump)
    assertEquals(next, 4)
    assertEquals(out, Vector(ItemOutput.Changed(4)))
  }

  test("a leaf accepts external input as props") {
    val Update(next, out, _) = Item.update(0, ItemInput.Set(42))
    assertEquals(next, 42)
    assertEquals(out, Vector(ItemOutput.Changed(42)))
  }

  test("a list grows its desired children on add") {
    val s0 = ItemList.init
    val s1 = ItemList.update(s0, ListInput.Add).state
    val s2 = ItemList.update(s1, ListInput.Add).state

    val children = ItemList.children(s2)
    assertEquals(children.map(_.key), Vector("0", "1"))
    assert(children.forall(_.component eq Item))
  }

  test("a list shrinks its desired children on remove") {
    val s0 = ItemList.init
    val s1 = ItemList.update(s0, ListInput.Add).state
    val s2 = ItemList.update(s1, ListInput.Add).state
    val s3 = ItemList.update(s2, ListInput.Remove(0)).state

    assertEquals(ItemList.children(s3).map(_.key), Vector("1"))
  }

  test("child onOutput routes into the parent's input type") {
    val s = ItemList.update(ItemList.init, ListInput.Add).state
    val child = ItemList.children(s).head
    val route = child.onOutput.asInstanceOf[ItemOutput => ListInput]
    assertEquals(
      route(ItemOutput.Changed(7)),
      ListInput.FromItem(0, ItemOutput.Changed(7)))
  }

  test("child input carries the parent's view of the child's value") {
    val s0 = ItemList.init
    val s1 = ItemList.update(s0, ListInput.Add).state
    val s2 = ItemList.update(
      s1, ListInput.FromItem(0, ItemOutput.Changed(9))).state
    val child = ItemList.children(s2).head
    assertEquals(child.input.asInstanceOf[ItemInput], ItemInput.Set(9))
  }

  test("a list emits a summary output on each change") {
    val s = ItemList.update(ItemList.init, ListInput.Add)
    assertEquals(s.out, Vector(ListOutput.Summary(0, 1)))
  }

  test("subject emits to subscribers in order") {
    val subject = new Subject[Int]
    val seen = scala.collection.mutable.ListBuffer.empty[Int]
    subject.subscribe(seen += _)
    subject.emit(1)
    subject.emit(2)
    assertEquals(seen.toList, List(1, 2))
  }
}

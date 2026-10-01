package opelan.ui.fp

import opelan.ui.fp.demo._

class ComponentSuite extends munit.FunSuite {

  test("counter increments and emits its new value") {
    val Update(next, out) = Counter.update(3, CounterInput.Increment)
    assertEquals(next, 4)
    assertEquals(out, Vector(CounterOutput.Changed(4)))
  }

  test("counter accepts external input as props") {
    val Update(next, out) = Counter.update(0, CounterInput.SetValue(42))
    assertEquals(next, 42)
    assertEquals(out, Vector(CounterOutput.Changed(42)))
  }

  test("counter list grows its desired children on add") {
    val s0 = CounterList.init
    val s1 = CounterList.update(s0, CounterListInput.AddCounter).state
    val s2 = CounterList.update(s1, CounterListInput.AddCounter).state

    val children = CounterList.children(s2)
    assertEquals(children.map(_.key), Vector("0", "1"))
    assert(children.forall(_.component eq Counter))
  }

  test("counter list shrinks its desired children on remove") {
    val s0 = CounterList.init
    val s1 = CounterList.update(s0, CounterListInput.AddCounter).state
    val s2 = CounterList.update(s1, CounterListInput.AddCounter).state
    val s3 = CounterList.update(s2, CounterListInput.RemoveCounter(0)).state

    assertEquals(CounterList.children(s3).map(_.key), Vector("1"))
  }

  test("child onOutput routes into the parent's input type") {
    val s = CounterList.update(CounterList.init, CounterListInput.AddCounter).state
    val child = CounterList.children(s).head
    val route = child.onOutput.asInstanceOf[CounterOutput => CounterListInput]
    assertEquals(
      route(CounterOutput.Changed(7)),
      CounterListInput.FromCounter(0, CounterOutput.Changed(7)))
  }

  test("child input carries the parent's view of the child's value") {
    val s0 = CounterList.init
    val s1 = CounterList.update(s0, CounterListInput.AddCounter).state
    val s2 = CounterList.update(
      s1, CounterListInput.FromCounter(0, CounterOutput.Changed(9))).state
    val child = CounterList.children(s2).head
    assertEquals(child.input.asInstanceOf[CounterInput], CounterInput.SetValue(9))
  }

  test("list emits a summary output on each change") {
    val s = CounterList.update(CounterList.init, CounterListInput.AddCounter)
    assertEquals(s.out, Vector(CounterListOutput.Summary(0, 1)))
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

package opelan.foundation.version

import opelan.foundation.document._
import VersionTree._

class VersionSuite extends munit.FunSuite {

  val initialVersionDoc = Build.beginDocument(Version.buildRootVersionNode("0a"))

  //       0("0a")
  //      /        \
  //  1("1a")    2("1b")
  //            /      \
  //       4("2b")    3("2a")   -- children prepended: insert index 0
  val largerExample = for {
    d1 <- update(0, "1a", initialVersionDoc)
    d2 <- update(0, "1b", d1)
    d3 <- update(2, "2a", d2)
    d4 <- update(2, "2b", d3)
  } yield d4

  test("update extends branch with new version node") {
    val actual = update(0, "1a", initialVersionDoc)
      .flatMap(_.getNode(1).toRight("node not found"))
    assertEquals(actual, Right(Node(0, Version(None, "1a", Some(0)), Nil, Some(0))))
  }

  test("update adds new branch") {
    val actual = for {
      d1 <- update(0, "1a", initialVersionDoc)
      d2 <- update(1, "1b", d1)
      n2 <- d2.getNode(2).toRight("node not found")
    } yield n2
    assertEquals(actual, Right(Node(0, Version(None, "1b", Some(1)), Nil, Some(1))))
  }

  test("update extends branch further with correct lsa") {
    val lsa = largerExample
      .flatMap(_.getNode(4).toRight("no version"))
      .flatMap(_.data.lsaNodeId.toRight("no lsa"))
    assertEquals(lsa, Right(2))
  }

  test("lastCommonElement returns last common element") {
    assertEquals(lastCommonElement(List(1, 2, 3, 4, 5), List(1, 2, 3, 6, 7)), Some(3))
  }

  test("lastCommonElement returns None when no common element") {
    assertEquals(lastCommonElement(List(1, 2, 3), List(4, 5)), None)
  }

  test("lastCommonElement returns None for empty lists") {
    assertEquals(lastCommonElement(List.empty[Int], List.empty[Int]), None)
  }

  test("lsaPathFromRootTo returns correct path (0 1)") {
    assertEquals(largerExample.map(lsaPathFromRootTo(1, _)), Right(List(0, 1)))
  }

  test("lsaPathFromRootTo returns correct path (0 2 4)") {
    assertEquals(largerExample.map(lsaPathFromRootTo(4, _)), Right(List(0, 2, 4)))
  }

  test("lowest single common ancestor of two nodes with same parent") {
    assertEquals(largerExample.map(d => getLsca(3, 4, d)), Right(Some(2)))
  }

  test("lowest single common ancestor of two nodes further apart") {
    assertEquals(largerExample.map(d => getLsca(1, 3, d)), Right(Some(0)))
  }

  test("lsa of merged node is lsca of parents") {
    val res = for {
      d <- largerExample
      m <- merge(1, 3, "3a", d)
      v <- getVersion(5, m).toRight("no version")
      lsa <- v.lsaNodeId.toRight("no lsa")
    } yield lsa
    assertEquals(res, Right(0))
  }
}

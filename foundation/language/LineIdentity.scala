package opelan.foundation.language

import opelan.foundation.document._

// The text surface's line identity: which document node each line of
// the editor's text continues, carried across keystrokes. A line keeps
// its node while any of its original characters remain — text typed in
// front of `a * b means mul(a, b)` doesn't make it a new rule — and
// when a line splits, the part holding most of them keeps the node.
// Characters typed since the line took its node belong to no node;
// a line left with none of the originals is a new line.
case class TrackedLine(text: String, id: Option[Int], original: Vector[Boolean])

case class LineIdentity(lines: List[TrackedLine]) {
  def text: String = lines.map(_.text).mkString("\n")
  def ids: List[Option[Int]] = lines.map(_.id)

  // The identity after the surface's text became `next`: the edit is
  // the span between the texts' common prefix and suffix; characters
  // outside it keep their provenance, characters inside are new.
  def retext(next: String): LineIdentity = {
    val before = text
    val prefix = before.zip(next).takeWhile(p => p._1 == p._2).length
    val suffix = before.drop(prefix).reverse.zip(next.drop(prefix).reverse)
      .takeWhile(p => p._1 == p._2).length
    val provenance = charProvenance.take(prefix) ++
      Vector.fill(next.length - prefix - suffix)(None) ++
      charProvenance.drop(before.length - suffix)
    val split = splitLines(next, provenance)
    val counts = for {
      ((_, prov), at) <- split.zipWithIndex
      (from, n) <- prov.flatten.collect { case (l, true) => l }
        .groupBy(identity).map((l, ls) => l -> ls.size)
    } yield (at, from, n)
    val owner = counts.sortBy { case (at, _, n) => (-n, at) }
      .foldLeft(Map.empty[Int, Int]) { case (acc, (at, from, _)) =>
        if (acc.contains(at) || acc.values.exists(_ == from)) acc
        else acc + (at -> from)
      }
    LineIdentity(split.zipWithIndex.map { case ((t, prov), at) =>
      owner.get(at) match {
        case Some(from) => TrackedLine(t, lines(from).id,
          prov.map(_.exists(p => p._1 == from && p._2)))
        case None => TrackedLine(t, None, Vector.fill(t.length)(false))
      }
    })
  }

  // Lines without a node take the committed document's: the k-th
  // nonblank line prints the k-th line id, when the counts agree.
  def adopt(lineIds: List[Int]): LineIdentity = {
    val nonblank = lines.indices.filter(i => lines(i).text.trim.nonEmpty)
    if (nonblank.length != lineIds.length) this
    else {
      val ids = nonblank.zip(lineIds).toMap
      LineIdentity(lines.zipWithIndex.map { case (l, i) =>
        if (l.id.isDefined) l
        else ids.get(i).fold(l)(id =>
          TrackedLine(l.text, Some(id), Vector.fill(l.text.length)(true)))
      })
    }
  }

  // Each character's (line, original?) — None for line breaks.
  private def charProvenance: Vector[Option[(Int, Boolean)]] =
    lines.zipWithIndex.flatMap { case (l, li) =>
      l.text.indices.map(c => Some((li, l.original(c)))) :+ None
    }.dropRight(1).toVector
}

object LineIdentity {
  // A freshly derived surface: every character original, each nonblank
  // line the node `lineIds` names for it.
  def derived(text: String, lineIds: List[Int]): LineIdentity =
    LineIdentity(text.split("\n", -1).toList.map(t =>
      TrackedLine(t, None, Vector.fill(t.length)(true)))).adopt(lineIds)
}

private def splitLines(
    text: String, provenance: Vector[Option[(Int, Boolean)]])
    : List[(String, Vector[Option[(Int, Boolean)]])] = {
  val breaks = text.indices.filter(text(_) == '\n').toList
  val starts = 0 :: breaks.map(_ + 1)
  val ends = breaks :+ text.length
  starts.zip(ends).map((s, e) => (text.substring(s, e), provenance.slice(s, e)))
}

// A text-surface edit through the surface's line identity: `identity`
// (re-derived from `doc` when it no longer describes `before`) advanced
// to `text`, its ids hinting the commit; new lines then adopt the
// committed document's ids. A language without line structure commits
// untracked.
def applyTrackedText(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    before: String,
    identity: Option[LineIdentity],
    text: String): (Either[String, Document[NodeData]], Option[LineIdentity]) =
  languageFor(doc).map(_.textLineIds(doc)).filter(_.nonEmpty) match {
    case None => (applyDocText(doc, languageFor, text), None)
    case Some(lineIds) =>
      val current = identity.filter(_.text == before)
        .getOrElse(LineIdentity.derived(before, lineIds))
      val next = current.retext(text)
      val applied = applyDocText(doc, languageFor, text, next.ids)
      (applied, Some(applied.toOption.flatMap(d =>
        languageFor(d).map(l => next.adopt(l.textLineIds(d))))
        .getOrElse(next)))
  }

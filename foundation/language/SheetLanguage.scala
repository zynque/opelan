package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// The sheet language: a grid of labeled cells whose formula cells are
// expressions over refs to other cells (the demo's "Budget" app). The
// document is the app — the "sheet" view computes it, "formulas" shows
// the machinery.
object SheetLanguage extends Language {

  val typeRef = ExternalNodeReference("opelan:languages/sheet", 0, 0)

  def name = "Sheet"

  def views = List("sheet", "formulas")

  def render(view: String, doc: Document[NodeData]): Either[String, DetachedNode[NodeData]] =
    view match {
      case "sheet"    => content(doc).map(sheetView(doc, _))
      case "formulas" => content(doc).map(formulasView(doc, _))
      case v          => Left(s"$name has no '$v' view")
    }

  private def content(doc: Document[NodeData]): Either[String, Int] =
    contentId(doc).toRight(s"$name: typed document has no content")

  // Cells are editable projections: committed text parses per the
  // sheet's cell grammar (formula, ref, literal, hole, or label).
  override def parseCell(text: String): Option[DetachedNode[NodeData]] =
    Some(parseCellText(text))

  // The demo's app: a small household budget. Cells are positional;
  // formula refs are InternalNodeRefs to other cells, seeded here with
  // "r:<row>,<col>" placeholders resolved against the built document.
  def sampleDoc: Document[NodeData] = {
    val doc0 = makeTyped(typeRef, NodeData.StringData("Household budget"), sheet)
    wireRefs(doc0).toOption.getOrElse(doc0)
  }

  // Placeholder "r:row,col" cell refs (as written in `sheet` below)
  // become InternalNodeRefs to that cell's node.
  private val RefPat = "r:(\\d+),(\\d+)".r

  private def ref(r: Int, c: Int): DetachedNode[NodeData] = sl(s"r:$r,$c")

  private def wireRefs(doc: Document[NodeData]): Either[String, Document[NodeData]] = {
    val sheetId = contentId(doc).get
    val rows = doc.childrenOf(sheetId)
    def cellAt(r: Int, c: Int) = doc.childrenOf(rows(r)).lift(c)
    val fixes = doc.nodes.indices.flatMap { id =>
      doc.nodes(id).data match {
        case NodeData.StringData(RefPat(r, c)) => cellAt(r.toInt, c.toInt).map(id -> _)
        case _                                 => None
      }
    }
    fixes.foldLeft[Either[String, Document[NodeData]]](Right(doc)) {
      case (acc, (id, target)) =>
        acc.flatMap(d => updateNodeData(id, NodeData.InternalNodeRef(target), d))
    }
  }

  private def sheet: DetachedNode[NodeData] =
    n(s(SheetTag),
      n(s(HRowTag), sl("item"), sl("qty"), sl("price"), sl("line total")),
      n(s(RowTag), sl("Apples"), il(4), fl(1.20), n(s("mul"), ref(1, 1), ref(1, 2))),
      n(s(RowTag), sl("Bread"),  il(2), fl(2.50), n(s("mul"), ref(2, 1), ref(2, 2))),
      n(s(RowTag), sl("Coffee"), il(1), fl(6.40), n(s("mul"), ref(3, 1), ref(3, 2))),
      n(s(RowTag), sl("total"), sl(""), sl(""),
        n(s("add"),
          n(s("add"), ref(1, 3), ref(2, 3)),
          ref(3, 3))),
      n(s(RowTag), sl("people"), il(2), sl(""), sl("")),
      n(s(RowTag), sl("per person"), sl(""), sl(""),
        n(s("div"), ref(4, 3), ref(5, 1))))

  // The language document is the encoded definition itself — its
  // `cells` section names the expression language by ref, so following
  // it lands on lang: expression (and DocLanguage resolves it at step
  // 10).
  def langDoc: Document[NodeData] =
    opelan.foundation.langdef.langDefDoc(
      opelan.foundation.langdef.sheetDef)
}

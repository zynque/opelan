package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.language.Language
import opelan.foundation.langdef.ViewContext

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
      case "sheet"    => content(doc).map(sheetView(doc, _, context(view)))
      case "formulas" => content(doc).map(formulasView(doc, _, context(view)))
      case v          => Left(s"$name has no '$v' view")
    }

  // The same settings the encoded definition carries, so the registry
  // fallback renders like the stored lang: sheet.
  private def context(view: String): ViewContext =
    ViewContext(
      sheetDef.views.find(_.name == view).map(_.settings).getOrElse(Map.empty),
      parseCell)

  private def content(doc: Document[NodeData]): Either[String, Int] =
    contentId(doc).toRight(s"$name: typed document has no content")

  // Cells are editable projections: committed text parses per the
  // sheet's cell grammar (formula, ref, literal, hole, or label).
  override def parseCell(text: String): Option[DetachedNode[NodeData]] =
    Some(parseCellText(text))

  // The demo's app: a small household budget. Cells are positional;
  // formula refs are InternalNodeRefs to other cells, seeded here with
  // "r:<row>,<col>" placeholders resolved against the built document.
  // `per person` is seeded as a hole holding formula text: it computes
  // once the cell grammar (lang: expression) can read `/`.
  def sampleDoc: Document[NodeData] = {
    val doc0 = makeTyped(typeRef,
      er(opelan.foundation.langdef.docTitleRef),
      er(opelan.foundation.langdef.docImportsRef),
      NodeData.StringData("Household budget"), sheet)
    wireRefs(doc0).toOption.getOrElse(doc0)
  }

  // Placeholder "r:row,col" cell refs (as written in `sheet` below)
  // become InternalNodeRefs to that cell's node.
  private val RefPat = "r:(\\d+),(\\d+)".r

  private def ref(r: Int, c: Int): DetachedNode[NodeData] = sl(s"r:$r,$c")

  private def wireRefs(doc: Document[NodeData]): Either[String, Document[NodeData]] = {
    val sheetId = contentId(doc).get
    val rows = doc.childrenOf(sheetId)
    def cellAt(r: String, c: String) = doc.childrenOf(rows(r.toInt)).lift(c.toInt)
    val fixes = doc.nodes.indices.flatMap { id =>
      doc.nodes(id).data match {
        case NodeData.StringData(RefPat(r, c)) =>
          cellAt(r, c).map(id -> NodeData.InternalNodeRef(_))
        case NodeData.GapData(t) =>
          Some(id -> NodeData.GapData(RefPat.replaceAllIn(t, m =>
            cellAt(m.group(1), m.group(2)).fold(m.matched)(c => s"ref:$c"))))
        case _ => None
      }
    }
    fixes.foldLeft[Either[String, Document[NodeData]]](Right(doc)) {
      case (acc, (id, data)) => acc.flatMap(d => updateNodeData(id, data, d))
    }
  }

  private def sheet: DetachedNode[NodeData] =
    n(sheetTag(SheetTag),
      n(sheetTag(HRowTag), sl("item"), sl("qty"), sl("price"), sl("line total")),
      n(sheetTag(RowTag), sl("Apples"), il(4), fl(1.20), n(exprTag("mul"), ref(1, 1), ref(1, 2))),
      n(sheetTag(RowTag), sl("Bread"),  il(2), fl(2.50), n(exprTag("mul"), ref(2, 1), ref(2, 2))),
      n(sheetTag(RowTag), sl("Coffee"), il(1), fl(6.40), n(exprTag("mul"), ref(3, 1), ref(3, 2))),
      n(sheetTag(RowTag), sl("total"), sl(""), sl(""),
        n(exprTag("add"),
          n(exprTag("add"), ref(1, 3), ref(2, 3)),
          ref(3, 3))),
      n(sheetTag(RowTag), sl("people"), il(2), sl(""), sl("")),
      n(sheetTag(RowTag), sl("per person"), sl(""), sl(""),
        DetachedNode.leaf(NodeData.GapData("r:4,3 / r:5,1"))))

  // The language document is the encoded definition itself — its
  // `cells` section names the expression language by ref, so following
  // it lands on lang: expression (and DocLanguage resolves it at step
  // 10).
  def langDoc: Document[NodeData] =
    opelan.foundation.langdef.langDefDoc(sheetDef)
}

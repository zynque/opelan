package opelan.ui.text

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

// ES module namespaces, driven dynamically — the workbench only needs a
// mounted editor that reports document changes. Note: every package must
// come from the top-level 6.x tree; mixing in 0.x-era packages (like
// @codemirror/basic-setup, which pins state/view 0.20.x) loads a second
// copy of @codemirror/state and breaks its instanceof extension checks.
@js.native @JSImport("@codemirror/view", JSImport.Namespace)
private object CMView extends js.Object

@js.native @JSImport("@codemirror/commands", JSImport.Namespace)
private object CMCommands extends js.Object

@js.native @JSImport("@codemirror/language", JSImport.Namespace)
private object CMLanguage extends js.Object

@js.native @JSImport("@codemirror/state", JSImport.Namespace)
private object CMState extends js.Object

// Thin wrapper over CodeMirror 6: a mounted EditorView that calls back on
// every document-changing transaction, with an optional line-decorating
// extension — the host paints what its surface's text parses as.
object CodeMirror {

  private def call0(f: js.Dynamic): js.Dynamic =
    f.asInstanceOf[js.Function0[js.Dynamic]]()

  private val viewMod = CMView.asInstanceOf[js.Dynamic]
  private val cmdMod = CMCommands.asInstanceOf[js.Dynamic]
  private val langMod = CMLanguage.asInstanceOf[js.Dynamic]
  private val stateMod = CMState.asInstanceOf[js.Dynamic]

  // One line's presentation: a whole-line class ("" for none) and
  // character-offset spans within the line's text.
  type LineMark = (String, List[(Int, Int, String)])

  // Mount an editor under `parent` showing `doc`; `onChange` fires with
  // the full text on each doc-changing transaction (i.e. per keystroke).
  // The returned handle can destroy the editor.
  def mount(parent: dom.Element, doc: String, onChange: String => Unit)
      : Handle =
    mountMarked(parent, doc, onChange, _ => Nil)

  // `marks` classifies the document's lines (its list index-aligned with
  // the text's lines); it runs on mount and after every doc change, so
  // the paint follows the text itself — not the committed document.
  def mountMarked(parent: dom.Element, doc: String,
      onChange: String => Unit, marks: String => List[LineMark])
      : Handle = {
    val listener = viewMod.EditorView.updateListener.of(
      js.Any.fromFunction1 { (v: js.Dynamic) =>
        if (v.docChanged.asInstanceOf[Boolean])
          onChange(v.state.doc.toString.asInstanceOf[String])
      })
    val keymap = viewMod.keymap.of(
      cmdMod.defaultKeymap.asInstanceOf[js.Array[js.Any]]
        .concat(cmdMod.historyKeymap.asInstanceOf[js.Array[js.Any]])
        .concat(js.Array(cmdMod.indentWithTab)))
    val view = js.Dynamic.newInstance(viewMod.EditorView)(js.Dynamic.literal(
      "doc" -> doc,
      "extensions" -> js.Array(
        call0(viewMod.lineNumbers),
        call0(viewMod.highlightActiveLine),
        viewMod.EditorView.lineWrapping,
        call0(cmdMod.history),
        call0(langMod.bracketMatching),
        keymap,
        listener,
        decorationField(marks),
        marksTheme),
      "parent" -> parent))
    new Handle(view)
  }

  final class Handle(private val view: js.Dynamic) {
    def text: String = view.state.doc.toString.asInstanceOf[String]
    def destroy(): Unit = view.destroy()
  }

  // A decorations field recomputed per edit: each line's mark maps to a
  // line decoration and its spans to mark decorations.
  private def decorationField(marks: String => List[LineMark]): js.Dynamic =
    stateMod.StateField.define(js.Dynamic.literal(
      "create" -> js.Any.fromFunction1((s: js.Dynamic) =>
        decorations(s.doc.toString.asInstanceOf[String], marks)),
      "update" -> js.Any.fromFunction2((deco: js.Dynamic, tr: js.Dynamic) =>
        if (tr.docChanged.asInstanceOf[Boolean])
          decorations(tr.state.doc.toString.asInstanceOf[String], marks)
        else deco),
      "provide" -> js.Any.fromFunction1((f: js.Dynamic) =>
        viewMod.EditorView.decorations.from(f))))

  private def decorations(
      text: String, marks: String => List[LineMark]): js.Dynamic = {
    val lines = text.split("\n", -1)
    val ranges = js.Array[js.Dynamic]()
    var offset = 0
    marks(text).zip(lines).foreach { case ((cls, spans), line) =>
      if (cls.nonEmpty)
        ranges.push(viewMod.Decoration.line(js.Dynamic.literal(
          "attributes" -> js.Dynamic.literal("class" -> cls))).range(offset))
      spans.foreach { case (from, to, spanClass) =>
        ranges.push(viewMod.Decoration.mark(js.Dynamic.literal(
          "attributes" -> js.Dynamic.literal("class" -> spanClass)))
          .range(offset + from, offset + to)) }
      offset += line.length + 1
    }
    viewMod.Decoration.set(ranges, true)
  }

  // The mark names the def surface paints (langdef/doc/DefDocMarks).
  private val marksTheme: js.Dynamic =
    viewMod.EditorView.theme(js.Dynamic.literal(
      ".cm-meta" -> js.Dynamic.literal(
        "color" -> "#7b1fa2", "font-weight" -> "600"),
      ".cm-string" -> js.Dynamic.literal("color" -> "#2e7d32"),
      ".cm-number" -> js.Dynamic.literal("color" -> "#1565c0"),
      ".cm-ref" -> js.Dynamic.literal(
        "color" -> "#9a6700", "font-style" -> "italic"),
      ".cm-keyword" -> js.Dynamic.literal(
        "color" -> "#283593", "font-weight" -> "bold"),
      ".cm-placeholder" -> js.Dynamic.literal(
        "color" -> "#6d28d9", "font-style" -> "italic"),
      ".cm-gap" -> js.Dynamic.literal(
        "background-color" -> "#ffe9e9",
        "border-bottom" -> "2px dotted #c62828"),
      ".cm-unmatched" -> js.Dynamic.literal(
        "color" -> "#9a6700", "background-color" -> "#fff8ec"),
      // The markdown surface's kinds (examples/markdown/MarkdownMarks).
      ".cm-h1" -> js.Dynamic.literal(
        "font-weight" -> "700", "font-size" -> "1.25em"),
      ".cm-h2" -> js.Dynamic.literal("font-weight" -> "700"),
      ".cm-list" -> js.Dynamic.literal("color" -> "#444"),
      ".cm-marker" -> js.Dynamic.literal("color" -> "#93a1a1"),
      ".cm-bold" -> js.Dynamic.literal("font-weight" -> "700"),
      ".cm-italic" -> js.Dynamic.literal("font-style" -> "italic")))
}

//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

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

// Thin wrapper over CodeMirror 6: a mounted EditorView that calls back on
// every document-changing transaction.
object CodeMirror {

  private def call0(f: js.Dynamic): js.Dynamic =
    f.asInstanceOf[js.Function0[js.Dynamic]]()

  private val viewMod = CMView.asInstanceOf[js.Dynamic]
  private val cmdMod = CMCommands.asInstanceOf[js.Dynamic]
  private val langMod = CMLanguage.asInstanceOf[js.Dynamic]

  // Mount an editor under `parent` showing `doc`; `onChange` fires with
  // the full text on each doc-changing transaction (i.e. per keystroke).
  // The returned handle can destroy the editor.
  def mount(parent: dom.Element, doc: String, onChange: String => Unit): Handle = {
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
        listener),
      "parent" -> parent))
    new Handle(view)
  }

  final class Handle(private val view: js.Dynamic) {
    def text: String = view.state.doc.toString.asInstanceOf[String]
    def destroy(): Unit = view.destroy()
  }
}

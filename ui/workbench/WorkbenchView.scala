package opelan.ui.workbench

import opelan.foundation.document.Store
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import WorkbenchInput._

// The workbench's chrome as a pure view: a documents sidebar listing the
// store's heads, a view-switcher toolbar with save and sync, two managed
// pane slots (the document pane and the components demo — both stay
// mounted, hidden not unmounted, so their state survives switching), and
// the status bar. The pane contents live in their own runtimes, mounted
// by the shell into the Managed slots.
def workbenchView(m: WorkbenchModel): View[WorkbenchInput] =
  el("div",
    style("display: flex; height: 100vh; font-family: Arial, sans-serif;"))(
    sidebar(m),
    el("div", style("flex: 1; display: flex; flex-direction: column;"))(
      toolbar(m),
      content(m),
      statusBar(m)))

private def sidebar(m: WorkbenchModel): View[WorkbenchInput] =
  el("div",
    style("width: 250px; border-right: 1px solid #ccc; padding: 10px; " +
      "background: #f5f5f5; overflow: auto;"))(
    el("h3")(text("Documents")),
    docList(m),
    el("button",
      style("width: 100%; padding: 5px; margin: 5px 0;"),
      events = on("click", NewDocument))(
      text("New Document")))

private def docList(m: WorkbenchModel): View[WorkbenchInput] =
  if (m.workspace.entries.isEmpty)
    el("div", style("padding: 5px; color: #666;"))(text("No documents"))
  else
    els("div")(m.workspace.entries.map { case (url, v, label) =>
      el("div",
        Map(
          "style" -> ("padding: 5px; cursor: pointer; border-bottom: 1px solid #eee;" +
            (if (m.workspace.openUrl.contains(url)) " font-weight: bold;" else "")),
          "title" -> url),
        events = on("click", SelectDocument(url)))(
        text(s"$label  v$v"))
    })

private def toolbar(m: WorkbenchModel): View[WorkbenchInput] =
  el("div",
    style("padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;"))(
    viewButton("Document", ShellView.Document, m),
    viewButton("Components", ShellView.Components, m),
    el("button",
      style("margin-right: 5px; padding: 5px 10px;"),
      events = on("click", SaveRequested))(text("Save")),
    el("button",
      style("padding: 5px 10px;" +
        (if (m.syncOn) " font-weight: bold; background: #dde9f5;" else "")),
      events = on("click", ToggleSync))(text("Sync")),
    el("span", style("margin-left: 20px;"))(text(currentDocLabel(m))))

private def viewButton(
    label: String, v: ShellView, m: WorkbenchModel): View[WorkbenchInput] =
  el("button",
    style("margin-right: 5px; padding: 5px 10px;" +
      (if (m.view == v) " font-weight: bold; background: #dde9f5;" else "")),
    events = on("click", SwitchShellView(v)))(
    text(label))

private def content(m: WorkbenchModel): View[WorkbenchInput] =
  el("div", style("flex: 1; padding: 10px; overflow: auto;"))(
    managed("doc-pane",
      style("height: 100%;" + hidden(m.view != ShellView.Document)))(
      (el, emit) => WorkbenchShell.docPaneMount(el, emit)),
    managed("components-pane",
      style("height: 100%; border: 1px solid #ccc; padding: 10px;" +
        hidden(m.view != ShellView.Components)))(
      (el, emit) => WorkbenchShell.componentsPaneMount(el, emit)))

private def statusBar(m: WorkbenchModel): View[WorkbenchInput] =
  el("div",
    style("padding: 5px; border-top: 1px solid #ccc; background: #f9f9f9; " +
      "font-size: 12px;"))(
    text(m.status))

private def currentDocLabel(m: WorkbenchModel): String =
  (m.workspace.openUrl, m.workspace.openVersion, m.workspace.doc) match {
    case (Some(url), Some(v), Some(d)) =>
      s"${Store.labelOf(d).getOrElse(url)} — $url@v$v"
    case _ => "unsaved"
  }

private def hidden(condition: Boolean): String =
  if (condition) " display: none;" else ""

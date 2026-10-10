package opelan.ui.workbench

import org.scalajs.dom
import opelan.data.storage.IndexedDBStore
import opelan.ui.fp.{Handle, Runtime}

// The browser edge — the only imperative remnant of the old shell. It
// mounts the workbench and sends Boot; every effect the app performs
// lives inside the seam components declared in Workbench.children
// (ui.fx: storage, prompt, random, sync). There is no interpreter, no
// handle map, no session registry — composition is the whole story.
object WorkbenchShell {

  private var handle: Handle[WorkbenchInput, Nothing] = null

  def initialize(containerId: String): Unit = {
    val container = dom.document.getElementById(containerId)
    if (container == null)
      throw new IllegalArgumentException(
        s"Container element with id '$containerId' not found")
    handle = Runtime.mount(container, Workbench, WorkbenchInput.Boot)
  }

  def cleanup(): Unit = {
    Option(handle).foreach(_.dispose())
    handle = null
    IndexedDBStore.close()
  }
}

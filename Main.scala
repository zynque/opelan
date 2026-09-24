//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import org.scalajs.dom
import opelan.ui.editor.Workbench

// Main entry point for the Opelan Language Workbench
object Main {
  def main(args: Array[String]): Unit = {
    // Initialize the workbench
    Workbench.initialize("app")
  }
}

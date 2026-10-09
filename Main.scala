package opelan

import opelan.ui.editor.Workbench

// Main entry point for the Opelan Language Workbench
object Main {
  def main(args: Array[String]): Unit = {
    // Initialize the workbench
    Workbench.initialize("app")
  }
}

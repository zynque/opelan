package opelan

import opelan.ui.workbench.WorkbenchShell

// Main entry point for the Opelan Language Workbench
object Main {
  def main(args: Array[String]): Unit =
    WorkbenchShell.initialize("app")
}

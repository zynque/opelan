package opelan.ui.editor

// Static markup for the workbench shell: a projects sidebar, a view-switcher
// toolbar, and the content area hosting the document and components views.
object WorkbenchLayout {
  val markup: String = """
    <div id="workbench-layout" style="display: flex; height: 100vh; font-family: Arial, sans-serif;">
      <!-- Sidebar -->
      <div id="sidebar" style="width: 250px; border-right: 1px solid #ccc; padding: 10px; background: #f5f5f5;">
        <h3>Projects</h3>
        <div id="project-list"></div>
        <button id="new-project-btn" style="width: 100%; padding: 5px; margin: 5px 0;">New Project</button>
      </div>

      <!-- Main Content -->
      <div id="main-content" style="flex: 1; display: flex; flex-direction: column;">
        <!-- Toolbar -->
        <div id="toolbar" style="padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
          <button id="doc-view-btn" style="margin-right: 5px; padding: 5px 10px;">Document</button>
          <button id="components-view-btn" style="margin-right: 5px; padding: 5px 10px;">Components</button>
          <span style="margin-left: 20px;">Project: <span id="current-project-name">None</span></span>
        </div>

        <!-- Content Area -->
        <div id="content-area" style="flex: 1; padding: 10px; overflow: auto;">
          <div id="doc-view" style="height: 100%;">
            <div id="doc-container" style="height: 100%;"></div>
          </div>
          <div id="components-view" style="height: 100%; display: none;">
            <div id="components-container" style="height: 100%; border: 1px solid #ccc; padding: 10px;"></div>
          </div>
        </div>

        <!-- Status Bar -->
        <div id="status-bar" style="padding: 5px; border-top: 1px solid #ccc; background: #f9f9f9; font-size: 12px;">
          <span id="status-message">Ready</span>
        </div>
      </div>
    </div>
  """
}

package opelan.ui.editor

// Static markup for the workbench shell: a documents sidebar listing the
// document store's heads, a view-switcher toolbar with save, and the
// content area hosting the document and components views.
val workbenchMarkup: String = """
  <div id="workbench-layout" style="display: flex; height: 100vh; font-family: Arial, sans-serif;">
    <!-- Sidebar -->
    <div id="sidebar" style="width: 250px; border-right: 1px solid #ccc; padding: 10px; background: #f5f5f5;">
      <h3>Documents</h3>
      <div id="document-list"></div>
      <button id="new-doc-btn" style="width: 100%; padding: 5px; margin: 5px 0;">New Document</button>
    </div>

    <!-- Main Content -->
    <div id="main-content" style="flex: 1; display: flex; flex-direction: column;">
      <!-- Toolbar -->
      <div id="toolbar" style="padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
        <button id="doc-view-btn" style="margin-right: 5px; padding: 5px 10px;">Document</button>
        <button id="components-view-btn" style="margin-right: 5px; padding: 5px 10px;">Components</button>
        <button id="save-doc-btn" style="margin-right: 5px; padding: 5px 10px;">Save</button>
        <button id="sync-btn" style="padding: 5px 10px;">Sync</button>
        <span style="margin-left: 20px;"><span id="current-doc-name">unsaved</span></span>
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

@echo off
setlocal

rem Clean previous build artifacts
if exist run.js del run.js
if exist run.mjs del run.mjs
if exist bundle.js del bundle.js

rem Install npm dependencies
call npm install

rem Build Scala.js to ES module with all source files
call scala-cli package Main.scala core\project\Project.scala core\project\Workspace.scala core\schema\Schema.scala core\typing\TypedHoles.scala core\dsl\Parser.scala data\automerge\CollaborationManager.scala data\storage\IndexedDBStore.scala ui\visualization\DiagramRenderer.scala ui\editor\Workbench.scala --js --js-module-kind es -o run.js

if %errorlevel% neq 0 (
    echo Scala.js build failed!
    exit /b 1
)

echo Scala.js build completed. Now running webpack...

rem Bundle with webpack
echo Running: npx webpack
cmd /c "npx webpack"
echo Webpack command completed with errorlevel: %errorlevel%

if %errorlevel% neq 0 (
    echo Webpack bundling failed!
    exit /b 1
)

echo Build successful! Open http://localhost:8080 in your browser
echo Make sure to run: npx http-server -p 8080

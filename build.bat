@echo off
setlocal

rem Clean previous build artifacts
if exist app.js del app.js
if exist bundle.js del bundle.js

rem Install npm dependencies
call npm install

rem Build Scala.js to ES module with all source files
call scala-cli package Main.scala foundation ui data collaboration --js --js-module-kind es -o app.js

if %errorlevel% neq 0 (
    echo Scala.js build failed!
    exit /b 1
)

rem Bundle with webpack
cmd /c "npx webpack"

if %errorlevel% neq 0 (
    echo Webpack bundling failed!
    exit /b 1
)

echo Build successful! Open http://localhost:8080 in your browser
echo Make sure to run: npx http-server -p 8080

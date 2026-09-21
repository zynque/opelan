@echo off
echo Building Opelan Language Workbench with scala-cli...
echo Installing npm dependencies...
call npm install
if %ERRORLEVEL% NEQ 0 (
    echo npm install failed!
    exit /b %ERRORLEVEL%
)
echo Cleaning previous build...
if exist run.js del run.js
if exist run.mjs del run.mjs
if exist bundle.js del bundle.js
echo Building Scala.js application with ES module support...
call scala-cli package Main.scala --js --js-module-kind es
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)
echo Renaming output to .mjs extension...
if exist run.js ren run.js run.mjs
echo Bundling with webpack...
call npm run bundle
if %ERRORLEVEL% NEQ 0 (
    echo Bundling failed!
    exit /b %ERRORLEVEL%
)
echo Build successful!
echo.
echo To run the application, start a local server:
echo   npx http-server -p 8080
echo.
echo Then navigate to http://localhost:8080/index.html

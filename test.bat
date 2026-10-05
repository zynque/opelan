@echo off
setlocal

rem The Scala.js test runner writes the linked .mjs into %TEMP%; point TEMP
rem inside the project so Node can resolve npm deps (e.g. @automerge).
if not exist .tmp mkdir .tmp
set TEMP=%CD%\.tmp
set TMP=%CD%\.tmp

rem Run tests
call scala-cli test Main.scala project.scala foundation ui data collaboration test

if %errorlevel% neq 0 (
    echo Tests failed!
    exit /b 1
)

echo All tests passed!

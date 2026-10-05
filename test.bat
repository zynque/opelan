@echo off
setlocal

rem The Scala.js test runner writes the linked test module (main.js) into
rem %TEMP%; point TEMP inside the project so Node can resolve npm deps
rem (e.g. @automerge).
if exist .tmp rmdir /s /q .tmp
mkdir .tmp
set TEMP=%CD%\.tmp
set TMP=%CD%\.tmp

rem Run tests
call scala-cli test Main.scala project.scala foundation ui data collaboration test

if %errorlevel% neq 0 (
    echo Tests failed!
    exit /b 1
)

echo All tests passed!

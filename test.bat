@echo off
setlocal

rem Run tests
call scala-cli test Main.scala project.scala foundation ui data collaboration test

if %errorlevel% neq 0 (
    echo Tests failed!
    exit /b 1
)

echo All tests passed!

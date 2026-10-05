#!/bin/bash

# The Scala.js test runner writes the linked test module (main.js) into
# $TMPDIR; point it inside the project so Node can resolve npm deps
# (e.g. @automerge).
rm -rf .tmp
mkdir -p .tmp
export TEMP="$PWD/.tmp"
export TMP="$PWD/.tmp"
export TMPDIR="$PWD/.tmp"

# Run tests
scala-cli test Main.scala project.scala foundation ui data collaboration test

if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi

echo "All tests passed!"

#!/bin/bash

# Run tests
scala-cli test Main.scala project.scala foundation ui data collaboration test

if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi

echo "All tests passed!"

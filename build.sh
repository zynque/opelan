#!/bin/bash

# Clean previous build artifacts
rm -f app.js bundle.js

# Install npm dependencies
npm install

# Build Scala.js to ES module with all source files
scala-cli package Main.scala foundation ui data collaboration --js --js-module-kind es -o app.js

if [ $? -ne 0 ]; then
    echo "Scala.js build failed!"
    exit 1
fi

# Bundle with webpack
npx webpack

if [ $? -ne 0 ]; then
    echo "Webpack bundling failed!"
    exit 1
fi

echo "Build successful! Open http://localhost:8080 in your browser"
echo "Make sure to run: npx http-server -p 8080"

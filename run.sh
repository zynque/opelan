#!/bin/bash
echo "Building Opelan Language Workbench with scala-cli..."
echo "Installing npm dependencies..."
npm install
if [ $? -ne 0 ]; then
    echo "npm install failed!"
    exit 1
fi
echo "Cleaning previous build..."
rm -f run.js run.mjs bundle.js
echo "Building Scala.js application with ES module support..."
scala-cli package Main.scala --js --js-module-kind es
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi
echo "Renaming output to .mjs extension..."
mv run.js run.mjs
echo "Bundling with webpack..."
npm run bundle
if [ $? -ne 0 ]; then
    echo "Bundling failed!"
    exit 1
fi
echo "Build successful!"
echo ""
echo "To run the application, start a local server:"
echo "  npx http-server -p 8080"
echo ""
echo "Then navigate to http://localhost:8080/index.html"

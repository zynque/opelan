#!/bin/bash

# Clean previous build artifacts
rm -f run.js run.mjs bundle.js

# Install npm dependencies
npm install

# Build Scala.js to ES module with all source files
scala-cli package Main.scala foundation/project/Project.scala foundation/project/Workspace.scala foundation/structure/Schema.scala foundation/typing/TypedGaps.scala foundation/dsl/Parser.scala data/automerge/CollaborationManager.scala data/storage/IndexedDBStore.scala ui/visualization/DiagramRenderer.scala ui/editor/Workbench.scala --js --js-module-kind es -o run.js

if [ $? -ne 0 ]; then
    echo "Scala.js build failed!"
    exit 1
fi

# Rename to .mjs for ES module
mv run.js run.mjs

# Bundle with webpack
npm run bundle

if [ $? -ne 0 ]; then
    echo "Webpack bundling failed!"
    exit 1
fi

echo "Build successful! Open http://localhost:8080 in your browser"
echo "Make sure to run: npx http-server -p 8080"

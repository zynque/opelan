# Opelan Language Workbench

A production-ready language workbench built with Scala.js, CodeMirror, and Automerge for local-first, collaborative language development.

## Features

- **Schema-First Design**: Structured data model with projects, definitions, and schemas
- **Multi-Modal Editing**: Text editor + visualization views for the same underlying data
- **Typed Gaps**: Progressive typing system inspired by Hazel
- **Real-Time Collaboration**: Automerge CRDT for collaborative editing
- **Local-First**: Browser-only deployment with IndexedDB persistence
- **P2P Collaboration**: Pure peer-to-peer with pluggable signaling/backends
- **Custom DSL**: Expression-oriented language with room for broader language forms

## Architecture

```
foundation/           # Foundation language system
├── structure/        # Type system, validation, structures
├── dsl/              # DSL parser, expressions, interpreter
├── typing/           # Typed gaps system
└── project/          # Project management, workspace

ui/                   # User interface components
├── editor/           # Text editing (CodeMirror integration)
├── visualization/    # Graph/diagram rendering
├── structure/        # Structure editing UI
└── collaboration/    # Collaboration UI

data/                 # Data persistence
├── automerge/        # Automerge CRDT integration
└── storage/          # IndexedDB persistence

collaboration/        # Collaboration infrastructure
├── signaling/        # P2P signaling protocols
├── backends/         # Pluggable backend adapters
└── presence/         # User presence and cursors
```

## Build Requirements

- Node.js 16+ and npm
- Scala CLI
- Modern browser with IndexedDB support

## Building and Running

### Using provided scripts (recommended)

```bash
# Windows
run.bat

# Unix/Mac
./run.sh
```

### Manual build

```bash
# Install dependencies
npm install

# Build Scala.js to ES module
scala-cli package Main.scala foundation/project/Project.scala foundation/project/Workspace.scala foundation/structure/Schema.scala foundation/typing/TypedHoles.scala foundation/dsl/Parser.scala data/automerge/CollaborationManager.scala data/storage/IndexedDBStore.scala ui/visualization/DiagramRenderer.scala ui/editor/Workbench.scala --js --js-module-kind es -o run.js

# Rename for ES module
mv run.js run.mjs

# Bundle with webpack
npm run bundle
```

### Running the application

```bash
# Start a local server (Node.js recommended)
npx http-server -p 8080

# Or use Python as alternative
python -m http.server 8080
```

Then open http://localhost:8080 in your browser.

## Development Status

This project is currently in Phase 1 (Foundation & Schema System) of the implementation plan. The current build includes:

- ✅ Working Scala.js + scala-cli + webpack + CodeMirror integration
- ✅ Modular project structure
- ✅ Core data models (Project, Definition, Schema)
- ✅ Typed holes system foundation
- ✅ DSL parser foundation
- ✅ Automerge collaboration layer
- ✅ IndexedDB persistence layer
- ✅ Basic visualization components
- ✅ Unified workbench interface

## Next Steps

The following features are planned for implementation:

1. **Enhanced Editor Integration**: Full CodeMirror integration with typed holes
2. **Schema Editor**: Visual schema definition interface
3. **Advanced Visualization**: Interactive diagrams with editing capabilities
4. **Real-Time Collaboration**: P2P synchronization and presence indicators
5. **DSL Interpreter**: Evaluation engine for the custom DSL
6. **Testing Framework**: Comprehensive test suite

## Contributing

This project uses a schema-first approach with structured data. When adding new features:

1. Define schemas for new data types
2. Implement core logic in the appropriate module
3. Add UI components in the UI layer
4. Update persistence and collaboration layers as needed

## License

MIT License

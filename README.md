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
├── document/         # Persistent document tree model (ported from olw-p4)
├── version/          # Version DAG / merge-base machinery (ported from olw-p4)
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
build.bat

# Unix/Mac
./build.sh
```

### Manual build

```bash
# Install dependencies
npm install

# Build Scala.js to ES module
scala-cli package Main.scala foundation ui data collaboration --js --js-module-kind es -o app.js

# Bundle with webpack
npm run bundle
```

### Running tests

```bash
# Windows
test.bat

# Unix/Mac
./test.sh

# Or manually
scala-cli test Main.scala project.scala foundation ui data collaboration test
```

### Running the application

```bash
# Start a local server (Node.js recommended)
npx http-server -p 8080

# Or use Python as alternative
python -m http.server 8080
```

Then open http://localhost:8080 in your browser.

## Status

See [STATUS.md](STATUS.md) for current development status and next steps.

## Contributing

This project uses a schema-first approach with structured data. When adding new features:

1. Define schemas for new data types
2. Implement core logic in the appropriate module
3. Add UI components in the UI layer
4. Update persistence and collaboration layers as needed

## License

MIT License

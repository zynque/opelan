# Opelan Language Workbench

I dream of a world where anyone can "look under the hood" of any program at the click of a button — then edit, tinker, and share their modifications, all without leaving the browser. A world where code takes the shape best suited to its domain — visual, textual, or a mix of both. A world where code is worth reading not just by machines or professional developers, but by anyone and everyone seeking to understand and improve the digital systems that shape our world. That is the next generation of open source development: a platform by all and for all.

This project is a step toward that vision — an experimental prototype exploring language workbench concepts (structured editing, polysyntactic views, typed holes, and local-first documents) built with Scala.js and CodeMirror. It draws inspiration from prior language workbench and projectional editing systems — MPS, OMeta, and many others — as well as ideas I've gathered over more than 30 years of tinkering and pondering.

**This is a research prototype, not production software.** It exists to explore ideas from the [MANIFESTO](MANIFESTO.md); expect rough edges, incomplete features, and evolving design. See [STATUS.md](STATUS.md) for an honest accounting of what works and what doesn't.

**A note on AI use**: LLM agents are used generously in this project to accelerate development of the prototype. The design is heavily inspired by my earlier hand-written prototypes ([olw-p1](https://github.com/zynque/olw-p1) … [olw-p4](https://github.com/zynque/olw-p4)), and the agents are carefully steered to reflect my technical style and preferences. The eventual goal, though, is a fully bootstrapped implementation in which every bit of code has been carefully tended and scrutinized by human hands.

## Features

Working today:

- **Document Model**: Everything is a versioned, structured tree — programs, documents, and tooling state
- **Polysyntactic Editing**: Structural editor, text surface, and language-provided derived views over one document
- **Typed Holes**: Hazel-inspired holes keep incomplete input editable instead of failing
- **Local-First**: Browser-only deployment with IndexedDB persistence
- **Versioned History**: Version DAG as a document — branching undo and a click-to-jump history tree
- **Live Sync**: Automerge-backed editing shared live between browser tabs (BroadcastChannel — no server)

Planned / exploratory:

- Real P2P sync across machines (signaling transport) and presence/cursors
- Language definitions as documents (bootstrap)

## Architecture

```
foundation/           # Pure model layer
├── document/         # Persistent document tree model (ported from olw-p4)
├── version/          # Version DAG / merge-base machinery (ported from olw-p4)
└── language/         # Languages, parsers, and derived views over documents

ui/                   # User interface components
├── fp/               # Elm-style component runtime (+ demo components)
├── editor/           # Structural document editor + workbench shell
├── typeddoc/         # Typed-document pane: structure + text + derived views
└── text/             # CodeMirror integration

data/                 # Data persistence
└── storage/          # IndexedDB persistence

collaboration/        # Collaboration infrastructure (planned)
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

Then open http://localhost:8080 in your browser. See [USAGE.md](USAGE.md)
for keyboard shortcuts and a tour of the views.

## Status

See [STATUS.md](STATUS.md) for current development status and next steps.

## Contributing

This project is built on the document model — new features should represent
state as `Document[NodeData]` rather than parallel model types. When adding
new features:

1. Keep pure logic in `foundation/` and DOM/effect code thin in `ui/`
2. Implement UI as `ui/fp` components (see `ui/typeddoc` for the pattern)
3. Add tests for pure logic under `test/`

## License

MIT License

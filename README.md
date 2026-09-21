# Opelan Language Workbench

A minimal proof of concept Scala.js application that demonstrates local-first data storage using Automerge and code editing using CodeMirror. This is intended as a foundation for a language workbench.

## Features

- **Scala.js**: Full-stack Scala development compiled to JavaScript
- **Automerge**: Local-first data storage with CRDT support for collaborative editing
- **CodeMirror 6**: Modern code editor with syntax highlighting and autocompletion
- **scala-cli**: Modern build tool for simplified Scala project management

## Prerequisites

- Java JDK 11 or higher
- scala-cli (install from https://scala-cli.virtuslab.org)
- Node.js and npm (for JavaScript dependencies)

## Building the Project

This project uses scala-cli instead of sbt for a simpler build process:

1. **Install scala-cli** (if not already installed):
   ```bash
   curl -sSLf https://virtuslab.github.io/scala-cli-packages/scala-cli-setup.sh | sh
   ```

2. **Install npm dependencies:**
   ```bash
   npm install
   ```

3. **Build the project:**
   ```bash
   scala-cli compile Main.scala
   ```

4. **Package for web:**
   ```bash
   scala-cli package Main.scala --js --js-module-kind commonjs
   npm run bundle
   ```

## Running the Project

1. **Build the project:**
   ```bash
   scala-cli package Main.scala --js --js-module-kind es
   ```

2. **Serve the HTML file:**
   You can use any static file server. For example, using Node.js:
   ```bash
   npx http-server -p 8080
   ```

   Or using Python:
   ```bash
   python -m http.server 8080
   ```

3. **Open in browser:**
   Navigate to `http://localhost:8080` and open `index.html`

## Development Workflow

During development, you can use watch mode for automatic recompilation:

```bash
scala-cli compile Main.scala --watch
```

This will recompile automatically when you make changes to the Scala source files.

## Project Structure

```
opelan/
├── Main.scala                # Main application with dependency directives
├── index.html                # HTML entry point
├── package.json              # npm dependencies (optional, for reference)
├── run.sh                    # Unix build script
├── run.bat                   # Windows build script
└── README.md
```

## Dependency Management

The project uses a hybrid approach:
- **Scala dependencies** are declared in `Main.scala` using `//> using` directives
- **npm dependencies** are declared in `package.json` and installed via npm

```scala
//> using scala 3.3.1
//> using platform js
//> using dep org.scala-js::scalajs-dom:2.8.0
```

```json
{
  "dependencies": {
    "@automerge/automerge": "^1.0.2",
    "@codemirror/state": "^6.4.0",
    ...
  }
}
```

## How It Works

1. **Automerge Integration**: The application initializes an Automerge document to store the code content and edit history. Changes in the editor are automatically synchronized to the Automerge document.

2. **CodeMirror Editor**: A CodeMirror 6 instance is created with basic setup, keybindings, and autocompletion. The editor content is bound to the Automerge document.

3. **Real-time Sync**: When you type in the editor, changes are captured and stored in the Automerge document with timestamps in the history array.

4. **Persistence**: The "Save to Console" button serializes the Automerge document to JSON and logs it to the console, demonstrating how you could persist the document.

## Key Components

- `Main.scala`: Contains the main application logic, including:
  - Dependency declarations via scala-cli directives
  - Automerge document initialization and management
  - CodeMirror editor setup with extensions
  - Real-time synchronization between editor and Automerge
  - UI updates for displaying document state

## Alternative: Using sbt

If you prefer to use sbt instead of scala-cli, the project includes sbt configuration files:

- `build.sbt`: SBT build configuration
- `project/build.properties`: SBT version
- `project/plugins.sbt`: SBT plugins (Scala.js, bundler)
- `src/main/scala/opelan/Main.scala`: SBT version of the main file
- `src/main/resources/index.html`: SBT version of HTML

To use sbt:
```bash
sbt fastOptJS
```

## Future Enhancements

This proof of concept can be extended to:

- **Collaborative Editing**: Use Automerge's networking capabilities for real-time collaboration
- **Language Server Protocol**: Integrate LSP for advanced language features
- **Persistence Layer**: Add local storage or backend synchronization
- **Multi-file Support**: Extend to handle multiple files and project structure
- **Custom Language Support**: Add syntax highlighting and parsing for custom languages

## Troubleshooting

If you encounter issues with npm dependencies, try:
```bash
rm -rf node_modules package-lock.json
npm install
scala-cli compile Main.scala
```

If you encounter issues with scala-cli compilation, try:
```bash
scala-cli clean Main.scala
rm -rf .scala-build
scala-cli compile Main.scala
```

If you encounter issues with dependency resolution, try clearing the scala-cli cache:
```bash
# Windows
rmdir /s /q "%LOCALAPPDATA%\ScalaCli\cache"

# Unix/Linux
rm -rf ~/.cache/scala-cli
```

## License

See LICENSE file for details.

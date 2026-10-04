# The Open Language Application Manifesto

## Overview

This manifesto outlines the core principles for building the next generation of creative and productivity software. It emphasizes openness, language orientation, and application capabilities that empower users and developers alike.

## Open

### Open Source

We are best served by software tools that openly share their source code, so that anyone can contribute and all can benefit.

### Frictionless

Making source code available is not enough. Software should be trivial to modify, from within itself — a full development environment built in, with no external tools to download or configure.

### Transitive

Applications built on this platform should inherit its capabilities by default, upholding every principle described here — opting out only where it makes sense to do so.

## Language Oriented

### Language

Every piece of software has a language in which its user communicates intent, whether textual, visual, or physical. Key presses, mouse clicks, touches, and gestures, arranged in patterns, are all languages in a certain sense. It is up to software to interpret that intent, perform calculations, and produce an appropriate outcome.

### Programmable

Software should be designed so that users can extend and modify its behavior through scripting or configuration.

### Discoverable

The software should surface what is possible — auto-complete, menus, contextual suggestions — rather than requiring memorized commands.

### Immediately Typed

A type system determines which expressions are valid and sensible. Dynamic typing checks too late — at runtime, when an error may already be in the user's hands. Static typing still checks too late — at compile time, forcing developers to wait to discover mistakes. An ideal type system validates as the code is typed. This is an immediate type system.

### Transitively Typed

Applications built on an open language platform should naturally inherit its type system, or a domain-specific subset or variation.

### Polysyntactic

Syntax takes far too much precedence in software development; surface representation is too often treated as substance. Software should offer multiple representations of the same content, configurable to each user's preference, and translate seamlessly between them — so sharing work never requires adapting to someone else's format.

## Application

### Available and Responsive

Be web first, available on any device with a browser, and responsive to device size and orientation.

### Online/offline

Fully functional while offline; synchronizes automatically when online.

### Preserve History

Every application should have an undo button. Developers take source control for granted; so should every user of a creativity or productivity application. History should be integrated and semantically aware — a diff tool should understand the *meaning* of a difference, not just its text.

### Reactive

The results of a change should be made immediately visible via live examples (similar to unit tests). Changes to libraries an application depends on should surface unobtrusive notifications describing their severity and nature; opting in should be a single click.

### Never Broken

Every intermediate state of a user's work — unfinished, inconsistent, or outright malformed — is valid and useful information. Software should interpret whatever exists as far as it can, treating the gaps as first-class content rather than as failures. A hole is a place the user has not finished yet; it should be visible and able to heal as work continues — never an error that blocks rendering, discards input, or prevents the rest of the work from functioning.

### Real Time Collaboration

Allow users to work on shared content together, editing it interactively in real time.

### Learnable & Self Documenting

Documentation should live alongside the content it describes, with examples demonstrating the usage of each entity.

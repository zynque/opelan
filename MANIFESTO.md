# The Open Language Application Manifesto

## Open

### Open Source

We are best served by software tools that openly share their source code, so that anyone can contribute and all can benefit.

### Frictionless

Every piece of creativity/productivity software should be trivial to modify. It should come with a full integrated development environment built in, so that the software can be modified from directly within the application itself, without the need to download and configure external tools.

### Transitive

By default, applications built using this platform should be endowed with all the capabilities of the platform itself - thus upholding all the principles described here. Applications may chose to opt out of some or all of these features where it makes sense to do so.

## Language Oriented

### Language

Every piece of software has a language in which its user communicates intent, whether that language be textual, visual, or physical. Key presses, mouse clicks, touches, guestures, arranged in complex patterns convey a certain desire. All of these, whether simple or complex, are languages in a certain sense. It is up to software to interpret that desire, perform calculations, and produce an appropriate outcome.

### Programmable

*TO BE FILLED IN*

### Discoverable

Provide choices for the user (auto-complete, drop-down menus).

### Immediately Typed

A type system determines what sort of expressions are valid and sensical in a programming language. A dynamically typed language performs these validations while the end user program is running. When a type error occurrs, it is too late. The program might be already in the user's hands, in which case the damage has already been done. A statically typed language performs these validations when the program is compiled. Again this is too late. The software developer should not need to wait for he compilation process to complete to discover mistakes. An ideal type system performs validations as the code is typed. This is an immediate type system.

### Transitively Typed

Applications built upon an open language platform should naturally inherit the type system or a domain specific subset or variation. 

### Polysyntactic

Syntax takes far too much precidence in the software development community. Useless arguments over tabs and spaces waste time. A modern software development tool would ideally allow multiple representations configurable by each individual user to their own preference. The software should translate seamlessly between these various representations so that code can be shared without putting the burden on users to adapt to different coding formats. 

### Monosemantic

Application definitions should have a single canonical meaning/interpretation. It's execution though, might run on multiple platforms and/or modes. In particular, applications should be able to run immediately (interpreted) while also being able to be compiled into a more optimized form.

## Application

### Available and Responsive

Be web first, available on any device with a browser, and responsive to device size and orientation.

### Online/offline

Fully functional while offline, and synchronizes automatically when online.

### Preserve History

Every application should have an undo button. It is common sense for developers to use a source control system. So it should be for every productivity/creativity application. It should be integrated, so that history can be represented not just as side by side plain text, but as various representations as suits the application. A diff tool should be aware of the *meaning* of the difference.

### Reactive

The results of a change to source code should be made immediately visible via sample usages (similar to unit tests). Changes to libraries that your application depends on should pop up non-obtrusive notifications, indicating the severity and nature of the change. Opting into such a change should be as simple as a click of a button.

### Real Time Collaboration

Allow users to edit the code interactively in real time.

### Self Documenting

Application documentation should be embedded along side the code. Examples should demonstrate usage of each code entity.
package opelan.foundation.grammar

// A generic token: `kind` names its class ("int", "op", "lpar", …) and
// `text` is the matched source slice. Lexers for individual languages
// translate their concrete tokens into GToks, so grammars compose over
// them without knowing the lexer.
case class GTok(kind: String, text: String, from: Int, to: Int)

// A node's source span in the input text.
case class Span(from: Int, to: Int)

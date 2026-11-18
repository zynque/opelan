package opelan.foundation.grammar

// A grammar as a value: the parser and the lexicon it reads through,
// paired at construction so the two can't drift apart. Sessions and
// syntaxes carry a Grammar rather than (parser, lexicon) fields — a
// rule change that swaps parsers can't keep running under the old
// tokenization. Run state stays out: `input` builds the text + memo
// environment per parse.
case class Grammar(parser: Parser, lexicon: Lexicon) {

  def input(text: String, memo: MemoTable = MemoTable()): ParseInput =
    ParseInput(text, lexicon, memo)

  def apply(text: String, memo: MemoTable = MemoTable()): ParseResult =
    parser(input(text, memo), 0)
}

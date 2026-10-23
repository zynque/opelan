package opelan.foundation.means

import opelan.foundation.document.NodeData
import opelan.foundation.grammar._

// The grammar of rule text, as scannerless combinators:
//
//   rule       := pattern means? meaning? where?
//   pattern    := token*                      (up to `means` or `=>`)
//   meaning    := build | use | phrase        (each up to its end)
//   build      := word "(" arguments? ")"     (followed by the end)
//   use        := placeholder                 (followed by the end)
//   phrase     := balanced+                   (up to the end)
//   where      := "where" group (";" group)*
//   group      := token* (":" token*)?
//
// At the top a meaning ends at `where` or the end of input; inside a
// constructor's arguments, at "," or ")". Optional parts are optional so
// a malformed rule still parses; RuleRead reports what's missing.

private def tag(name: String) = NodeData.StringData(name)

private def token(kind: String, text: String): Parser =
  tokWhere(kind)(_.text == text)(t => NodeData.StringData(t.kind))

private def single(text: String) = token("single", text)

private val meansKeyword = choice(token("word", "means"), token("symbol", "=>"))
private val whereKeyword = token("word", "where")
private val colon = token("symbol", ":")

private def tokenExcept(stop: Parser): Parser =
  seq(notFollowedBy(stop), anyToken)((_, t) => t)

private val topEnd = choice(whereKeyword, endOfInput)
private val argumentEnd = choice(single(","), single(")"))

private def meaning(end: Parser): Parser = choice(
  sequence(tag("build"))(
    tok("word")(t => NodeData.StringData(t.kind)), single("("),
    optional(arguments), single(")"), followedBy(end)),
  sequence(tag("use"))(
    tokWhere("word")(t => isPlaceholderWord(t.text))(t => NodeData.StringData(t.kind)),
    followedBy(end)),
  sequence(tag("phrase"))(balanced(end), many(tag("rest"))(balanced(end))))

private lazy val argument: Parser = rule("argument")(meaning(argumentEnd))

private lazy val arguments: Parser = sequence(tag("arguments"))(
  argument,
  many(tag("more"))(sequence(tag("next"))(single(","), argument)))

// A token, or a parenthesized group kept whole so its commas and
// parentheses don't end the phrase.
private def balanced(end: Parser): Parser =
  choice(group, tokenExcept(end))

private lazy val group: Parser = rule("group")(sequence(tag("group"))(
  single("("),
  many(tag("inside"))(choice(group, tokenExcept(choice(single(")"), whereKeyword)))),
  optional(single(")"))))

private val constraints: Parser = {
  val one = sequence(tag("constraint"))(
    many(tag("names"))(tokenExcept(choice(colon, single(";")))),
    optional(sequence(tag("typed"))(colon,
      many(tag("term"))(tokenExcept(single(";"))))))
  sequence(tag("where"))(whereKeyword, one,
    many(tag("more"))(sequence(tag("next"))(single(";"), one)))
}

val ruleGrammar: Parser = sequence(tag("rule"))(
  many(tag("pattern"))(tokenExcept(meansKeyword)),
  optional(meansKeyword),
  optional(meaning(topEnd)),
  optional(constraints))

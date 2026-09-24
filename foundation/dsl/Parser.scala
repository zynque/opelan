package opelan.foundation.dsl

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import opelan.foundation.structure.Schema
import opelan.foundation.typing.TypedGap

// DSL expression types
sealed trait Expression {
  def id: String
  def typeName: String
}

object Expression {
  case class Literal(id: String, value: js.Any, literalType: String) extends Expression {
    def typeName = literalType
  }
  
  case class Variable(id: String, name: String) extends Expression {
    def typeName = "variable"
  }
  
  case class FunctionCall(
    id: String, 
    functionName: String, 
    arguments: List[Expression],
    functionSchema: Option[String] = None
  ) extends Expression {
    def typeName = "function_call"
  }
  
  case class BinaryOperation(
    id: String,
    operator: String,
    left: Expression,
    right: Expression
  ) extends Expression {
    def typeName = "binary_op"
  }
  
  case class IfExpression(
    id: String,
    condition: Expression,
    thenBranch: Expression,
    elseBranch: Option[Expression]
  ) extends Expression {
    def typeName = "if_expr"
  }
  
  case class GapExpression(
    id: String,
    gapId: String, // Reference to TypedGap
    expectedType: Option[String] = None
  ) extends Expression {
    def typeName = "gap"
  }
}

// Parse result types
sealed trait ParseResult {
  def isSuccess: Boolean
}

object ParseResult {
  case class Success(expression: Expression) extends ParseResult {
    def isSuccess = true
  }
  
  case class Failure(errors: List[ParseError]) extends ParseResult {
    def isSuccess = false
  }
  
  case class Partial(expression: Expression, remaining: String, gaps: List[TypedGap]) extends ParseResult {
    def isSuccess = true
  }
}

// Parse error
case class ParseError(
  position: js.Dynamic,
  message: String,
  severity: String = "error" // error, warning, info
)

// DSL Parser for parsing expressions with schema awareness
class DSLParser {
  private var nextExprId: Int = 1
  
  // Main parsing method - handles both structured and text-based parsing
  def parse(input: String, schema: Option[Schema] = None): ParseResult = {
    try {
      val tokens = tokenize(input)
      parseTokens(tokens, schema)
    } catch {
      case e: Exception =>
        ParseResult.Failure(List(ParseError(js.Dynamic.literal("line" -> 0, "column" -> 0), s"Parse error: ${e.getMessage}")))
    }
  }
  
  // Tokenize input string
  private def tokenize(input: String): List[Token] = {
    // Simple tokenizer - can be enhanced with proper lexer
    input.split("\\s+").zipWithIndex.map { case (token, index) =>
      Token(token, index, index + token.length)
    }.toList.filter(_.text.nonEmpty)
  }
  
  // Parse tokens into expressions
  private def parseTokens(tokens: List[Token], schema: Option[Schema]): ParseResult = {
    if (tokens.isEmpty) {
      return ParseResult.Failure(List(ParseError(js.Dynamic.literal("line" -> 0, "column" -> 0), "Empty input")))
    }
    
    // For now, create a simple expression structure
    // This is a placeholder - should be replaced with proper parser
    val expr = Expression.Literal(
      id = nextExpressionId(),
      value = tokens.map(_.text).mkString(" "),
      literalType = "string"
    )
    
    ParseResult.Success(expr)
  }
  
  // Generate next expression ID
  private def nextExpressionId(): String = {
    val id = s"expr_$nextExprId"
    nextExprId += 1
    id
  }
  
  // Parse with typed gaps support
  def parseWithGaps(input: String, schema: Option[Schema] = None): ParseResult = {
    // Enhanced parsing that creates typed gaps for incomplete expressions
    val baseResult = parse(input, schema)
    
    // For now, just return the base result
    // Should be enhanced to detect incomplete expressions and create gaps
    baseResult
  }
  
  // Validate parsed expression against schema
  def validate(expression: Expression, schema: Schema): Boolean = {
    // Basic validation - can be extended with proper type checking
    true
  }
  
  // Convert expression to structured data (for Automerge storage)
  def toStructuredData(expression: Expression): js.Dynamic = {
    expression match {
      case Expression.Literal(id, value, literalType) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "literal",
          "value" -> value,
          "literalType" -> literalType
        )
      case Expression.Variable(id, name) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "variable",
          "name" -> name
        )
      case Expression.FunctionCall(id, functionName, args, functionSchema) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "function_call",
          "functionName" -> functionName,
          "arguments" -> js.Array(args.map(toStructuredData)*),
          "functionSchema" -> functionSchema.orNull
        )
      case Expression.BinaryOperation(id, op, left, right) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "binary_op",
          "operator" -> op,
          "left" -> toStructuredData(left),
          "right" -> toStructuredData(right)
        )
      case Expression.IfExpression(id, cond, thenB, elseB) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "if_expr",
          "condition" -> toStructuredData(cond),
          "thenBranch" -> toStructuredData(thenB),
          "elseBranch" -> elseB.map(toStructuredData).orNull
        )
      case Expression.GapExpression(id, gapId, expectedType) =>
        js.Dynamic.literal(
          "id" -> id,
          "type" -> "gap",
          "gapId" -> gapId,
          "expectedType" -> expectedType.orNull
        )
    }
  }
  
  // Convert structured data back to expression
  def fromStructuredData(data: js.Dynamic): Expression = {
    val exprType = data.selectDynamic("type").asInstanceOf[String]
    
    exprType match {
      case "literal" =>
        Expression.Literal(
          id = data.selectDynamic("id").asInstanceOf[String],
          value = data.selectDynamic("value"),
          literalType = data.selectDynamic("literalType").asInstanceOf[String]
        )
      case "variable" =>
        Expression.Variable(
          id = data.selectDynamic("id").asInstanceOf[String],
          name = data.selectDynamic("name").asInstanceOf[String]
        )
      case "function_call" =>
        val argsData = data.selectDynamic("arguments").asInstanceOf[js.Array[js.Dynamic]]
        val args = argsData.map(fromStructuredData).toList
        Expression.FunctionCall(
          id = data.selectDynamic("id").asInstanceOf[String],
          functionName = data.selectDynamic("functionName").asInstanceOf[String],
          arguments = args,
          functionSchema = Option(data.selectDynamic("functionSchema")).map(_.asInstanceOf[String])
        )
      case "binary_op" =>
        Expression.BinaryOperation(
          id = data.selectDynamic("id").asInstanceOf[String],
          operator = data.selectDynamic("operator").asInstanceOf[String],
          left = fromStructuredData(data.selectDynamic("left")),
          right = fromStructuredData(data.selectDynamic("right"))
        )
      case "if_expr" =>
        Expression.IfExpression(
          id = data.selectDynamic("id").asInstanceOf[String],
          condition = fromStructuredData(data.selectDynamic("condition")),
          thenBranch = fromStructuredData(data.selectDynamic("thenBranch")),
          elseBranch = Option(data.selectDynamic("elseBranch")).map(fromStructuredData)
        )
      case "gap" =>
        Expression.GapExpression(
          id = data.selectDynamic("id").asInstanceOf[String],
          gapId = data.selectDynamic("gapId").asInstanceOf[String],
          expectedType = Option(data.selectDynamic("expectedType")).map(_.asInstanceOf[String])
        )
      case _ =>
        throw new IllegalArgumentException(s"Unknown expression type: $exprType")
    }
  }
}

// Token for parsing
case class Token(text: String, start: Int, end: Int)

// Global DSL parser instance
object DSLParser {
  private val parser = new DSLParser()
  
  def parse(input: String, schema: Option[Schema] = None): ParseResult = parser.parse(input, schema)
  def parseWithGaps(input: String, schema: Option[Schema] = None): ParseResult = parser.parseWithGaps(input, schema)
  def validate(expression: Expression, schema: Schema): Boolean = parser.validate(expression, schema)
  def toStructuredData(expression: Expression): js.Dynamic = parser.toStructuredData(expression)
  def fromStructuredData(data: js.Dynamic): Expression = parser.fromStructuredData(data)
}

//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.visualization

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLCanvasElement, CanvasRenderingContext2D}
import opelan.foundation.project.{Project, Definition}
import opelan.foundation.dsl.Expression

// Visualization component types
sealed trait VisualizationType {
  def name: String
}

object VisualizationType {
  case object Graph extends VisualizationType { def name = "graph" }
  case object Tree extends VisualizationType { def name = "tree" }
  case object Flowchart extends VisualizationType { def name = "flowchart" }
  case object Table extends VisualizationType { def name = "table" }
  case object Custom extends VisualizationType { def name = "custom" }
}

// Visualization node
case class VisualNode(
  id: String,
  label: String,
  nodeType: String,
  position: Position,
  data: js.Dynamic,
  connections: List[Connection] = List.empty
)

// Connection between nodes
case class Connection(
  sourceId: String,
  targetId: String,
  label: String,
  connectionType: String = "default"
)

// Position in visualization
case class Position(x: Double, y: Double)

// Visualization renderer for different diagram types
class DiagramRenderer(container: dom.Element) {
  private var currentVisualization: Option[VisualNode] = None
  private var visualizationType: VisualizationType = VisualizationType.Graph
  
  // Set visualization type
  def setVisualizationType(vType: VisualizationType): Unit = {
    visualizationType = vType
  }
  
  // Render project structure as graph
  def renderProjectGraph(project: Project): Unit = {
    val nodes = project.definitions.toList.map { definition =>
      VisualNode(
        id = definition.id,
        label = definition.name,
        nodeType = definition.typeName,
        position = calculatePosition(definition.id),
        data = js.Dynamic.literal("definition" -> definition.asInstanceOf[js.Any]),
        connections = extractConnections(definition)
      )
    }
    
    renderGraph(nodes)
  }
  
  // Render expression as tree structure
  def renderExpressionTree(expression: Expression): Unit = {
    val node = expressionToVisualNode(expression)
    renderTree(node)
  }
  
  // Render function call hierarchy
  def renderCallHierarchy(definition: Definition): Unit = {
    // Create a call graph from the definition
    val nodes = extractCallNodes(definition)
    renderFlowchart(nodes)
  }
  
  // Calculate position for node (basic layout algorithm)
  private def calculatePosition(id: String): Position = {
    // Simple grid layout - can be enhanced with proper graph layout algorithms
    val hash = id.hashCode
    val x = (hash % 10) * 100 + 50
    val y = (hash / 10) % 10 * 80 + 50
    Position(x.toDouble, y.toDouble)
  }
  
  // Extract connections from definition
  private def extractConnections(definition: Definition): List[Connection] = {
    // Basic implementation - can be enhanced to analyze actual dependencies
    List.empty
  }
  
  // Convert expression to visual node
  private def expressionToVisualNode(expression: Expression): VisualNode = {
    expression match {
      case Expression.Literal(id, value, literalType) =>
        VisualNode(
          id = id,
          label = s"$literalType: $value",
          nodeType = "literal",
          position = calculatePosition(id),
          data = js.Dynamic.literal("expression" -> expression.asInstanceOf[js.Any])
        )
      case Expression.Variable(id, name) =>
        VisualNode(
          id = id,
          label = s"var: $name",
          nodeType = "variable",
          position = calculatePosition(id),
          data = js.Dynamic.literal("expression" -> expression.asInstanceOf[js.Any])
        )
      case Expression.FunctionCall(id, functionName, args, _) =>
        VisualNode(
          id = id,
          label = s"call: $functionName",
          nodeType = "function_call",
          position = calculatePosition(id),
          data = js.Dynamic.literal(
            "expression" -> expression.asInstanceOf[js.Any],
            "arguments" -> args.length
          ),
          connections = args.map(arg => Connection(id, arg.id, "arg"))
        )
      case Expression.BinaryOperation(id, op, left, right) =>
        VisualNode(
          id = id,
          label = s"op: $op",
          nodeType = "binary_op",
          position = calculatePosition(id),
          data = js.Dynamic.literal("expression" -> expression.asInstanceOf[js.Any]),
          connections = List(
            Connection(id, left.id, "left"),
            Connection(id, right.id, "right")
          )
        )
      case Expression.IfExpression(id, cond, thenB, elseB) =>
        val connections = List(
          Connection(id, cond.id, "condition"),
          Connection(id, thenB.id, "then")
        ) ++ elseB.map(e => Connection(id, e.id, "else"))
        
        VisualNode(
          id = id,
          label = "if",
          nodeType = "if_expr",
          position = calculatePosition(id),
          data = js.Dynamic.literal("expression" -> expression.asInstanceOf[js.Any]),
          connections = connections
        )
      case Expression.GapExpression(id, gapId, expectedType) =>
        VisualNode(
          id = id,
          label = s"gap: ${expectedType.getOrElse("any")}",
          nodeType = "gap",
          position = calculatePosition(id),
          data = js.Dynamic.literal("expression" -> expression.asInstanceOf[js.Any])
        )
    }
  }
  
  // Extract call nodes from definition
  private def extractCallNodes(definition: Definition): List[VisualNode] = {
    // Basic implementation - analyze definition content to extract function calls
    List.empty
  }
  
  // Render graph visualization
  private def renderGraph(nodes: List[VisualNode]): Unit = {
    // Clear container
    while (container.firstChild != null) {
      container.removeChild(container.firstChild)
    }
    
    // Create canvas for rendering
    val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
    canvas.width = 800
    canvas.height = 600
    canvas.style.border = "1px solid #ccc"
    container.appendChild(canvas)
    
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    
    // Draw nodes
    nodes.foreach { node =>
      drawNode(ctx, node)
    }
    
    // Draw connections
    nodes.foreach { node =>
      node.connections.foreach { conn =>
        drawConnection(ctx, nodes.find(_.id == conn.sourceId).get, nodes.find(_.id == conn.targetId).get, conn)
      }
    }
    
    currentVisualization = Some(nodes.head)
  }
  
  // Render tree visualization
  private def renderTree(rootNode: VisualNode): Unit = {
    // Clear container
    while (container.firstChild != null) {
      container.removeChild(container.firstChild)
    }
    
    // Create tree container
    val treeContainer = dom.document.createElement("div")
    treeContainer.asInstanceOf[dom.HTMLElement].style.display = "flex"
    treeContainer.asInstanceOf[dom.HTMLElement].style.setProperty("flex-direction", "column")
    treeContainer.asInstanceOf[dom.HTMLElement].style.setProperty("align-items", "center")
    container.appendChild(treeContainer)
    
    // Render tree recursively
    renderTreeNode(treeContainer, rootNode, 0)
  }
  
  // Render tree node recursively
  private def renderTreeNode(container: dom.Element, node: VisualNode, depth: Int): Unit = {
    val nodeElement = dom.document.createElement("div")
    nodeElement.asInstanceOf[dom.HTMLElement].style.padding = "10px"
    nodeElement.asInstanceOf[dom.HTMLElement].style.margin = "5px"
    nodeElement.asInstanceOf[dom.HTMLElement].style.border = "1px solid #ccc"
    nodeElement.asInstanceOf[dom.HTMLElement].style.borderRadius = "5px"
    nodeElement.asInstanceOf[dom.HTMLElement].style.backgroundColor = getNodeColor(node.nodeType)
    nodeElement.textContent = node.label
    container.appendChild(nodeElement)
    
    // Add children
    if (node.connections.nonEmpty) {
      val childrenContainer = dom.document.createElement("div")
      childrenContainer.asInstanceOf[dom.HTMLElement].style.display = "flex"
      childrenContainer.asInstanceOf[dom.HTMLElement].style.setProperty("justify-content", "center")
      childrenContainer.asInstanceOf[dom.HTMLElement].style.marginTop = "10px"
      container.appendChild(childrenContainer)
      
      node.connections.foreach { conn =>
        // This would render child nodes - simplified for now
        val childElement = dom.document.createElement("div")
        childElement.asInstanceOf[dom.HTMLElement].style.padding = "8px"
        childElement.asInstanceOf[dom.HTMLElement].style.margin = "2px"
        childElement.asInstanceOf[dom.HTMLElement].style.border = "1px solid #eee"
        childElement.textContent = s"${conn.label}: ${conn.targetId}"
        childrenContainer.appendChild(childElement)
      }
    }
  }
  
  // Render flowchart visualization
  private def renderFlowchart(nodes: List[VisualNode]): Unit = {
    // Clear container
    while (container.firstChild != null) {
      container.removeChild(container.firstChild)
    }
    
    // Create flowchart container
    val flowchartContainer = dom.document.createElement("div")
    flowchartContainer.asInstanceOf[dom.HTMLElement].style.display = "flex"
    flowchartContainer.asInstanceOf[dom.HTMLElement].style.setProperty("flex-direction", "column")
    flowchartContainer.asInstanceOf[dom.HTMLElement].style.setProperty("align-items", "center")
    container.appendChild(flowchartContainer)
    
    // Render flowchart nodes
    nodes.foreach { node =>
      val nodeElement = dom.document.createElement("div")
      nodeElement.asInstanceOf[dom.HTMLElement].style.padding = "15px"
      nodeElement.asInstanceOf[dom.HTMLElement].style.margin = "10px"
      nodeElement.asInstanceOf[dom.HTMLElement].style.border = "2px solid #333"
      nodeElement.asInstanceOf[dom.HTMLElement].style.borderRadius = "8px"
      nodeElement.asInstanceOf[dom.HTMLElement].style.backgroundColor = getNodeColor(node.nodeType)
      nodeElement.asInstanceOf[dom.HTMLElement].style.minWidth = "150px"
      nodeElement.asInstanceOf[dom.HTMLElement].style.textAlign = "center"
      nodeElement.textContent = node.label
      flowchartContainer.appendChild(nodeElement)
    }
  }
  
  // Draw a node on canvas
  private def drawNode(ctx: dom.CanvasRenderingContext2D, node: VisualNode): Unit = {
    ctx.fillStyle = getNodeColor(node.nodeType)
    ctx.strokeStyle = "#333"
    ctx.lineWidth = 2.0
    
    // Draw node rectangle
    ctx.fillRect(node.position.x, node.position.y, 120, 60)
    ctx.strokeRect(node.position.x, node.position.y, 120, 60)
    
    // Draw node label
    ctx.fillStyle = "#000"
    ctx.font = "12px Arial"
    ctx.textAlign = "center"
    ctx.textBaseline = "middle"
    ctx.fillText(node.label, node.position.x + 60, node.position.y + 30)
  }
  
  // Draw a connection between nodes
  private def drawConnection(ctx: dom.CanvasRenderingContext2D, source: VisualNode, target: VisualNode, connection: Connection): Unit = {
    ctx.strokeStyle = "#666"
    ctx.lineWidth = 1.5
    
    // Draw line from source to target
    ctx.beginPath()
    ctx.moveTo(source.position.x + 60, source.position.y + 60)
    ctx.lineTo(target.position.x + 60, target.position.y)
    ctx.stroke()
    
    // Draw arrowhead
    val angle = Math.atan2(target.position.y - source.position.y, target.position.x - source.position.x)
    val arrowLength = 10
    val arrowAngle = Math.PI / 6
    
    ctx.beginPath()
    ctx.moveTo(target.position.x + 60, target.position.y)
    ctx.lineTo(
      target.position.x + 60 - arrowLength * Math.cos(angle - arrowAngle),
      target.position.y - arrowLength * Math.sin(angle - arrowAngle)
    )
    ctx.moveTo(target.position.x + 60, target.position.y)
    ctx.lineTo(
      target.position.x + 60 - arrowLength * Math.cos(angle + arrowAngle),
      target.position.y - arrowLength * Math.sin(angle + arrowAngle)
    )
    ctx.stroke()
  }
  
  // Get color for node type
  private def getNodeColor(nodeType: String): String = {
    nodeType match {
      case "literal" => "#e8f4f8"
      case "variable" => "#f0f8e8"
      case "function_call" => "#f8e8f0"
      case "binary_op" => "#f8f0e8"
      case "if_expr" => "#f0e8f8"
      case "gap" => "#f8f8e8"
      case "function" => "#e8f8f8"
      case "type" => "#f8e8e8"
      case _ => "#f8f8f8"
    }
  }
  
  // Get current visualization
  def getCurrentVisualization(): Option[VisualNode] = currentVisualization
  
  // Clear visualization
  def clear(): Unit = {
    while (container.firstChild != null) {
      container.removeChild(container.firstChild)
    }
    currentVisualization = None
  }
}

// Global diagram renderer instance
object DiagramRenderer {
  private var renderer: Option[DiagramRenderer] = None
  
  def initialize(container: dom.Element): Unit = {
    renderer = Some(new DiagramRenderer(container))
  }
  
  def setVisualizationType(vType: VisualizationType): Unit = {
    renderer.foreach(_.setVisualizationType(vType))
  }
  
  def renderProjectGraph(project: Project): Unit = {
    renderer.foreach(_.renderProjectGraph(project))
  }
  
  def renderExpressionTree(expression: Expression): Unit = {
    renderer.foreach(_.renderExpressionTree(expression))
  }
  
  def renderCallHierarchy(definition: Definition): Unit = {
    renderer.foreach(_.renderCallHierarchy(definition))
  }
  
  def getCurrentVisualization(): Option[VisualNode] = {
    renderer.flatMap(_.getCurrentVisualization())
  }
  
  def clear(): Unit = {
    renderer.foreach(_.clear())
  }
}

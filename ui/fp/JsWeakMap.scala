package opelan.ui.fp

import scala.scalajs.js
import org.scalajs.dom

// JS WeakMap facade — scalajs-dom does not provide one, and
// mutable.WeakHashMap does not link under Scala.js.
@js.native
@js.annotation.JSGlobal("WeakMap")
private[fp] class JsWeakMap[V] extends js.Object {
  def get(key: dom.Element): js.UndefOr[V] = js.native
  def set(key: dom.Element, value: V): Unit = js.native
}

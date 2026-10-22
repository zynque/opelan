package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.means._

// A rule set as document nodes — the transitional encoding: each rule
// as its text, the binding list as tiers of pattern texts, worked
// examples as input/expected pairs. The text form is what a user edits
// in place; decode reads it back through readRuleOrHole, so a line that
// doesn't parse lands as a hole rule rather than breaking the def.
// The by-reference form — pattern pieces and constructors as nodes
// linked to schema entries — is Phase C's structured encoding.
private[langdef] def rulesNode(set: RuleSet): DetachedNode[NodeData] =
  n(pending("rules"),
    (set.rules.map(r => n(pending("rule"), sl(printRule(r)))) ++
      bindingNode(set.tiers) ++
      set.examples.map(e =>
        n(pending("example"), sl(e.input), sl(e.expected))))*)

private def bindingNode(tiers: List[Tier]): List[DetachedNode[NodeData]] =
  if (tiers.isEmpty) Nil
  else List(n(pending("binding"),
    tiers.map(t => n(pending("tier"),
      t.patterns.map(p => sl(printPattern(p)))*))*))

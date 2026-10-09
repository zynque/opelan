package opelan.collaboration

import scala.util.Random

// Session identity until accounts exist: a unique Automerge actor id per
// session (tabs resuming the same persisted bytes must not share one —
// changes would collide on (actor, seq)) plus a generated display name
// used to tag changes in the shared history.

// Automerge actor ids are 64 lowercase hex chars.
def freshActorId(rng: Random = Random): String =
  List.fill(64)("0123456789abcdef" (rng.nextInt(16))).mkString

private val adjectives = Vector(
  "amber", "brisk", "calm", "dusky", "ember", "frost", "golden",
  "hollow", "ivory", "lucid", "misty", "nimble", "opal", "quiet",
  "rusty", "sable")
private val animals = Vector(
  "heron", "fox", "otter", "lynx", "wren", "badger", "mole", "newt",
  "stoat", "finch", "hare", "rook", "vole", "asp", "toad", "elk")

def generateName(rng: Random = Random): String = {
  val a = adjectives(rng.nextInt(adjectives.length))
  val n = animals(rng.nextInt(animals.length))
  s"$a-$n"
}

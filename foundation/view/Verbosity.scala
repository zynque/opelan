package opelan.foundation.view

// The detail-level zoom axis (DESIGN.md §6, *Zoom navigation*): how much
// of *what* the content says a projection shows. Never selects
// implementation — that is a separate axis.
enum Verbosity {
  case Terse, Verbose
}

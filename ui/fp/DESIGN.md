# `ui/fp/` — design

The component runtime: a small Elm/Tyrian-style framework, built after
p3's lesson that a hand-grown ad-hoc UI layer becomes unmanageable.

- `Component[I, O]` — `init`, `update` (pure: state + input →
  `Update(state, outputs)`), `view` (the desired `View[I]` tree), and
  `children` (desired keyed child descriptions). One input channel
  carries everything: view events, parent-pushed props, and routed child
  outputs. Effects are *output values*, interpreted by whoever mounts
  the component.
- `Runtime` — a single FIFO dispatch queue (no re-entrancy). The queue
  is the intended hook for an input log — itself, per the *Language*
  principle, a document.
- `Render`/`Patch` — `View` → DOM, then positional patching that reuses
  nodes where the shape matches, which keeps focus and in-progress input
  alive across re-renders. Event handlers resolve through a per-element
  table, so listeners are never re-bound. `data-focus`/`data-scroll`
  give declarative post-render focus and scroll.
- `Reconcile` — keyed child instances: same key and component → reuse
  and push changed input; new keys mount; missing keys are destroyed.
- `View.Managed` — the escape hatch for imperative widgets that own
  their DOM (CodeMirror); keyed, and remounted when the key changes.
- `Handle`/`Subject` — the external face of a mounted tree: push inputs,
  subscribe to outputs.

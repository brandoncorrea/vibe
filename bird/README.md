# Flappy Bird (ClojureScript)

A small Flappy Bird clone built with ClojureScript + shadow-cljs, drawn on a
2D canvas. No external dependencies beyond shadow-cljs itself.

## Run

```bash
npm install
npm run watch
```

Then open <http://localhost:8080>.

## Controls

- **Space / ↑ / W** — flap
- **Click / tap** — flap
- On the Game Over screen, click or press space to play again.

## Layout

- `src/bird/config.cljs` — tunable constants (gravity, pipe gap, colors, …)
- `src/bird/state.cljs`  — initial state shape
- `src/bird/game.cljs`   — physics, pipe spawning, collisions, scoring
- `src/bird/render.cljs` — canvas drawing
- `src/bird/core.cljs`   — entry point, frame loop, input

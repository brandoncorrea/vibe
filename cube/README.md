# cube

A spinnable cube lit by a colored point light, drawn in ClojureScript with
plain 2D canvas (rotation + projection + Lambert shading, no 3D lib).

Three slider bars set the cube's X / Y / Z rotation in degrees.

## Run

```bash
npm install
npx shadow-cljs watch app
```

Open <http://localhost:8080>.

## Test

```bash
npm test
```

Runs the pure-function tests under `test/cube/core_test.cljs` via shadow-cljs'
node-test target.

## Knobs

In `src/cube/core.cljs`:

- `light-pos` — world-space position of the colored light
- `light-color` — RGB (0..1) of the light
- `ambient` — base color of unlit faces
- `cam-distance` / `focal` — perspective camera

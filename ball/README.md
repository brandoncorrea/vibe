# ball

Bouncing red sphere in ClojureScript. Gravity pulls it down, the floor *adds*
energy on impact (so it bounces higher over time), walls dampen, and a fading
trail follows it. Click anywhere to teleport the ball.

## Run

```bash
npm install
npx shadow-cljs watch app
```

Open <http://localhost:8080>.

## Physics knobs

In `src/ball/core.cljs`:

- `gravity` — per-frame downward acceleration
- `energy-boost` — floor restitution (>1 adds energy)
- `wall-damping` — wall/ceiling restitution
- `max-trail` — trail length

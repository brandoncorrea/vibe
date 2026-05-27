# Photographs chart

ClojureScript app rendering estimated photographs taken worldwide per 5-year period (1976–2025).

Built with [shadow-cljs](https://github.com/thheller/shadow-cljs) and [Reagent](https://reagent-project.github.io/).

## Development

```bash
npm install
npm run watch
```

Then open <http://localhost:8088>. Source changes hot-reload.
The shadow-cljs dashboard is at <http://localhost:9633>.

### Port choices

The sibling shadow-cljs projects in this repo (`ball`, `cube`, `landing`) all
declare a build named `:app` and grab ports starting at 8080. To stay out of
their way this project uses **build name `:chart`** and a dedicated set of
ports — 8088 (HTTP), 9633 (shadow UI), 9088 (nREPL). If you spawn a fourth
sibling project, give it its own unique build name and ports too: shadow-cljs
will otherwise reuse a running server and collide on the shared build id.

## Production build

```bash
npm run release
```

Output goes to `public/js/main.js`. Serve `public/` with any static file server.

## Layout

```
src/chart/
├── core.cljs   entry point and lifecycle hooks
├── data.cljs   the dataset
├── scale.cljs  pure formatting / layout math
└── views.cljs  Reagent components
```

# landing

ClojureScript landing page that lists the vibe-coded exercises (`ball`, `cube`)
as cards with tiny animated previews — the bouncing ball card renders a real
physics-step preview, and the cube card renders a live wireframe cube.

## Run (landing page only)

```bash
npm install
npx shadow-cljs watch app
```

Open <http://localhost:8090>.

The cards link to `./ball/` and `./cube/`. During landing-only dev those
links 404 unless you also build the sibling exercises into `public/`.

## Bundle everything for deploy

From the repo root:

```bash
# Build each exercise as a release bundle
(cd ball  && npm install && npx shadow-cljs release app)
(cd cube  && npm install && npx shadow-cljs release app)
(cd landing && npm install && npx shadow-cljs release app)

# Stage the exercises under landing/public/
mkdir -p landing/public/ball landing/public/cube
cp -r ball/public/*  landing/public/ball/
cp -r cube/public/*  landing/public/cube/

# landing/public is now a fully self-contained static site
```

Serve `landing/public/` with any static host (Netlify, GitHub Pages, `python3
-m http.server`, etc.). The cards' relative `./ball/` and `./cube/` hrefs will
resolve to the staged bundles.

## Adding a new exercise

Append a map to `exercises` in `src/landing/core.cljs`:

```clojure
{:id      :my-thing
 :title   "My Thing"
 :blurb   "What it does."
 :tags    ["tag-a" "tag-b"]
 :accent  "#abcdef"
 :href    "./my-thing/"
 :preview :ball}   ;; reuse an existing preview animation
```

Add a new preview by writing a `(defn- my-preview [canvas accent] ...)` that
returns a `(fn [dt] ...)` tick function, then register it in `make-preview`.

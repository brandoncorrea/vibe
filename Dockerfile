# Multi-stage build:
#   1. node + JDK image runs `shadow-cljs release app` for ball, cube, chart,
#      bird, and landing, then stages each into landing/public/<name>/
#   2. nginx:alpine serves the merged static tree
#
# No BuildKit-specific syntax — works with the classic builder too
# (e.g. plain `docker-compose build` on a stock Arch install).

# ---- Build -----------------------------------------------------------------
FROM node:20-bookworm-slim AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends default-jre-headless ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Install npm deps first (cache layer survives source edits)
COPY ball/package.json    ball/package-lock.json    ball/
COPY cube/package.json    cube/package-lock.json    cube/
COPY chart/package.json   chart/package-lock.json   chart/
COPY bird/package.json    bird/package-lock.json    bird/
COPY landing/package.json landing/package-lock.json landing/

RUN cd ball     && npm ci \
 && cd ../cube     && npm ci \
 && cd ../chart    && npm ci \
 && cd ../bird     && npm ci \
 && cd ../landing  && npm ci

# Copy the rest of each project (sources, public/, configs)
COPY ball/    ball/
COPY cube/    cube/
COPY chart/   chart/
COPY bird/    bird/
COPY landing/ landing/

# Release builds. Most projects name their build :app; chart names its build
# :chart so sibling projects' :app builds can't collide when a shared
# shadow-cljs server is hit.
RUN cd ball     && npx shadow-cljs release app \
 && cd ../cube     && npx shadow-cljs release app \
 && cd ../chart    && npx shadow-cljs release chart \
 && cd ../bird     && npx shadow-cljs release app \
 && cd ../landing  && npx shadow-cljs release app

# Merge each exercise's build under landing/public so the landing page's
# relative ./<name>/ links resolve from a single static root.
#
# Each subproject's index.html references assets as absolute paths
# (e.g. <script src="/js/main.js">) which only works when served from
# the project's own root. After the merge, those absolute paths would
# resolve to landing's bundle. Rewriting them to be relative (no
# leading slash) makes the browser resolve `/chart/` -> `/chart/js/main.js`
# correctly.
RUN mkdir -p landing/public/ball landing/public/cube landing/public/chart landing/public/bird \
 && cp -r ball/public/.  landing/public/ball/ \
 && cp -r cube/public/.  landing/public/cube/ \
 && cp -r chart/public/. landing/public/chart/ \
 && cp -r bird/public/.  landing/public/bird/ \
 && for d in ball cube chart bird; do \
        find landing/public/$d -maxdepth 2 -name '*.html' \
            -exec sed -i -E 's#(href|src)="/#\1="#g' {} + ; \
    done

# ---- Runtime ---------------------------------------------------------------
FROM nginx:1.27-alpine

COPY --from=build /src/landing/public /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# syntax=docker/dockerfile:1.7
#
# Multi-stage build:
#   1. node + JDK image runs `shadow-cljs release app` for ball, cube, landing
#      and stages the outputs into landing/public/{ball,cube}/
#   2. nginx:alpine serves the merged static tree

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

RUN --mount=type=cache,target=/root/.npm \
    cd ball     && npm ci \
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

# Release builds. ~/.m2 cache mount avoids re-downloading Maven deps on rebuild.
# Most projects name their build :app; chart names its build :chart so sibling
# projects' :app builds can't collide when a shared shadow-cljs server is hit.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.gitlibs \
    cd ball     && npx shadow-cljs release app \
 && cd ../cube     && npx shadow-cljs release app \
 && cd ../chart    && npx shadow-cljs release chart \
 && cd ../bird     && npx shadow-cljs release app \
 && cd ../landing  && npx shadow-cljs release app

# Merge each exercise's build under landing/public so the landing page's
# relative ./<name>/ links resolve from a single static root.
RUN mkdir -p landing/public/ball landing/public/cube landing/public/chart landing/public/bird \
 && cp -r ball/public/.  landing/public/ball/ \
 && cp -r cube/public/.  landing/public/cube/ \
 && cp -r chart/public/. landing/public/chart/ \
 && cp -r bird/public/.  landing/public/bird/

# ---- Runtime ---------------------------------------------------------------
FROM nginx:1.27-alpine

COPY --from=build /src/landing/public /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

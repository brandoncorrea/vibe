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
COPY landing/package.json landing/package-lock.json landing/

RUN --mount=type=cache,target=/root/.npm \
    cd ball     && npm ci \
 && cd ../cube     && npm ci \
 && cd ../landing  && npm ci

# Copy the rest of each project (sources, public/, configs)
COPY ball/    ball/
COPY cube/    cube/
COPY landing/ landing/

# Release builds. ~/.m2 cache mount avoids re-downloading Maven deps on rebuild.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.gitlibs \
    cd ball     && npx shadow-cljs release app \
 && cd ../cube     && npx shadow-cljs release app \
 && cd ../landing  && npx shadow-cljs release app

# Merge the exercise builds under landing/public so the landing page's
# relative ./ball/ and ./cube/ links resolve from a single static root.
RUN mkdir -p landing/public/ball landing/public/cube \
 && cp -r ball/public/. landing/public/ball/ \
 && cp -r cube/public/. landing/public/cube/

# ---- Runtime ---------------------------------------------------------------
FROM nginx:1.27-alpine

COPY --from=build /src/landing/public /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

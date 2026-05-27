(ns bird.config
  "Tunable constants for the world, bird, and pipes.")

(def world-w 480)
(def world-h 720)

(def ground-h 90)

(def gravity 0.42)
(def flap-impulse -7.4)
(def max-fall-speed 11)
(def bird-x 130)
(def bird-r 16)
(def tilt-up -0.5)
(def tilt-down 1.2)

(def pipe-w 64)
(def pipe-gap 165)
(def pipe-min-top 80)
(def pipe-spacing 230)
(def pipe-speed 2.6)

(def colors
  {:sky-top    "#79c8ff"
   :sky-bot    "#cfeeff"
   :night-top  "#16314f"
   :night-bot  "#3f6c8f"
   :pipe-fill  "#5fc25a"
   :pipe-dark  "#2e7a35"
   :pipe-light "#a6e69d"
   :pipe-rim   "#1f3a1a"
   :ground     "#d4b56b"
   :ground-dk  "#9c8344"
   :grass      "#7bc24d"
   :bird-body  "#ffd24a"
   :bird-belly "#fff3b0"
   :bird-wing  "#f0a832"
   :bird-beak  "#ff7a1a"
   :bird-eye   "#222"})

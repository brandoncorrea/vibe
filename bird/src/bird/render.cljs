(ns bird.render
  "Canvas drawing."
  (:require [bird.config :as cfg]
            [clojure.math :as math]))

(def TAU (* 2 math/PI))

(defn- clamp [v lo hi] (max lo (min hi v)))

(defn- draw-sky! [ctx tick]
  (let [grad (.createLinearGradient ctx 0 0 0 cfg/world-h)]
    (.addColorStop grad 0 (:sky-top cfg/colors))
    (.addColorStop grad 1 (:sky-bot cfg/colors))
    (set! (.-fillStyle ctx) grad)
    (.fillRect ctx 0 0 cfg/world-w cfg/world-h))
  (set! (.-fillStyle ctx) "rgba(255,255,255,0.85)")
  (doseq [i (range 4)]
    (let [base-x (mod (- (* i 160) (* 0.25 tick)) (+ cfg/world-w 200))
          x (- base-x 100)
          y (+ 60 (* i 40))]
      (.beginPath ctx)
      (.arc ctx x y 18 0 TAU)
      (.arc ctx (+ x 22) (- y 6) 22 0 TAU)
      (.arc ctx (+ x 46) y 18 0 TAU)
      (.arc ctx (+ x 24) (+ y 8) 18 0 TAU)
      (.fill ctx))))

(defn- draw-pipes! [ctx pipes]
  (let [ground-y (- cfg/world-h cfg/ground-h)]
    (doseq [{:keys [x gap-y]} pipes]
      (let [bot-y (+ gap-y cfg/pipe-gap)
            cap-h 24
            ;; vertical gradient for shading
            grad (.createLinearGradient ctx x 0 (+ x cfg/pipe-w) 0)]
        (.addColorStop grad 0 (:pipe-dark cfg/colors))
        (.addColorStop grad 0.35 (:pipe-fill cfg/colors))
        (.addColorStop grad 0.55 (:pipe-light cfg/colors))
        (.addColorStop grad 1 (:pipe-dark cfg/colors))
        (set! (.-fillStyle ctx) grad)
        ;; top pipe body
        (.fillRect ctx x 0 cfg/pipe-w (- gap-y cap-h))
        ;; bottom pipe body
        (.fillRect ctx x (+ bot-y cap-h) cfg/pipe-w (- ground-y (+ bot-y cap-h)))
        ;; caps (slightly wider)
        (let [cx (- x 4)
              cw (+ cfg/pipe-w 8)
              cgrad (.createLinearGradient ctx cx 0 (+ cx cw) 0)]
          (.addColorStop cgrad 0 (:pipe-dark cfg/colors))
          (.addColorStop cgrad 0.4 (:pipe-fill cfg/colors))
          (.addColorStop cgrad 0.6 (:pipe-light cfg/colors))
          (.addColorStop cgrad 1 (:pipe-dark cfg/colors))
          (set! (.-fillStyle ctx) cgrad)
          (.fillRect ctx cx (- gap-y cap-h) cw cap-h)
          (.fillRect ctx cx bot-y cw cap-h))
        ;; rim outline
        (set! (.-strokeStyle ctx) (:pipe-rim cfg/colors))
        (set! (.-lineWidth ctx) 2)
        (.strokeRect ctx (- x 4) (- gap-y cap-h) (+ cfg/pipe-w 8) cap-h)
        (.strokeRect ctx (- x 4) bot-y (+ cfg/pipe-w 8) cap-h)))))

(defn- draw-ground! [ctx tick]
  (let [gy (- cfg/world-h cfg/ground-h)]
    ;; grass strip
    (set! (.-fillStyle ctx) (:grass cfg/colors))
    (.fillRect ctx 0 gy cfg/world-w 14)
    ;; dirt
    (let [grad (.createLinearGradient ctx 0 (+ gy 14) 0 cfg/world-h)]
      (.addColorStop grad 0 (:ground cfg/colors))
      (.addColorStop grad 1 (:ground-dk cfg/colors))
      (set! (.-fillStyle ctx) grad)
      (.fillRect ctx 0 (+ gy 14) cfg/world-w (- cfg/ground-h 14)))
    ;; scrolling stripes
    (set! (.-strokeStyle ctx) "rgba(0,0,0,0.18)")
    (set! (.-lineWidth ctx) 2)
    (let [offset (mod (* tick cfg/pipe-speed) 24)]
      (doseq [i (range -1 (inc (quot cfg/world-w 24)))]
        (let [x (- (* i 24) offset)]
          (.beginPath ctx)
          (.moveTo ctx x (+ gy 18))
          (.lineTo ctx (+ x 12) cfg/world-h)
          (.stroke ctx))))))

(defn- draw-bird! [ctx {:keys [x y vy flap-anim]}]
  (let [tilt (clamp (* 0.05 vy) cfg/tilt-up cfg/tilt-down)
        wing-up? (pos? flap-anim)]
    (.save ctx)
    (.translate ctx x y)
    (.rotate ctx tilt)
    ;; soft shadow
    (set! (.-fillStyle ctx) "rgba(0,0,0,0.18)")
    (.beginPath ctx)
    (.ellipse ctx 2 4 (+ cfg/bird-r 2) (* 0.78 cfg/bird-r) 0 0 TAU)
    (.fill ctx)
    ;; body
    (let [grad (.createRadialGradient ctx -4 -4 2 0 0 cfg/bird-r)]
      (.addColorStop grad 0 "#fff2b0")
      (.addColorStop grad 0.5 (:bird-body cfg/colors))
      (.addColorStop grad 1 "#c68a18")
      (set! (.-fillStyle ctx) grad))
    (.beginPath ctx)
    (.ellipse ctx 0 0 (+ cfg/bird-r 2) (* 0.85 cfg/bird-r) 0 0 TAU)
    (.fill ctx)
    ;; belly
    (set! (.-fillStyle ctx) (:bird-belly cfg/colors))
    (.beginPath ctx)
    (.ellipse ctx 2 5 (* 0.7 cfg/bird-r) (* 0.5 cfg/bird-r) 0 0 TAU)
    (.fill ctx)
    ;; wing
    (set! (.-fillStyle ctx) (:bird-wing cfg/colors))
    (.beginPath ctx)
    (if wing-up?
      (.ellipse ctx -2 -6 (* 0.65 cfg/bird-r) (* 0.32 cfg/bird-r) -0.5 0 TAU)
      (.ellipse ctx -2 4 (* 0.7 cfg/bird-r) (* 0.4 cfg/bird-r) 0.4 0 TAU))
    (.fill ctx)
    (set! (.-strokeStyle ctx) "rgba(0,0,0,0.25)")
    (set! (.-lineWidth ctx) 1.5)
    (.stroke ctx)
    ;; eye white
    (set! (.-fillStyle ctx) "#fff")
    (.beginPath ctx)
    (.arc ctx 7 -5 5 0 TAU)
    (.fill ctx)
    ;; pupil
    (set! (.-fillStyle ctx) (:bird-eye cfg/colors))
    (.beginPath ctx)
    (.arc ctx 8 -5 2.4 0 TAU)
    (.fill ctx)
    ;; beak
    (set! (.-fillStyle ctx) (:bird-beak cfg/colors))
    (.beginPath ctx)
    (.moveTo ctx 10 -1)
    (.lineTo ctx 24 0)
    (.lineTo ctx 10 5)
    (.closePath ctx)
    (.fill ctx)
    (set! (.-strokeStyle ctx) "rgba(0,0,0,0.3)")
    (.stroke ctx)
    ;; body outline
    (set! (.-strokeStyle ctx) "rgba(0,0,0,0.35)")
    (set! (.-lineWidth ctx) 1.5)
    (.beginPath ctx)
    (.ellipse ctx 0 0 (+ cfg/bird-r 2) (* 0.85 cfg/bird-r) 0 0 TAU)
    (.stroke ctx)
    (.restore ctx)))

(defn- centered-text! [ctx s y font color]
  (set! (.-font ctx) font)
  (set! (.-textAlign ctx) "center")
  (set! (.-textBaseline ctx) "alphabetic")
  (set! (.-fillStyle ctx) color)
  (.fillText ctx s (/ cfg/world-w 2) y)
  (set! (.-textAlign ctx) "start"))

(defn- draw-score! [ctx score]
  (set! (.-textAlign ctx) "center")
  (set! (.-font ctx) "bold 64px -apple-system, system-ui, sans-serif")
  (set! (.-lineWidth ctx) 6)
  (set! (.-strokeStyle ctx) "rgba(0,0,0,0.65)")
  (.strokeText ctx (str score) (/ cfg/world-w 2) 96)
  (set! (.-fillStyle ctx) "#fff")
  (.fillText ctx (str score) (/ cfg/world-w 2) 96)
  (set! (.-textAlign ctx) "start"))

(defn- draw-panel! [ctx x y w h]
  (set! (.-fillStyle ctx) "rgba(0, 0, 0, 0.55)")
  (.beginPath ctx)
  (.roundRect ctx x y w h 14)
  (.fill ctx)
  (set! (.-strokeStyle ctx) "rgba(255,255,255,0.18)")
  (set! (.-lineWidth ctx) 1)
  (.stroke ctx))

(defn- draw-ready! [ctx tick]
  (draw-panel! ctx 60 220 360 220)
  (centered-text! ctx "FLAPPY" 290 "bold 56px -apple-system, system-ui, sans-serif" "#ffd24a")
  (centered-text! ctx "BIRD"   345 "bold 56px -apple-system, system-ui, sans-serif" "#ffd24a")
  (let [pulse (+ 0.6 (* 0.4 (math/sin (* 0.1 tick))))]
    (set! (.-globalAlpha ctx) pulse)
    (centered-text! ctx "tap or press space to flap" 405
                    "16px -apple-system, system-ui, sans-serif"
                    "#fff")
    (set! (.-globalAlpha ctx) 1)))

(defn- draw-game-over! [ctx score best]
  (draw-panel! ctx 60 200 360 280)
  (centered-text! ctx "GAME OVER" 270
                  "bold 44px -apple-system, system-ui, sans-serif"
                  "#ff7a7a")
  (centered-text! ctx (str "Score   " score) 340
                  "22px -apple-system, system-ui, sans-serif"
                  "#fff")
  (centered-text! ctx (str "Best    " best) 372
                  "22px -apple-system, system-ui, sans-serif"
                  "#ffd24a")
  (centered-text! ctx "click / space to play again" 440
                  "15px -apple-system, system-ui, sans-serif"
                  "rgba(255,255,255,0.85)"))

(defn- draw-flash! [ctx flash]
  (when (pos? flash)
    (set! (.-fillStyle ctx) (str "rgba(255,255,255," (* 0.06 flash) ")"))
    (.fillRect ctx 0 0 cfg/world-w cfg/world-h)))

(defn render! [ctx {:keys [phase bird pipes score best tick flash]}]
  (draw-sky! ctx tick)
  (draw-pipes! ctx pipes)
  (draw-ground! ctx tick)
  (draw-bird! ctx bird)
  (draw-flash! ctx flash)
  (case phase
    :ready     (draw-ready! ctx tick)
    :playing   (draw-score! ctx score)
    :game-over (do (draw-score! ctx score)
                   (draw-game-over! ctx score best))))

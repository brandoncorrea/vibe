(ns ball.render
  "Canvas drawing."
  (:require [ball.config :as cfg]
            [ball.game :as game]
            [clojure.math :as math]))

(defn clear! [ctx w h]
  (set! (.-fillStyle ctx) "rgba(17, 17, 17, 0.25)")
  (.fillRect ctx 0 0 w h))

(defn draw-trail! [ctx trail palette radius]
  (let [n (count trail)
        rgb (:trail (cfg/palettes palette))]
    (doseq [[i [tx ty]] (map-indexed vector trail)]
      (let [alpha (- 1 (/ i (max 1 n)))
            r (* radius (- 1 (/ i (* 1.5 n))))]
        (when (pos? r)
          (set! (.-fillStyle ctx)
                (str "rgba(" rgb ", " (* 0.55 alpha) ")"))
          (.beginPath ctx)
          (.arc ctx tx ty r 0 (* 2 math/PI))
          (.fill ctx))))))

(defn draw-ball! [ctx {:keys [x y palette radius hp max-hp]}]
  (let [[c0 c1 c2] (:stops (cfg/palettes palette))
        grad (.createRadialGradient ctx
                                    (- x (/ radius 3)) (- y (/ radius 3)) 2
                                    x y radius)]
    (.addColorStop grad 0 c0)
    (.addColorStop grad 0.4 c1)
    (.addColorStop grad 1 c2)
    (set! (.-fillStyle ctx) grad)
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 math/PI))
    (.fill ctx)
    (when (< hp max-hp)
      (set! (.-strokeStyle ctx) "rgba(0,0,0,0.55)")
      (set! (.-lineWidth ctx) 2)
      (.beginPath ctx)
      (.moveTo ctx (- x (* radius 0.8)) (- y (* radius 0.2)))
      (.lineTo ctx (- x (* radius 0.1)) (+ y (* radius 0.1)))
      (.lineTo ctx (+ x (* radius 0.4)) (- y (* radius 0.3)))
      (.lineTo ctx (+ x (* radius 0.7)) (+ y (* radius 0.4)))
      (.stroke ctx))))

(defn draw-player! [ctx {:keys [x y invuln facing]}]
  (when (or (zero? invuln) (odd? (quot invuln 4)))
    (let [cx (+ x (/ cfg/player-w 2))
          grad (.createLinearGradient ctx x y x (+ y cfg/player-h))]
      (.addColorStop grad 0 "#fff2a8")
      (.addColorStop grad 1 "#d49a00")
      (set! (.-fillStyle ctx) grad)
      (.beginPath ctx)
      (.roundRect ctx x y cfg/player-w cfg/player-h 8)
      (.fill ctx)
      (set! (.-fillStyle ctx) "#111")
      (let [eye-y (+ y 14)
            off (* 6 facing)]
        (.beginPath ctx)
        (.arc ctx (+ cx off -4) eye-y 2.5 0 (* 2 math/PI))
        (.arc ctx (+ cx off  6) eye-y 2.5 0 (* 2 math/PI))
        (.fill ctx)))))

(defn draw-projectiles! [ctx projectiles]
  (set! (.-fillStyle ctx) "#fff")
  (set! (.-shadowColor ctx) "rgba(255,255,255,0.9)")
  (set! (.-shadowBlur ctx) 12)
  (doseq [{:keys [x y]} projectiles]
    (.beginPath ctx)
    (.arc ctx x y cfg/proj-radius 0 (* 2 math/PI))
    (.fill ctx))
  (set! (.-shadowBlur ctx) 0))

(defn draw-powerups! [ctx powerups]
  (doseq [{:keys [kind x y ttl]} powerups]
    (let [{:keys [color glyph]} (cfg/powerup-kinds kind)
          fading? (< ttl 60)
          alpha (if fading? (/ (mod ttl 20) 20) 1)]
      (set! (.-globalAlpha ctx) (max 0.3 alpha))
      (set! (.-fillStyle ctx) color)
      (set! (.-shadowColor ctx) color)
      (set! (.-shadowBlur ctx) 14)
      (.beginPath ctx)
      (.roundRect ctx x y cfg/powerup-size cfg/powerup-size 6)
      (.fill ctx)
      (set! (.-shadowBlur ctx) 0)
      (set! (.-fillStyle ctx) "#111")
      (set! (.-font ctx) "bold 18px monospace")
      (set! (.-textAlign ctx) "center")
      (set! (.-textBaseline ctx) "middle")
      (.fillText ctx glyph (+ x (/ cfg/powerup-size 2)) (+ y (/ cfg/powerup-size 2) 1))
      (set! (.-textAlign ctx) "start")
      (set! (.-textBaseline ctx) "alphabetic")
      (set! (.-globalAlpha ctx) 1))))

(defn draw-aim! [ctx {:keys [player aim]}]
  (when aim
    (when-let [[vx vy power] (game/aim-vector aim)]
      (let [px (+ (:x player) (/ cfg/player-w 2))
            py (+ (:y player) (/ cfg/player-h 2))
            ex (+ px (* vx 8))
            ey (+ py (* vy 8))]
        (set! (.-strokeStyle ctx) (str "rgba(255,255,255," (+ 0.3 (* 0.6 power)) ")"))
        (set! (.-lineWidth ctx) 2)
        (.setLineDash ctx #js [6 6])
        (.beginPath ctx)
        (.moveTo ctx px py)
        (.lineTo ctx ex ey)
        (.stroke ctx)
        (.setLineDash ctx #js [])
        (set! (.-fillStyle ctx) "#fff")
        (.beginPath ctx)
        (.arc ctx ex ey 4 0 (* 2 math/PI))
        (.fill ctx)))))

(defn draw-hud! [ctx {:keys [lives pops score phase balls buffs w h]}]
  (set! (.-fillStyle ctx) "#fff")
  (set! (.-font ctx) "16px monospace")
  (.fillText ctx (str "LIVES " (apply str (repeat lives "♥"))) 16 28)
  (.fillText ctx (str "POPS  " pops) 16 50)
  (.fillText ctx (str "TIME  " (.toFixed (/ score 60) 1) "s") 16 72)
  (.fillText ctx (str "LEFT  " (count balls)) 16 94)
  (when-let [ms (:multishot buffs)]
    (set! (.-fillStyle ctx) (:color (cfg/powerup-kinds :multishot)))
    (.fillText ctx (str "MULTISHOT " (.toFixed (/ ms 60) 1) "s") 16 120))
  (when (#{:game-over :win} phase)
    (set! (.-fillStyle ctx) "rgba(0,0,0,0.6)")
    (.fillRect ctx 0 0 w h)
    (set! (.-textAlign ctx) "center")
    (set! (.-fillStyle ctx) (if (= phase :win) "#9fffa6" "#ff9090"))
    (set! (.-font ctx) "bold 64px monospace")
    (.fillText ctx (if (= phase :win) "YOU WIN" "GAME OVER") (/ w 2) (/ h 2))
    (set! (.-fillStyle ctx) "#fff")
    (set! (.-font ctx) "20px monospace")
    (.fillText ctx (str "Pops " pops "  ·  Time " (.toFixed (/ score 60) 1) "s")
               (/ w 2) (+ (/ h 2) 40))
    (.fillText ctx "click to play again" (/ w 2) (+ (/ h 2) 72))
    (set! (.-textAlign ctx) "start")))

(defn render! [ctx {:keys [balls player projectiles powerups w h] :as s}]
  (clear! ctx w h)
  (doseq [{:keys [trail palette radius]} balls]
    (draw-trail! ctx trail palette radius))
  (doseq [b balls]
    (draw-ball! ctx b))
  (draw-powerups! ctx powerups)
  (draw-projectiles! ctx projectiles)
  (draw-player! ctx player)
  (draw-aim! ctx s)
  (draw-hud! ctx s))

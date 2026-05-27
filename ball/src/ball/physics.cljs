(ns ball.physics
  "Per-entity time-stepping. Each step-* function takes one entity (plus
  whatever ambient world data it needs) and returns the next entity, or
  nil if it should despawn."
  (:require [ball.config :as cfg]
            [ball.math :as m]
            [clojure.math :as math]))

(defn homing-accel [{bx :x by :y homing :homing} {px :x py :y}]
  (if (zero? homing)
    [0 0]
    (let [pcx (+ px (/ cfg/player-w 2))
          pcy (+ py (/ cfg/player-h 2))
          dx (- pcx bx) dy (- pcy by)
          len (math/sqrt (+ (* dx dx) (* dy dy)))]
      (if (< len 1)
        [0 0]
        [(* (/ dx len) homing) (* (/ dy len) homing)]))))

(defn step-ball [w h player {:keys [x y vx vy trail radius bounce-gain max-speed] :as b}]
  (let [[hx hy] (homing-accel b player)
        vx0 (+ vx hx)
        vy0 (+ vy hy cfg/gravity)
        x*  (+ x vx0)
        y*  (+ y vy0)
        floor (- h radius)
        ceil  radius
        right (- w radius)
        left  radius
        [x** vxw] (cond
                    (> x* right) [right (* (- vx0) cfg/wall-damping)]
                    (< x* left)  [left  (* (- vx0) cfg/wall-damping)]
                    :else        [x* vx0])
        [y** vyw] (cond
                    (>= y* floor) [floor (* (- vy0) bounce-gain)]
                    (<= y* ceil)  [ceil  (* (- vy0) cfg/wall-damping)]
                    :else         [y* vy0])
        [vx** vy**] (m/clamp-speed vxw vyw max-speed)
        trail* (take cfg/max-trail (conj trail [x** y**]))]
    (assoc b :x x** :y y** :vx vx** :vy vy** :trail trail*)))

(defn step-player [w h keys {:keys [x y vy on-floor invuln facing] :as p}]
  (let [left?  (contains? keys :left)
        right? (contains? keys :right)
        jump?  (contains? keys :jump)
        ax (cond left? (- cfg/player-speed) right? cfg/player-speed :else 0)
        facing* (cond left? -1 right? 1 :else facing)
        vy0 (if (and jump? on-floor) (- cfg/player-jump) vy)
        vy1 (+ vy0 cfg/player-gravity)
        x*  (+ x ax)
        y*  (+ y vy1)
        floor (- h cfg/player-h)
        right-wall (- w cfg/player-w)
        x** (-> x* (max 0) (min right-wall))
        [y** vy** on-floor*]
        (if (>= y* floor)
          [floor 0 true]
          [y* vy1 false])]
    (assoc p
           :x x** :y y** :vx ax :vy vy**
           :on-floor on-floor*
           :facing facing*
           :invuln (max 0 (dec invuln)))))

(defn step-projectile [w h {:keys [x y vx vy] :as p}]
  (let [x* (+ x vx) y* (+ y vy)
        r cfg/proj-radius]
    (when (and (>= x* (- r)) (<= x* (+ w r))
               (>= y* (- r)) (<= y* (+ h r)))
      (assoc p :x x* :y y*))))

(defn step-powerup [h {:keys [y vy ttl] :as pu}]
  (let [floor (- h cfg/powerup-size)
        vy*  (+ vy cfg/powerup-gravity)
        y*   (+ y vy*)
        [y** vy**] (if (>= y* floor) [floor 0] [y* vy*])
        ttl* (dec ttl)]
    (when (pos? ttl*)
      (assoc pu :y y** :vy vy** :ttl ttl*))))

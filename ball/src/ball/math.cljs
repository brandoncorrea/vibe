(ns ball.math
  "Vector and geometry helpers."
  (:require [clojure.math :as math]))

(defn dist2 [ax ay bx by]
  (let [dx (- ax bx) dy (- ay by)]
    (+ (* dx dx) (* dy dy))))

(defn clamp-speed [vx vy max-speed]
  (let [sp (math/sqrt (+ (* vx vx) (* vy vy)))]
    (if (> sp max-speed)
      (let [k (/ max-speed sp)] [(* vx k) (* vy k)])
      [vx vy])))

(defn rotate [vx vy angle]
  (let [c (math/cos angle) s (math/sin angle)]
    [(- (* vx c) (* vy s))
     (+ (* vx s) (* vy c))]))

(defn rect-circle-hit? [px py pw ph cx cy cr]
  (let [nx (-> cx (max px) (min (+ px pw)))
        ny (-> cy (max py) (min (+ py ph)))
        dx (- cx nx)
        dy (- cy ny)]
    (<= (+ (* dx dx) (* dy dy)) (* cr cr))))

(defn rect-rect-hit? [ax ay aw ah bx by bw bh]
  (and (< ax (+ bx bw)) (> (+ ax aw) bx)
       (< ay (+ by bh)) (> (+ ay ah) by)))

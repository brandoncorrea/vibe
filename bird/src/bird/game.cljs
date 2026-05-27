(ns bird.game
  "Game logic: physics, pipe spawning, collisions, scoring."
  (:require [bird.config :as cfg]))

(defn- rand-gap-y []
  (let [min-y cfg/pipe-min-top
        max-y (- cfg/world-h cfg/ground-h cfg/pipe-gap cfg/pipe-min-top)]
    (+ min-y (rand-int (max 1 (- max-y min-y))))))

(defn- new-pipe []
  {:x      cfg/world-w
   :gap-y  (rand-gap-y)
   :scored false})

(defn flap [{:keys [phase] :as s}]
  (case phase
    :ready    (-> s
                  (assoc :phase :playing)
                  (assoc-in [:bird :vy] cfg/flap-impulse)
                  (assoc-in [:bird :flap-anim] 6))
    :playing  (-> s
                  (assoc-in [:bird :vy] cfg/flap-impulse)
                  (assoc-in [:bird :flap-anim] 6))
    :game-over s))

(defn- step-bird [{:keys [bird] :as s}]
  (let [vy (min cfg/max-fall-speed (+ (:vy bird) cfg/gravity))
        y  (+ (:y bird) vy)
        fa (max 0 (dec (:flap-anim bird)))]
    (assoc s :bird (assoc bird :vy vy :y y :flap-anim fa))))

(defn- step-spawn [{:keys [spawn-t pipes] :as s}]
  (let [t (inc spawn-t)
        spawn? (>= t (/ cfg/pipe-spacing cfg/pipe-speed))]
    (if spawn?
      (assoc s :spawn-t 0 :pipes (conj pipes (new-pipe)))
      (assoc s :spawn-t t))))

(defn- step-pipes [{:keys [pipes] :as s}]
  (let [moved (map #(update % :x - cfg/pipe-speed) pipes)
        kept  (vec (remove #(< (:x %) (- cfg/pipe-w)) moved))]
    (assoc s :pipes kept)))

(defn- score-pipes [{:keys [pipes bird score] :as s}]
  (let [bx (:x bird)
        [pipes' gained]
        (reduce
          (fn [[acc g] p]
            (if (and (not (:scored p)) (< (+ (:x p) cfg/pipe-w) bx))
              [(conj acc (assoc p :scored true)) (inc g)]
              [(conj acc p) g]))
          [[] 0]
          pipes)]
    (cond-> s
      true       (assoc :pipes pipes')
      (pos? gained) (update :score + gained))))

(defn- circle-rect-hit? [cx cy r rx ry rw rh]
  (let [nx (max rx (min cx (+ rx rw)))
        ny (max ry (min cy (+ ry rh)))
        dx (- cx nx)
        dy (- cy ny)]
    (<= (+ (* dx dx) (* dy dy)) (* r r))))

(defn- bird-hits-pipe? [bird pipe]
  (let [{:keys [x y]} bird
        {px :x gy :gap-y} pipe
        top-h gy
        bot-y (+ gy cfg/pipe-gap)
        bot-h (- cfg/world-h cfg/ground-h bot-y)]
    (or (circle-rect-hit? x y cfg/bird-r px 0 cfg/pipe-w top-h)
        (circle-rect-hit? x y cfg/bird-r px bot-y cfg/pipe-w bot-h))))

(defn- check-collisions [{:keys [bird pipes] :as s}]
  (let [{:keys [y]} bird
        ground-y (- cfg/world-h cfg/ground-h)
        hit-ground? (>= (+ y cfg/bird-r) ground-y)
        hit-ceiling? (< (- y cfg/bird-r) 0)
        hit-pipe? (some (partial bird-hits-pipe? bird) pipes)]
    (if (or hit-ground? hit-ceiling? hit-pipe?)
      (-> s
          (assoc :phase :game-over)
          (assoc :flash 12)
          (update :best max (:score s))
          (cond-> hit-ground? (assoc-in [:bird :y] (- ground-y cfg/bird-r))))
      s)))

(defn- step-game-over [{:keys [bird] :as s}]
  (let [ground-y (- cfg/world-h cfg/ground-h)
        vy (min cfg/max-fall-speed (+ (:vy bird) cfg/gravity))
        y  (min (- ground-y cfg/bird-r) (+ (:y bird) vy))]
    (-> s
        (assoc :bird (assoc bird :vy vy :y y))
        (update :flash #(max 0 (dec %))))))

(defn step [{:keys [phase] :as s}]
  (case phase
    :ready
    (update s :tick inc)

    :playing
    (-> s
        step-bird
        step-spawn
        step-pipes
        score-pipes
        check-collisions
        (update :tick inc))

    :game-over
    (-> s
        step-game-over
        (update :tick inc))))

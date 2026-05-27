(ns ball.game
  "Multi-entity resolution and the top-level step."
  (:require [ball.config :as cfg]
            [ball.math :as m]
            [ball.physics :as phys]
            [clojure.math :as math]))

;; --- Power-ups --------------------------------------------------------------

(defn spawn-powerup [x y]
  (let [kind (rand-nth (vec (keys cfg/powerup-kinds)))]
    {:kind kind
     :x    (- x (/ cfg/powerup-size 2))
     :y    y
     :vy   0
     :ttl  cfg/powerup-ttl}))

(defn apply-powerup [s kind]
  (case kind
    :life (update s :lives #(min cfg/max-lives (inc %)))
    :multishot (assoc-in s [:buffs :multishot] cfg/multishot-frames)))

(defn collect-powerups [{:keys [player powerups] :as s}]
  (let [{px :x py :y} player
        [collected remaining]
        (reduce (fn [[c r] {:keys [x y] :as pu}]
                  (if (m/rect-rect-hit? px py cfg/player-w cfg/player-h
                                        x y cfg/powerup-size cfg/powerup-size)
                    [(conj c pu) r]
                    [c (conj r pu)]))
                [[] []]
                powerups)]
    (-> s
        (assoc :powerups remaining)
        (as-> s2 (reduce #(apply-powerup %1 (:kind %2)) s2 collected)))))

(defn collect-buffs [buffs]
  (into {} (for [[k v] buffs
                 :let [v* (dec v)]
                 :when (pos? v*)]
             [k v*])))

(defn tick-buffs [state]
  (assoc state :buffs (collect-buffs state)))

;; --- Projectile/ball collisions --------------------------------------------

(defn- mark-hits [balls projectiles]
  (mapv (fn [{:keys [x y] :as p}]
          (let [idx (some (fn [[i b]]
                            (let [r (+ (:radius b) cfg/proj-radius)]
                              (when (<= (m/dist2 x y (:x b) (:y b)) (* r r))
                                i)))
                          (map-indexed vector balls))]
            (assoc p :hit idx)))
        projectiles))

(defn dead? [ball]
  (<= (:hp ball) 0))

(defn resolve-projectiles
  "Each projectile hits at most one ball. Balls dropping to 0 hp may drop a power-up."
  [{:keys [balls projectiles pops powerups] :as s}]
  (let [marked       (mark-hits balls projectiles)
        hit-counts   (frequencies (keep :hit marked))
        damaged      (map-indexed (fn [i b] (update b :hp - (get hit-counts i 0))) balls)
        killed       (filter dead? damaged)
        balls*       (vec (remove dead? damaged))
        drops        (->> killed
                          (filter (fn [_] (< (rand) cfg/drop-rate)))
                          (mapv #(spawn-powerup (:x %) (:y %))))
        projectiles* (->> marked (remove :hit) (mapv #(dissoc % :hit)))]
    (assoc s
      :balls balls*
      :projectiles projectiles*
      :pops (+ pops (count killed))
      :powerups (into powerups drops))))

;; --- Outcome checks ---------------------------------------------------------

(defn playing? [state] (= :playing (:phase state)))

(defn invulnerable? [state]
  (or (not (playing? state))
      (pos? (:invuln (:player state)))))

(defn ball-hit? [player ball]
  (let [{:keys [x y]} player
        {bx :x by :y br :radius} ball]
    (m/rect-circle-hit? x y cfg/player-w cfg/player-h bx by br)))

(defn player-hit? [{:keys [player balls]}]
  (some (partial ball-hit? player) balls))

(defn hit-player [state]
  (let [lives (dec (:lives state))]
    (-> (assoc state :lives lives)
        (assoc-in [:player :invuln] cfg/invuln-frames)
        (cond-> (zero? lives) (assoc :phase :game-over)))))

(defn check-hit [state]
  (cond
    (invulnerable? state) state
    (player-hit? state) (hit-player state)
    :else state))

(defn win? [state]
  (and (playing? state)
       (empty? (:balls state))))

(defn check-win [state]
  (cond-> state (win? state) (assoc :phase :win)))

;; --- Top-level step ---------------------------------------------------------

(defn step [{:keys [balls player projectiles powerups keys cooldown w h] :as s}]
  (let [s* (assoc s
             :balls (mapv (partial phys/step-ball w h player) balls)
             :player (phys/step-player w h keys player)
             :projectiles (->> projectiles
                               (keep (partial phys/step-projectile w h))
                               vec)
             :powerups (->> powerups
                            (keep (partial phys/step-powerup h))
                            vec)
             :cooldown (max 0 (dec cooldown)))]
    (-> s*
        (cond-> (playing? s) (update :score inc))
        tick-buffs
        resolve-projectiles
        (cond-> (playing? s) collect-powerups)
        check-hit
        check-win)))

;; --- Aim and fire -----------------------------------------------------------

(defn aim-vector
  "Returns [vx vy power 0..1] for an aim drag, or nil if shorter than min-drag."
  [{:keys [sx sy ex ey]}]
  (let [dx  (- ex sx) dy (- ey sy)
        len (math/sqrt (+ (* dx dx) (* dy dy)))]
    (when (>= len cfg/min-drag)
      (let [clamped (min len cfg/max-drag)
            power   (/ clamped cfg/max-drag)
            ux      (/ dx len) uy (/ dy len)]
        [(* ux cfg/proj-speed power) (* uy cfg/proj-speed power) power]))))

(defn fire [s]
  (if-let [[vx vy _power] (and (:aim s) (aim-vector (:aim s)))]
    (let [{:keys [player cooldown buffs]} s]
      (if (pos? cooldown)
        (assoc s :aim nil)
        (let [px    (+ (:x player) (/ cfg/player-w 2))
              py    (+ (:y player) (/ cfg/player-h 2))
              vels  (if (:multishot buffs)
                      [(m/rotate vx vy (- cfg/multishot-spread))
                       [vx vy]
                       (m/rotate vx vy cfg/multishot-spread)]
                      [[vx vy]])
              shots (mapv (fn [[vx vy]] {:x px :y py :vx vx :vy vy}) vels)]
          (-> s
              (assoc :aim nil :cooldown cfg/fire-cooldown)
              (update :projectiles into shots)))))
    (assoc s :aim nil)))

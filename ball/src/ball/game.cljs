(ns ball.game
  "Multi-entity resolution and the top-level step."
  (:require [ball.config :as cfg]
            [ball.math :as m]
            [ball.physics :as phys]))

;; --- Power-ups --------------------------------------------------------------

(defn spawn-powerup [x y]
  (let [kind (rand-nth (vec (keys cfg/powerup-kinds)))]
    {:kind kind :x (- x (/ cfg/powerup-size 2)) :y y :vy 0 :ttl cfg/powerup-ttl}))

(defn apply-powerup [s kind]
  (case kind
    :life      (update s :lives #(min cfg/max-lives (inc %)))
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

(defn tick-buffs [{:keys [buffs] :as s}]
  (assoc s :buffs (into {} (for [[k v] buffs
                                 :let [v* (dec v)]
                                 :when (pos? v*)]
                             [k v*]))))

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

(defn resolve-projectiles
  "Each projectile hits at most one ball. Balls dropping to 0 hp may drop a power-up."
  [{:keys [balls projectiles pops powerups] :as s}]
  (let [marked (mark-hits balls projectiles)
        hit-counts (frequencies (keep :hit marked))
        damaged (map-indexed (fn [i b] (update b :hp - (get hit-counts i 0))) balls)
        killed (filter #(<= (:hp %) 0) damaged)
        balls* (vec (remove #(<= (:hp %) 0) damaged))
        drops (->> killed
                   (filter (fn [_] (< (rand) cfg/drop-rate)))
                   (mapv #(spawn-powerup (:x %) (:y %))))
        projectiles* (->> marked (remove :hit) (mapv #(dissoc % :hit)))]
    (assoc s
           :balls balls*
           :projectiles projectiles*
           :pops (+ pops (count killed))
           :powerups (into powerups drops))))

;; --- Outcome checks ---------------------------------------------------------

(defn check-hit [{:keys [balls player lives phase] :as s}]
  (if (or (not= phase :playing) (pos? (:invuln player)))
    s
    (let [{:keys [x y]} player
          hit? (some (fn [{bx :x by :y br :radius}]
                       (m/rect-circle-hit? x y cfg/player-w cfg/player-h bx by br))
                     balls)]
      (if hit?
        (let [lives* (dec lives)]
          (-> s
              (assoc :lives lives*)
              (assoc-in [:player :invuln] cfg/invuln-frames)
              (cond-> (zero? lives*) (assoc :phase :game-over))))
        s))))

(defn check-win [{:keys [balls phase] :as s}]
  (if (and (= phase :playing) (empty? balls))
    (assoc s :phase :win)
    s))

;; --- Top-level step ---------------------------------------------------------

(defn step [{:keys [balls player projectiles powerups keys phase cooldown w h] :as s}]
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
    (cond-> s*
      (= phase :playing) (update :score inc)
      true               tick-buffs
      true               resolve-projectiles
      (= phase :playing) collect-powerups
      true               check-hit
      true               check-win)))

;; --- Aim and fire -----------------------------------------------------------

(defn aim-vector
  "Returns [vx vy power 0..1] for an aim drag, or nil if shorter than min-drag."
  [{:keys [sx sy ex ey]}]
  (let [dx (- ex sx) dy (- ey sy)
        len (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (when (>= len cfg/min-drag)
      (let [clamped (min len cfg/max-drag)
            power (/ clamped cfg/max-drag)
            ux (/ dx len) uy (/ dy len)]
        [(* ux cfg/proj-speed power) (* uy cfg/proj-speed power) power]))))

(defn fire [s]
  (if-let [[vx vy _power] (and (:aim s) (aim-vector (:aim s)))]
    (let [{:keys [player cooldown buffs]} s]
      (if (pos? cooldown)
        (assoc s :aim nil)
        (let [px (+ (:x player) (/ cfg/player-w 2))
              py (+ (:y player) (/ cfg/player-h 2))
              vels (if (:multishot buffs)
                     [(m/rotate vx vy (- cfg/multishot-spread))
                      [vx vy]
                      (m/rotate vx vy cfg/multishot-spread)]
                     [[vx vy]])
              shots (mapv (fn [[vx vy]] {:x px :y py :vx vx :vy vy}) vels)]
          (-> s
              (assoc :aim nil :cooldown cfg/fire-cooldown)
              (update :projectiles into shots)))))
    (assoc s :aim nil)))

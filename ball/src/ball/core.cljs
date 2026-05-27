(ns ball.core)

(def ^:const gravity 0.6)
(def ^:const wall-damping 0.92)
(def ^:const max-trail 80)

(def ^:const player-w 32)
(def ^:const player-h 44)
(def ^:const player-speed 7)
(def ^:const player-jump 15)
(def ^:const player-gravity 0.8)
(def ^:const invuln-frames 60)
(def ^:const max-lives 5)

(def ^:const proj-radius 6)
(def ^:const proj-speed 22)
(def ^:const max-drag 200)
(def ^:const min-drag 12)
(def ^:const fire-cooldown 8)

(def ^:const powerup-size 28)
(def ^:const powerup-gravity 0.35)
(def ^:const powerup-ttl 480)
(def ^:const drop-rate 0.35)
(def ^:const multishot-frames 360)
(def ^:const multishot-spread 0.26)

(def palettes
  {:green  {:trail "80, 220, 100"  :stops ["#c8ffc8" "#40c040" "#0a4a0a"]}
   :blue   {:trail "100, 160, 255" :stops ["#cfe0ff" "#4080ff" "#0a2a6a"]}
   :red    {:trail "255, 90, 90"   :stops ["#ffd0d0" "#ff3030" "#5a0a0a"]}
   :grey   {:trail "180, 180, 180" :stops ["#f0f0f0" "#909090" "#202020"]}
   :purple {:trail "200, 110, 255" :stops ["#ecc8ff" "#a040e0" "#3a0a4a"]}})

(def ball-kinds
  {:basic   {:palette :green  :radius 72 :hp 1 :speed 5 :bounce-gain 1.03 :max-speed 13}
   :fast    {:palette :red    :radius 52 :hp 1 :speed 8 :bounce-gain 1.02 :max-speed 16}
   :armored {:palette :grey   :radius 80 :hp 2 :speed 3 :bounce-gain 1.01 :max-speed 9}
   :homing  {:palette :purple :radius 60 :hp 1 :speed 4 :bounce-gain 1.02 :max-speed 11
             :homing 0.10}})

(def powerup-kinds
  {:life      {:color "#ff5a8a" :glyph "♥"}
   :multishot {:color "#7fd0ff" :glyph "✦"}})

(defn ball [kind x y dir]
  (let [{:keys [palette radius hp speed bounce-gain max-speed homing]} (ball-kinds kind)]
    {:kind kind :palette palette
     :radius radius :hp hp :max-hp hp
     :bounce-gain bounce-gain :max-speed max-speed
     :homing (or homing 0)
     :x x :y y :vx (* dir speed) :vy 0 :trail '()}))

(defn fresh-player [w h]
  {:x (/ w 2) :y (- h player-h) :vx 0 :vy 0
   :on-floor true :invuln 0 :facing 1})

(defn starting-balls [w]
  [(ball :basic   200       100  1)
   (ball :fast    (- w 200) 200 -1)
   (ball :armored (/ w 2)    80  1)
   (ball :homing  (/ w 3)   260 -1)])

(defn fresh-state [w h]
  {:balls (starting-balls w)
   :player (fresh-player w h)
   :projectiles []
   :powerups []
   :buffs {}
   :aim nil
   :keys #{}
   :lives 3
   :pops 0
   :score 0
   :cooldown 0
   :phase :playing
   :w w :h h})

(defonce state (atom (fresh-state 800 600)))

(defn resize! [canvas]
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (swap! state assoc :w w :h h)))

(defn homing-accel [{bx :x by :y homing :homing} {px :x py :y}]
  (if (zero? homing)
    [0 0]
    (let [pcx (+ px (/ player-w 2))
          pcy (+ py (/ player-h 2))
          dx (- pcx bx) dy (- pcy by)
          len (Math/sqrt (+ (* dx dx) (* dy dy)))]
      (if (< len 1)
        [0 0]
        [(* (/ dx len) homing) (* (/ dy len) homing)]))))

(defn clamp-speed [vx vy max-speed]
  (let [sp (Math/sqrt (+ (* vx vx) (* vy vy)))]
    (if (> sp max-speed)
      (let [k (/ max-speed sp)] [(* vx k) (* vy k)])
      [vx vy])))

(defn step-ball [w h player {:keys [x y vx vy trail radius bounce-gain max-speed] :as b}]
  (let [[hx hy] (homing-accel b player)
        vx0 (+ vx hx)
        vy0 (+ vy hy gravity)
        x*  (+ x vx0)
        y*  (+ y vy0)
        floor (- h radius)
        ceil  radius
        right (- w radius)
        left  radius
        [x** vxw] (cond
                    (> x* right) [right (* (- vx0) wall-damping)]
                    (< x* left)  [left  (* (- vx0) wall-damping)]
                    :else        [x* vx0])
        [y** vyw] (cond
                    (>= y* floor) [floor (* (- vy0) bounce-gain)]
                    (<= y* ceil)  [ceil  (* (- vy0) wall-damping)]
                    :else         [y* vy0])
        [vx** vy**] (clamp-speed vxw vyw max-speed)
        trail* (take max-trail (conj trail [x** y**]))]
    (assoc b :x x** :y y** :vx vx** :vy vy** :trail trail*)))

(defn step-player [w h keys {:keys [x y vy on-floor invuln facing] :as p}]
  (let [left? (contains? keys :left)
        right? (contains? keys :right)
        jump? (contains? keys :jump)
        ax (cond left? (- player-speed) right? player-speed :else 0)
        facing* (cond left? -1 right? 1 :else facing)
        vy0 (if (and jump? on-floor) (- player-jump) vy)
        vy1 (+ vy0 player-gravity)
        x*  (+ x ax)
        y*  (+ y vy1)
        floor (- h player-h)
        right-wall (- w player-w)
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
  (let [x* (+ x vx) y* (+ y vy)]
    (when (and (>= x* (- proj-radius)) (<= x* (+ w proj-radius))
               (>= y* (- proj-radius)) (<= y* (+ h proj-radius)))
      (assoc p :x x* :y y*))))

(defn step-powerup [h {:keys [x y vy ttl] :as pu}]
  (let [floor (- h powerup-size)
        vy*  (+ vy powerup-gravity)
        y*   (+ y vy*)
        [y** vy**] (if (>= y* floor) [floor 0] [y* vy*])
        ttl* (dec ttl)]
    (when (pos? ttl*)
      (assoc pu :y y** :vy vy** :ttl ttl* :x x))))

(defn rect-circle-hit? [px py pw ph cx cy cr]
  (let [nx (-> cx (max px) (min (+ px pw)))
        ny (-> cy (max py) (min (+ py ph)))
        dx (- cx nx)
        dy (- cy ny)]
    (<= (+ (* dx dx) (* dy dy)) (* cr cr))))

(defn rect-rect-hit? [ax ay aw ah bx by bw bh]
  (and (< ax (+ bx bw)) (> (+ ax aw) bx)
       (< ay (+ by bh)) (> (+ ay ah) by)))

(defn dist2 [ax ay bx by]
  (let [dx (- ax bx) dy (- ay by)]
    (+ (* dx dx) (* dy dy))))

(defn spawn-powerup [x y]
  (let [kind (rand-nth (vec (keys powerup-kinds)))]
    {:kind kind :x (- x (/ powerup-size 2)) :y y :vy 0 :ttl powerup-ttl}))

(defn resolve-projectiles
  "Each projectile hits at most one ball. Balls dropping to 0 hp may drop a power-up."
  [{:keys [balls projectiles pops powerups] :as s}]
  (let [marked (mapv (fn [{:keys [x y] :as p}]
                       (let [idx (some (fn [[i b]]
                                         (when (<= (dist2 x y (:x b) (:y b))
                                                   (let [r (+ (:radius b) proj-radius)]
                                                     (* r r)))
                                           i))
                                       (map-indexed vector balls))]
                         (assoc p :hit idx)))
                     projectiles)
        hit-counts (frequencies (keep :hit marked))
        killed (->> balls
                    (map-indexed
                      (fn [i b]
                        (when (<= (- (:hp b) (get hit-counts i 0)) 0) b)))
                    (keep identity))
        balls* (->> balls
                    (map-indexed (fn [i b] (update b :hp - (get hit-counts i 0))))
                    (remove #(<= (:hp %) 0))
                    vec)
        drops (->> killed
                   (filter (fn [_] (< (rand) drop-rate)))
                   (mapv #(spawn-powerup (:x %) (:y %))))
        projectiles* (->> marked
                          (remove :hit)
                          (mapv #(dissoc % :hit)))]
    (assoc s
           :balls balls*
           :projectiles projectiles*
           :pops (+ pops (count killed))
           :powerups (into powerups drops))))

(defn apply-powerup [s kind]
  (case kind
    :life (update s :lives #(min max-lives (inc %)))
    :multishot (assoc-in s [:buffs :multishot] multishot-frames)))

(defn collect-powerups [{:keys [player powerups] :as s}]
  (let [{px :x py :y} player
        [collected remaining]
        (reduce (fn [[c r] {:keys [x y] :as pu}]
                  (if (rect-rect-hit? px py player-w player-h
                                      x y powerup-size powerup-size)
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

(defn check-hit [{:keys [balls player lives phase] :as s}]
  (if (or (not= phase :playing) (pos? (:invuln player)))
    s
    (let [{:keys [x y]} player
          hit? (some (fn [{bx :x by :y br :radius}]
                       (rect-circle-hit? x y player-w player-h bx by br))
                     balls)]
      (if hit?
        (let [lives* (dec lives)]
          (-> s
              (assoc :lives lives*)
              (assoc-in [:player :invuln] invuln-frames)
              (cond-> (zero? lives*) (assoc :phase :game-over))))
        s))))

(defn check-win [{:keys [balls phase] :as s}]
  (if (and (= phase :playing) (empty? balls))
    (assoc s :phase :win)
    s))

(defn step [{:keys [balls player projectiles powerups keys phase cooldown w h] :as s}]
  (let [balls* (mapv (partial step-ball w h player) balls)
        player* (step-player w h keys player)
        projectiles* (->> projectiles
                          (keep (partial step-projectile w h))
                          vec)
        powerups* (->> powerups
                       (keep (partial step-powerup h))
                       vec)
        s* (assoc s
                  :balls balls*
                  :player player*
                  :projectiles projectiles*
                  :powerups powerups*
                  :cooldown (max 0 (dec cooldown)))]
    (cond-> s*
      (= phase :playing) (update :score inc)
      true tick-buffs
      true resolve-projectiles
      (= phase :playing) collect-powerups
      true check-hit
      true check-win)))

(defn clear! [ctx w h]
  (set! (.-fillStyle ctx) "rgba(17, 17, 17, 0.25)")
  (.fillRect ctx 0 0 w h))

(defn draw-trail! [ctx trail palette radius]
  (let [n (count trail)
        rgb (:trail (palettes palette))]
    (doseq [[i [tx ty]] (map-indexed vector trail)]
      (let [alpha (- 1 (/ i (max 1 n)))
            r (* radius (- 1 (/ i (* 1.5 n))))]
        (when (pos? r)
          (set! (.-fillStyle ctx)
                (str "rgba(" rgb ", " (* 0.55 alpha) ")"))
          (.beginPath ctx)
          (.arc ctx tx ty r 0 (* 2 Math/PI))
          (.fill ctx))))))

(defn draw-ball! [ctx {:keys [x y palette radius hp max-hp]}]
  (let [[c0 c1 c2] (:stops (palettes palette))
        grad (.createRadialGradient ctx
                                    (- x (/ radius 3)) (- y (/ radius 3)) 2
                                    x y radius)]
    (.addColorStop grad 0 c0)
    (.addColorStop grad 0.4 c1)
    (.addColorStop grad 1 c2)
    (set! (.-fillStyle ctx) grad)
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 Math/PI))
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
    (let [cx (+ x (/ player-w 2))
          grad (.createLinearGradient ctx x y x (+ y player-h))]
      (.addColorStop grad 0 "#fff2a8")
      (.addColorStop grad 1 "#d49a00")
      (set! (.-fillStyle ctx) grad)
      (.beginPath ctx)
      (.roundRect ctx x y player-w player-h 8)
      (.fill ctx)
      (set! (.-fillStyle ctx) "#111")
      (let [eye-y (+ y 14)
            off (* 6 facing)]
        (.beginPath ctx)
        (.arc ctx (+ cx off -4) eye-y 2.5 0 (* 2 Math/PI))
        (.arc ctx (+ cx off  6) eye-y 2.5 0 (* 2 Math/PI))
        (.fill ctx)))))

(defn draw-projectiles! [ctx projectiles]
  (set! (.-fillStyle ctx) "#fff")
  (set! (.-shadowColor ctx) "rgba(255,255,255,0.9)")
  (set! (.-shadowBlur ctx) 12)
  (doseq [{:keys [x y]} projectiles]
    (.beginPath ctx)
    (.arc ctx x y proj-radius 0 (* 2 Math/PI))
    (.fill ctx))
  (set! (.-shadowBlur ctx) 0))

(defn draw-powerups! [ctx powerups]
  (doseq [{:keys [kind x y ttl]} powerups]
    (let [{:keys [color glyph]} (powerup-kinds kind)
          ;; pulse during last second
          fading? (< ttl 60)
          alpha (if fading? (/ (mod ttl 20) 20) 1)]
      (set! (.-globalAlpha ctx) (max 0.3 alpha))
      (set! (.-fillStyle ctx) color)
      (set! (.-shadowColor ctx) color)
      (set! (.-shadowBlur ctx) 14)
      (.beginPath ctx)
      (.roundRect ctx x y powerup-size powerup-size 6)
      (.fill ctx)
      (set! (.-shadowBlur ctx) 0)
      (set! (.-fillStyle ctx) "#111")
      (set! (.-font ctx) "bold 18px monospace")
      (set! (.-textAlign ctx) "center")
      (set! (.-textBaseline ctx) "middle")
      (.fillText ctx glyph (+ x (/ powerup-size 2)) (+ y (/ powerup-size 2) 1))
      (set! (.-textAlign ctx) "start")
      (set! (.-textBaseline ctx) "alphabetic")
      (set! (.-globalAlpha ctx) 1))))

(defn aim-vector [{:keys [sx sy ex ey]}]
  (let [dx (- ex sx) dy (- ey sy)
        len (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (when (>= len min-drag)
      (let [clamped (min len max-drag)
            power (/ clamped max-drag)
            ux (/ dx len) uy (/ dy len)]
        [(* ux proj-speed power) (* uy proj-speed power) power]))))

(defn draw-aim! [ctx {:keys [player aim]}]
  (when aim
    (when-let [[vx vy power] (aim-vector aim)]
      (let [px (+ (:x player) (/ player-w 2))
            py (+ (:y player) (/ player-h 2))
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
        (.arc ctx ex ey 4 0 (* 2 Math/PI))
        (.fill ctx)))))

(defn draw-hud! [ctx {:keys [lives pops score phase balls buffs w h]}]
  (set! (.-fillStyle ctx) "#fff")
  (set! (.-font ctx) "16px monospace")
  (.fillText ctx (str "LIVES " (apply str (repeat lives "♥"))) 16 28)
  (.fillText ctx (str "POPS  " pops) 16 50)
  (.fillText ctx (str "TIME  " (.toFixed (/ score 60) 1) "s") 16 72)
  (.fillText ctx (str "LEFT  " (count balls)) 16 94)
  (when-let [ms (:multishot buffs)]
    (set! (.-fillStyle ctx) (:color (powerup-kinds :multishot)))
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

(defn render! [ctx {:keys [balls player projectiles powerups] :as s}]
  (let [{:keys [w h]} s]
    (clear! ctx w h)
    (doseq [{:keys [trail palette radius]} balls]
      (draw-trail! ctx trail palette radius))
    (doseq [b balls]
      (draw-ball! ctx b))
    (draw-powerups! ctx powerups)
    (draw-projectiles! ctx projectiles)
    (draw-player! ctx player)
    (draw-aim! ctx s)
    (draw-hud! ctx s)))

(defn frame [ctx]
  (let [s (swap! state step)]
    (render! ctx s))
  (js/requestAnimationFrame #(frame ctx)))

(def key-map
  {"ArrowLeft"  :left   "KeyA" :left
   "ArrowRight" :right  "KeyD" :right
   "ArrowUp"    :jump   "KeyW" :jump   "Space" :jump})

(defn on-keydown [e]
  (when-let [k (key-map (.-code e))]
    (.preventDefault e)
    (swap! state update :keys conj k)))

(defn on-keyup [e]
  (when-let [k (key-map (.-code e))]
    (swap! state update :keys disj k)))

(defn rotate [vx vy angle]
  (let [c (Math/cos angle) s (Math/sin angle)]
    [(- (* vx c) (* vy s))
     (+ (* vx s) (* vy c))]))

(defn fire! [s]
  (if-let [[vx vy _power] (and (:aim s) (aim-vector (:aim s)))]
    (let [{:keys [player cooldown buffs]} s]
      (if (pos? cooldown)
        (assoc s :aim nil)
        (let [px (+ (:x player) (/ player-w 2))
              py (+ (:y player) (/ player-h 2))
              vels (if (:multishot buffs)
                     [(rotate vx vy (- multishot-spread))
                      [vx vy]
                      (rotate vx vy multishot-spread)]
                     [[vx vy]])
              shots (mapv (fn [[vx vy]] {:x px :y py :vx vx :vy vy}) vels)]
          (-> s
              (assoc :aim nil :cooldown fire-cooldown)
              (update :projectiles into shots)))))
    (assoc s :aim nil)))

(defn on-mousedown [e]
  (let [x (.-clientX e) y (.-clientY e)]
    (swap! state
           (fn [{:keys [phase w h] :as s}]
             (case phase
               (:game-over :win) (fresh-state w h)
               :playing (assoc s :aim {:sx x :sy y :ex x :ey y}))))))

(defn on-mousemove [e]
  (when (:aim @state)
    (swap! state update :aim assoc
           :ex (.-clientX e)
           :ey (.-clientY e))))

(defn on-mouseup [_e]
  (when (:aim @state)
    (swap! state fire!)))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx (.getContext canvas "2d")]
    (resize! canvas)
    (swap! state (fn [{:keys [w h]}] (fresh-state w h)))
    (.addEventListener js/window "resize" #(resize! canvas))
    (.addEventListener js/window "keydown" on-keydown)
    (.addEventListener js/window "keyup" on-keyup)
    (.addEventListener canvas "mousedown" on-mousedown)
    (.addEventListener js/window "mousemove" on-mousemove)
    (.addEventListener js/window "mouseup" on-mouseup)
    (js/requestAnimationFrame #(frame ctx))))

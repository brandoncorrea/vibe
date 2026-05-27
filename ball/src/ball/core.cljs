(ns ball.core)

(def ^:const radius 72)
(def ^:const gravity 0.6)
(def ^:const energy-boost 1.08)
(def ^:const wall-damping 0.92)
(def ^:const max-trail 80)

(def ^:const player-w 32)
(def ^:const player-h 44)
(def ^:const player-speed 7)
(def ^:const player-jump 15)
(def ^:const player-gravity 0.8)
(def ^:const invuln-frames 60)

(def ^:const proj-radius 6)
(def ^:const proj-speed 22)
(def ^:const max-drag 200)
(def ^:const min-drag 12)
(def ^:const fire-cooldown 8)

(def palettes
  {:green {:trail "80, 220, 100"
           :stops ["#c8ffc8" "#40c040" "#0a4a0a"]}
   :blue  {:trail "100, 160, 255"
           :stops ["#cfe0ff" "#4080ff" "#0a2a6a"]}})

(defn ball [x y vx vy palette]
  {:x x :y y :vx vx :vy vy :trail '() :palette palette})

(defn fresh-player [w h]
  {:x (/ w 2) :y (- h player-h) :vx 0 :vy 0
   :on-floor true :invuln 0 :facing 1})

(defn fresh-state [w h]
  {:balls [(ball 200 100  5 0 :green)
           (ball (- w 200) 200 -4 0 :blue)]
   :player (fresh-player w h)
   :projectiles []
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

(defn step-ball [w h {:keys [x y vx vy trail] :as b}]
  (let [vy* (+ vy gravity)
        x*  (+ x vx)
        y*  (+ y vy*)
        floor (- h radius)
        ceil  radius
        right (- w radius)
        left  radius
        [x** vx**] (cond
                     (> x* right) [right (* (- vx) wall-damping)]
                     (< x* left)  [left  (* (- vx) wall-damping)]
                     :else        [x* vx])
        [y** vy**] (cond
                     (>= y* floor) [floor (* (- vy*) energy-boost)]
                     (<= y* ceil)  [ceil  (* (- vy*) wall-damping)]
                     :else         [y* vy*])
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

(defn rect-circle-hit? [px py pw ph cx cy cr]
  (let [nx (-> cx (max px) (min (+ px pw)))
        ny (-> cy (max py) (min (+ py ph)))
        dx (- cx nx)
        dy (- cy ny)]
    (<= (+ (* dx dx) (* dy dy)) (* cr cr))))

(defn dist2 [ax ay bx by]
  (let [dx (- ax bx) dy (- ay by)]
    (+ (* dx dx) (* dy dy))))

(defn resolve-projectiles [{:keys [balls projectiles pops] :as s}]
  (let [hit-r2 (Math/pow (+ radius proj-radius) 2)
        ;; mark each projectile with the index of the first ball it hits (or nil)
        marked (mapv (fn [{:keys [x y] :as p}]
                       (let [idx (some (fn [[i b]]
                                         (when (<= (dist2 x y (:x b) (:y b)) hit-r2) i))
                                       (map-indexed vector balls))]
                         (assoc p :hit idx)))
                     projectiles)
        hit-set (into #{} (keep :hit marked))
        balls* (->> balls
                    (map-indexed vector)
                    (remove (comp hit-set first))
                    (mapv second))
        projectiles* (->> marked
                          (remove :hit)
                          (mapv #(dissoc % :hit)))]
    (assoc s
           :balls balls*
           :projectiles projectiles*
           :pops (+ pops (count hit-set)))))

(defn check-hit [{:keys [balls player lives phase] :as s}]
  (if (or (not= phase :playing) (pos? (:invuln player)))
    s
    (let [{:keys [x y]} player
          hit? (some (fn [{bx :x by :y}]
                       (rect-circle-hit? x y player-w player-h bx by radius))
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

(defn step [{:keys [balls player projectiles keys phase cooldown w h] :as s}]
  (let [balls* (mapv (partial step-ball w h) balls)
        player* (step-player w h keys player)
        projectiles* (->> projectiles
                          (keep (partial step-projectile w h))
                          vec)
        s* (assoc s
                  :balls balls*
                  :player player*
                  :projectiles projectiles*
                  :cooldown (max 0 (dec cooldown)))]
    (cond-> s*
      (= phase :playing) (update :score inc)
      true resolve-projectiles
      true check-hit
      true check-win)))

(defn clear! [ctx w h]
  (set! (.-fillStyle ctx) "rgba(17, 17, 17, 0.25)")
  (.fillRect ctx 0 0 w h))

(defn draw-trail! [ctx trail palette]
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

(defn draw-ball! [ctx x y palette]
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
    (.fill ctx)))

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

(defn aim-vector
  "Returns [vx vy power 0..1] for an aim drag, or nil if too short."
  [{:keys [sx sy ex ey]}]
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

(defn draw-hud! [ctx {:keys [lives pops score phase w h]}]
  (set! (.-fillStyle ctx) "#fff")
  (set! (.-font ctx) "16px monospace")
  (.fillText ctx (str "LIVES " (apply str (repeat lives "♥"))) 16 28)
  (.fillText ctx (str "POPS  " pops) 16 50)
  (.fillText ctx (str "TIME  " (.toFixed (/ score 60) 1) "s") 16 72)
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

(defn render! [ctx {:keys [balls player projectiles] :as s}]
  (let [{:keys [w h]} s]
    (clear! ctx w h)
    (doseq [{:keys [trail palette]} balls]
      (draw-trail! ctx trail palette))
    (doseq [{:keys [x y palette]} balls]
      (draw-ball! ctx x y palette))
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

(defn fire! [s]
  (if-let [[vx vy _power] (and (:aim s) (aim-vector (:aim s)))]
    (let [{:keys [player cooldown projectiles]} s]
      (if (pos? cooldown)
        (assoc s :aim nil)
        (let [px (+ (:x player) (/ player-w 2))
              py (+ (:y player) (/ player-h 2))]
          (-> s
              (assoc :aim nil
                     :cooldown fire-cooldown)
              (update :projectiles conj
                      {:x px :y py :vx vx :vy vy})))))
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

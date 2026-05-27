(ns ball.core)

(def ^:const radius 72)
(def ^:const gravity 0.6)
(def ^:const energy-boost 1.08)
(def ^:const wall-damping 0.92)
(def ^:const max-trail 80)

(def palettes
  {:green {:trail "80, 220, 100"
           :stops ["#c8ffc8" "#40c040" "#0a4a0a"]}
   :blue  {:trail "100, 160, 255"
           :stops ["#cfe0ff" "#4080ff" "#0a2a6a"]}})

(defn ball [x y vx vy palette]
  {:x x :y y :vx vx :vy vy :trail '() :palette palette})

(defonce state
  (atom {:balls [(ball 200 100  5 0 :green)
                 (ball 600 200 -4 0 :blue)]
         :w 800 :h 600}))

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

(defn step [{:keys [balls w h] :as s}]
  (assoc s :balls (mapv (partial step-ball w h) balls)))

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

(defn render! [ctx {:keys [balls w h]}]
  (clear! ctx w h)
  (doseq [{:keys [trail palette]} balls]
    (draw-trail! ctx trail palette))
  (doseq [{:keys [x y palette]} balls]
    (draw-ball! ctx x y palette)))

(defn frame [ctx]
  (let [s (swap! state step)]
    (render! ctx s))
  (js/requestAnimationFrame #(frame ctx)))

(defn nudge-nearest [balls cx cy]
  (let [idx (->> balls
                 (map-indexed (fn [i {:keys [x y]}]
                                [i (+ (Math/pow (- x cx) 2)
                                      (Math/pow (- y cy) 2))]))
                 (apply min-key second)
                 first)]
    (update balls idx assoc
            :x cx :y cy
            :vx (- (rand 12) 6)
            :vy 0)))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx (.getContext canvas "2d")]
    (resize! canvas)
    (.addEventListener js/window "resize" #(resize! canvas))
    (.addEventListener canvas "click"
                       (fn [e]
                         (swap! state update :balls
                                nudge-nearest
                                (.-clientX e) (.-clientY e))))
    (js/requestAnimationFrame #(frame ctx))))

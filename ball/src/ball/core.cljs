(ns ball.core)

(def ^:const radius 72)
(def ^:const gravity 0.6)
(def ^:const energy-boost 1.08)
(def ^:const wall-damping 0.92)
(def ^:const max-trail 80)

(defonce state
  (atom {:x 200 :y 100
         :vx 5 :vy 0
         :trail '()
         :w 800 :h 600}))

(defn resize! [canvas]
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (swap! state assoc :w w :h h)))

(defn step [{:keys [x y vx vy trail w h] :as s}]
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
    (assoc s :x x** :y y** :vx vx** :vy vy** :trail trail*)))

(defn clear! [ctx w h]
  (set! (.-fillStyle ctx) "rgba(17, 17, 17, 0.25)")
  (.fillRect ctx 0 0 w h))

(defn draw-trail! [ctx trail]
  (let [n (count trail)]
    (doseq [[i [tx ty]] (map-indexed vector trail)]
      (let [alpha (- 1 (/ i (max 1 n)))
            r (* radius (- 1 (/ i (* 1.5 n))))]
        (when (pos? r)
          (set! (.-fillStyle ctx)
                (str "rgba(80, 220, 100, " (* 0.55 alpha) ")"))
          (.beginPath ctx)
          (.arc ctx tx ty r 0 (* 2 Math/PI))
          (.fill ctx))))))

(defn draw-ball! [ctx x y]
  (let [grad (.createRadialGradient ctx
                                    (- x (/ radius 3)) (- y (/ radius 3)) 2
                                    x y radius)]
    (.addColorStop grad 0 "#c8ffc8")
    (.addColorStop grad 0.4 "#40c040")
    (.addColorStop grad 1 "#0a4a0a")
    (set! (.-fillStyle ctx) grad)
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 Math/PI))
    (.fill ctx)))

(defn render! [ctx {:keys [x y trail w h]}]
  (clear! ctx w h)
  (draw-trail! ctx trail)
  (draw-ball! ctx x y))

(defn frame [ctx]
  (let [s (swap! state step)]
    (render! ctx s))
  (js/requestAnimationFrame #(frame ctx)))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx (.getContext canvas "2d")]
    (resize! canvas)
    (.addEventListener js/window "resize" #(resize! canvas))
    (.addEventListener canvas "click"
                       (fn [e]
                         (swap! state assoc
                                :x (.-clientX e)
                                :y (.-clientY e)
                                :vx (- (rand 12) 6)
                                :vy 0)))
    (js/requestAnimationFrame #(frame ctx))))

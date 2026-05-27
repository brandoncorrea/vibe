(ns landing.core)

;; -----------------------------------------------------------------------------
;; Exercise registry.
;;
;; :href is a relative path. The build-all.sh script (see README) places each
;; exercise's release build under landing/public/<name>/ so these links work
;; from the same origin.
;; -----------------------------------------------------------------------------

(def exercises
  [{:id      :ball
    :title   "Bouncing Ball"
    :blurb   "Gravity, energy-adding floor, fading trails, and a slingshot
              launcher that grew into a tiny dodge-and-pop game."
    :tags    ["physics" "canvas" "game"]
    :accent  "#ff6b6b"
    :href    "./ball/"
    :preview :ball}

   {:id      :cube
    :title   "Spinning Cube"
    :blurb   "A wireframe cube rendered to plain 2D canvas — rotation,
              projection, and Lambert shading from scratch (no 3D lib)."
    :tags    ["3d" "canvas" "math"]
    :accent  "#6ad6ff"
    :href    "./cube/"
    :preview :cube}

   {:id      :chart
    :title   "Photographs Chart"
    :blurb   "A Reagent + SVG bar chart of estimated photographs taken
              worldwide per 5-year period (1976–2025)."
    :tags    ["reagent" "svg" "data"]
    :accent  "#a78bfa"
    :href    "./chart/"
    :preview :chart}])

;; -----------------------------------------------------------------------------
;; DOM helpers
;; -----------------------------------------------------------------------------

(defn- el
  ([tag] (.createElement js/document (name tag)))
  ([tag attrs]
   (let [e (el tag)]
     (doseq [[k v] attrs]
       (case k
         :class    (set! (.-className e) v)
         :text     (set! (.-textContent e) v)
         :html     (set! (.-innerHTML e) v)
         :href     (.setAttribute e "href" v)
         :style    (.setAttribute e "style" v)
         (.setAttribute e (name k) (str v))))
     e)))

(defn- append! [parent & children]
  (doseq [c children] (.appendChild parent c)))

;; -----------------------------------------------------------------------------
;; Preview animations — each returns a tick fn (dt-seconds -> nil).
;; -----------------------------------------------------------------------------

(defn- fit-canvas! [^js canvas]
  (let [dpr (or js/window.devicePixelRatio 1)
        w   (.-clientWidth canvas)
        h   (.-clientHeight canvas)]
    (set! (.-width canvas)  (Math/floor (* w dpr)))
    (set! (.-height canvas) (Math/floor (* h dpr)))
    (let [ctx (.getContext canvas "2d")]
      (.setTransform ctx dpr 0 0 dpr 0 0)
      ctx)))

(defn- ball-preview [^js canvas accent]
  (let [state (atom {:x 30 :y 20 :vx 70 :vy 0 :trail '()})]
    (fn [dt]
      (let [ctx (.getContext canvas "2d")
            w   (.-clientWidth canvas)
            h   (.-clientHeight canvas)
            r   8
            g   320]
        (swap! state
               (fn [{:keys [x y vx vy trail]}]
                 (let [vy' (+ vy (* g dt))
                       x'  (+ x (* vx dt))
                       y'  (+ y (* vy' dt))
                       [x' vx'] (cond
                                  (< x' r)       [r (* -0.85 vx)]
                                  (> x' (- w r)) [(- w r) (* -0.85 vx)]
                                  :else          [x' vx])
                       [y' vy'] (if (> y' (- h r))
                                  [(- h r) (* -1.04 vy')]
                                  [y' vy'])
                       trail' (take 14 (conj trail [x' y']))]
                   {:x x' :y y' :vx vx' :vy vy' :trail trail'})))
        (.clearRect ctx 0 0 w h)
        (let [{:keys [x y trail]} @state]
          (doseq [[i [tx ty]] (map-indexed vector trail)]
            (let [a (- 1 (/ i 14))]
              (set! (.-globalAlpha ctx) (* 0.4 a))
              (set! (.-fillStyle ctx) accent)
              (.beginPath ctx)
              (.arc ctx tx ty (* r (- 1 (* 0.05 i))) 0 (* 2 Math/PI))
              (.fill ctx)))
          (set! (.-globalAlpha ctx) 1)
          (set! (.-fillStyle ctx) accent)
          (.beginPath ctx)
          (.arc ctx x y r 0 (* 2 Math/PI))
          (.fill ctx))))))

(def ^:private cube-verts
  [[-1 -1 -1] [ 1 -1 -1] [ 1  1 -1] [-1  1 -1]
   [-1 -1  1] [ 1 -1  1] [ 1  1  1] [-1  1  1]])

(def ^:private cube-edges
  [[0 1] [1 2] [2 3] [3 0]
   [4 5] [5 6] [6 7] [7 4]
   [0 4] [1 5] [2 6] [3 7]])

(defn- rotate [[x y z] ax ay]
  (let [cx (Math/cos ax) sx (Math/sin ax)
        cy (Math/cos ay) sy (Math/sin ay)
        ;; X rotation
        y1 (- (* y cx) (* z sx))
        z1 (+ (* y sx) (* z cx))
        ;; Y rotation
        x2 (+ (* x cy) (* z1 sy))
        z2 (+ (- (* x sy)) (* z1 cy))]
    [x2 y1 z2]))

(defn- cube-preview [^js canvas accent]
  (let [t (atom 0)]
    (fn [dt]
      (swap! t + dt)
      (let [ctx (.getContext canvas "2d")
            w   (.-clientWidth canvas)
            h   (.-clientHeight canvas)
            cx  (/ w 2)
            cy  (/ h 2)
            s   (* 0.22 (min w h))
            d   4
            ax  (* 0.7 @t)
            ay  (* 1.0 @t)
            pts (mapv (fn [v]
                        (let [[x y z] (rotate v ax ay)
                              k (/ d (+ d z))]
                          [(+ cx (* x s k)) (+ cy (* y s k))]))
                      cube-verts)]
        (.clearRect ctx 0 0 w h)
        (set! (.-strokeStyle ctx) accent)
        (set! (.-lineWidth ctx) 1.6)
        (set! (.-lineCap ctx) "round")
        (doseq [[a b] cube-edges]
          (let [[ax ay] (pts a) [bx by] (pts b)]
            (.beginPath ctx)
            (.moveTo ctx ax ay)
            (.lineTo ctx bx by)
            (.stroke ctx)))))))

(def ^:private chart-values
  ;; Real chart data — billions of photographs per 5-year period, 1976–2025.
  [55 78 100 135 250 750 1500 3500 6500 8500])

(def ^:private chart-max 8500)

(defn- chart-preview [^js canvas accent]
  (let [t          (atom 0)
        cycle      4.0
        bar-delay  0.08
        bar-grow   0.5]
    (fn [dt]
      (swap! t #(mod (+ % dt) cycle))
      (let [ctx      (.getContext canvas "2d")
            w        (.-clientWidth canvas)
            h        (.-clientHeight canvas)
            pad-x    10
            pad-y    10
            gap      3
            n        (count chart-values)
            bw       (/ (- w (* 2 pad-x) (* gap (dec n))) n)
            usable-h (- h (* 2 pad-y))]
        (.clearRect ctx 0 0 w h)
        (set! (.-fillStyle ctx) accent)
        (doseq [[i v] (map-indexed vector chart-values)]
          (let [norm   (/ v chart-max)
                local  (max 0 (- @t (* i bar-delay)))
                prog   (min 1 (/ local bar-grow))
                eased  (- 1 (Math/pow (- 1 prog) 3))
                bh     (max 1.5 (* norm usable-h eased))
                bx     (+ pad-x (* i (+ bw gap)))
                by     (+ pad-y (- usable-h bh))]
            (.fillRect ctx bx by bw bh)))))))

(defn- make-preview [kind canvas accent]
  (case kind
    :ball  (ball-preview canvas accent)
    :cube  (cube-preview canvas accent)
    :chart (chart-preview canvas accent)))

;; -----------------------------------------------------------------------------
;; Card construction
;; -----------------------------------------------------------------------------

(defn- card-styles [accent]
  (str "display:flex; flex-direction:column; gap:14px;"
       "padding:18px; border-radius:14px;"
       "background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.015));"
       "border: 1px solid rgba(255,255,255,0.08);"
       "color:inherit; text-decoration:none;"
       "transition: transform .15s ease, border-color .15s ease, box-shadow .15s ease;"
       "box-shadow: 0 1px 0 rgba(255,255,255,0.03) inset;"
       "--accent:" accent ";"))

(defn- canvas-styles [accent]
  (str "width:100%; height:120px; border-radius:10px;"
       "background: radial-gradient(circle at 30% 30%, "
       "rgba(255,255,255,0.05), rgba(0,0,0,0.35));"
       "border: 1px solid rgba(255,255,255,0.06);"
       "box-shadow: 0 0 24px -8px " accent ";"))

(defn- tag-pill [text]
  (el :span {:text  text
             :style (str "display:inline-block; padding:3px 8px; margin-right:6px;"
                         "border-radius:999px; font-size:11px; letter-spacing:.06em;"
                         "color:#cbd2df; background:rgba(255,255,255,0.05);"
                         "border:1px solid rgba(255,255,255,0.06);")}))

(defn- build-card [{:keys [title blurb tags accent href preview]}]
  (let [a       (el :a {:href href :class "card" :style (card-styles accent)})
        canvas  (el :canvas {:style (canvas-styles accent)})
        h       (el :h2 {:text  title
                         :style "margin:0; font-size:20px; letter-spacing:-0.01em;"})
        p       (el :p {:text  blurb
                        :style "margin:0; color:#aab2c2; font-size:14px; line-height:1.5;"})
        tag-row (el :div {:style "margin-top:auto;"})
        cta     (el :div {:html (str "<span style=\"color:" accent
                                     "; font-weight:600;\">Open →</span>")
                         :style "font-size:13px; margin-top:4px;"})]
    (doseq [t tags] (append! tag-row (tag-pill t)))
    (append! a canvas h p tag-row cta)
    (.addEventListener a "mouseenter"
                       (fn [_]
                         (set! (.-style.transform a) "translateY(-2px)")
                         (set! (.-style.borderColor a) accent)))
    (.addEventListener a "mouseleave"
                       (fn [_]
                         (set! (.-style.transform a) "")
                         (set! (.-style.borderColor a) "rgba(255,255,255,0.08)")))
    {:el a :canvas canvas :preview preview :accent accent}))

;; -----------------------------------------------------------------------------
;; Animation loop
;; -----------------------------------------------------------------------------

(defonce ^:private state (atom {:ticks []}))

(defn- run-loop! []
  (let [last (atom (.now js/performance))]
    (letfn [(frame [now]
              (let [dt (min 0.05 (/ (- now @last) 1000))]
                (reset! last now)
                (doseq [tick (:ticks @state)] (tick dt))
                (js/requestAnimationFrame frame)))]
      (js/requestAnimationFrame frame))))

(defn- mount-card! [container card]
  (append! container (:el card))
  (fit-canvas! (:canvas card))
  (let [tick (make-preview (:preview card) (:canvas card) (:accent card))]
    (swap! state update :ticks conj tick)))

(defn- handle-resize! [cards]
  (.addEventListener js/window "resize"
                     (fn [_]
                       (doseq [c cards] (fit-canvas! (:canvas c))))))

;; -----------------------------------------------------------------------------
;; Entry point
;; -----------------------------------------------------------------------------

(defn ^:export init []
  (let [container (.getElementById js/document "cards")
        cards     (mapv build-card exercises)]
    (doseq [c cards] (mount-card! container c))
    (handle-resize! cards)
    (run-loop!)))

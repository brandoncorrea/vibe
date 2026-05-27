(ns cube.core)

;; --- geometry ---------------------------------------------------------------

(def ^:const cube-vertices
  [[-1 -1 -1] [ 1 -1 -1] [ 1  1 -1] [-1  1 -1]
   [-1 -1  1] [ 1 -1  1] [ 1  1  1] [-1  1  1]])

;; Each face: vertex indices in CCW order when looking at the face from outside,
;; plus the outward normal in cube-local coords.
(def ^:const cube-faces
  [{:idx [4 5 6 7] :normal [ 0  0  1]}   ; front
   {:idx [1 0 3 2] :normal [ 0  0 -1]}   ; back
   {:idx [1 2 6 5] :normal [ 1  0  0]}   ; right
   {:idx [0 4 7 3] :normal [-1  0  0]}   ; left
   {:idx [3 7 6 2] :normal [ 0  1  0]}   ; top
   {:idx [0 1 5 4] :normal [ 0 -1  0]}]) ; bottom

;; --- camera & lighting ------------------------------------------------------

(def ^:const focal 520)

(def ^:const drag-sens 0.5)    ; degrees per pixel
(def ^:const zoom-sens 0.005)  ; cam units per wheel-delta unit
(def ^:const min-cam 2.5)
(def ^:const max-cam 20)

(def ^:const light-color [1.00 0.55 0.22])  ; warm orange
(def ^:const ambient     [0.08 0.10 0.18])  ; cool dim base
(def ^:const base-face   [0.92 0.92 0.94])  ; near-white so the light reads pure

;; --- vec helpers ------------------------------------------------------------

(defn v- [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn v* [[x y z] s] [(* x s) (* y s) (* z s)])
(defn vdot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn vlen [v] (Math/sqrt (vdot v v)))
(defn vnorm [v]
  (let [l (vlen v)]
    (if (zero? l) v (v* v (/ 1 l)))))

(defn rot-x [a [x y z]]
  (let [c (Math/cos a) s (Math/sin a)]
    [x (- (* y c) (* z s)) (+ (* y s) (* z c))]))

(defn rot-y [a [x y z]]
  (let [c (Math/cos a) s (Math/sin a)]
    [(+ (* x c) (* z s)) y (- (* z c) (* x s))]))

(defn rot-z [a [x y z]]
  (let [c (Math/cos a) s (Math/sin a)]
    [(- (* x c) (* y s)) (+ (* x s) (* y c)) z]))

(defn rotate [rx ry rz v]
  (->> v (rot-x rx) (rot-y ry) (rot-z rz)))

;; --- projection -------------------------------------------------------------

(defn project
  "World point -> [screen-x screen-y depth-from-camera]. Camera sits at
   [0 0 cam] looking toward -Z, so smaller depth = closer."
  [w h cam [x y z]]
  (let [depth (max 0.01 (- cam z))
        s (/ focal depth)]
    [(+ (/ w 2) (* x s))
     (- (/ h 2) (* y s))   ; flip Y for canvas
     depth]))

;; --- shading ----------------------------------------------------------------

(defn shade
  "Lambert shading from a single colored point light + ambient."
  [light-pos normal face-center]
  (let [ldir (vnorm (v- light-pos face-center))
        diff (max 0 (vdot normal ldir))
        [lr lg lb] light-color
        [ar ag ab] ambient
        [br bg bb] base-face]
    [(min 1 (+ ar (* br lr diff)))
     (min 1 (+ ag (* bg lg diff)))
     (min 1 (+ ab (* bb lb diff)))]))

(defn rgb->css [[r g b]]
  (str "rgb(" (Math/round (* 255 r)) ","
              (Math/round (* 255 g)) ","
              (Math/round (* 255 b)) ")"))

;; --- state ------------------------------------------------------------------

(defonce state
  (atom {:rx 25 :ry 35 :rz 0
         :lx 2.2 :ly 2.4 :lz 3.0
         :cam 6
         :drag nil
         :w 800 :h 600}))

(defn resize! [canvas]
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (swap! state assoc :w w :h h)))

;; --- rendering --------------------------------------------------------------

(defn face-render-data [rx ry rz {:keys [idx normal]}]
  (let [verts (mapv (fn [i] (rotate rx ry rz (nth cube-vertices i))) idx)
        n     (rotate rx ry rz normal)
        cx (/ (reduce + (map first verts)) 4)
        cy (/ (reduce + (map second verts)) 4)
        cz (/ (reduce + (map #(nth % 2) verts)) 4)]
    {:verts verts :normal n :center [cx cy cz]}))

(defn draw-light-glow! [ctx w h cam light-pos]
  ;; Skip the glow when the light is at/behind the camera (depth ≤ 0); the
  ;; projection would blow up and paint the whole canvas.
  (let [[lx ly depth] (project w h cam light-pos)
        in-front? (> depth 0.2)
        radius (when in-front? (max 60 (/ 1100 depth)))
        [r g b] light-color]
    (when in-front?
      (let [grad (.createRadialGradient ctx lx ly 0 lx ly radius)]
        (.addColorStop grad 0 (str "rgba(" (Math/round (* 255 r)) ","
                                          (Math/round (* 255 g)) ","
                                          (Math/round (* 255 b)) ",0.55)"))
        (.addColorStop grad 1 (str "rgba(" (Math/round (* 255 r)) ","
                                          (Math/round (* 255 g)) ","
                                          (Math/round (* 255 b)) ",0)"))
        (set! (.-fillStyle ctx) grad)
        (.fillRect ctx 0 0 w h)
        (set! (.-fillStyle ctx) "#fff2d6")
        (.beginPath ctx)
        (.arc ctx lx ly (max 3 (/ 22 depth)) 0 (* 2 Math/PI))
        (.fill ctx)))))

(defn draw-face! [ctx w h cam light-pos {:keys [verts] :as f}]
  (let [color (shade light-pos (:normal f) (:center f))
        pts (mapv #(project w h cam %) verts)]
    (set! (.-fillStyle ctx) (rgb->css color))
    (set! (.-strokeStyle ctx) "rgba(0,0,0,0.45)")
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (let [[x0 y0] (first pts)]
      (.moveTo ctx x0 y0))
    (doseq [[x y] (rest pts)]
      (.lineTo ctx x y))
    (.closePath ctx)
    (.fill ctx)
    (.stroke ctx)))

(defn render! [ctx {:keys [rx ry rz lx ly lz cam w h]}]
  (let [light-pos [lx ly lz]]
    (set! (.-fillStyle ctx) "#0a0d12")
    (.fillRect ctx 0 0 w h)
    (draw-light-glow! ctx w h cam light-pos)
    (let [deg->rad (/ Math/PI 180)
          rxr (* rx deg->rad)
          ryr (* ry deg->rad)
          rzr (* rz deg->rad)
          faces (->> cube-faces
                     (map #(face-render-data rxr ryr rzr %))
                     ;; back-face cull: only normals pointing toward camera (+Z)
                     (filter #(pos? (nth (:normal %) 2)))
                     ;; painter's: far center-z first
                     (sort-by #(nth (:center %) 2)))]
      (doseq [f faces]
        (draw-face! ctx w h cam light-pos f)))))

(defn frame [ctx]
  (render! ctx @state)
  (js/requestAnimationFrame #(frame ctx)))

;; --- UI wiring --------------------------------------------------------------

(defn bind-slider! [id k fmt]
  (let [el  (.getElementById js/document id)
        out (.getElementById js/document (str id "-out"))]
    (when el
      (.addEventListener
       el "input"
       (fn [e]
         (let [v (js/parseFloat (.. e -target -value))]
           (set! (.-textContent out) (fmt v))
           (swap! state assoc k v)))))))

(defn fmt-deg [v] (str v "°"))
(defn fmt-pos [v] (.toFixed v 1))

(defn sync-slider!
  "Push a state-driven change back into the slider DOM (drag updates rotation
   without going through the slider's input event, so we mirror it here)."
  [id v fmt]
  (when-let [el (.getElementById js/document id)]
    (set! (.-value el) v))
  (when-let [out (.getElementById js/document (str id "-out"))]
    (set! (.-textContent out) (fmt v))))

(defn on-mousedown [e]
  (.preventDefault e)
  (let [s @state]
    (swap! state assoc :drag {:sx (.-clientX e) :sy (.-clientY e)
                              :rx0 (:rx s) :ry0 (:ry s)})))

(defn on-mousemove [e]
  (when-let [d (:drag @state)]
    (let [dx (- (.-clientX e) (:sx d))
          dy (- (.-clientY e) (:sy d))
          rx* (Math/round (mod (+ (:rx0 d) (* dy drag-sens)) 360))
          ry* (Math/round (mod (+ (:ry0 d) (* dx drag-sens)) 360))]
      (swap! state assoc :rx rx* :ry ry*)
      (sync-slider! "rotX" rx* fmt-deg)
      (sync-slider! "rotY" ry* fmt-deg))))

(defn on-mouseup [_e]
  (when (:drag @state)
    (swap! state assoc :drag nil)))

(defn on-wheel [e]
  (.preventDefault e)
  (let [dy (.-deltaY e)
        cam* (-> (+ (:cam @state) (* dy zoom-sens))
                 (max min-cam)
                 (min max-cam))]
    (swap! state assoc :cam cam*)))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx (.getContext canvas "2d")]
    (resize! canvas)
    (.addEventListener js/window "resize" #(resize! canvas))
    (bind-slider! "rotX"   :rx fmt-deg)
    (bind-slider! "rotY"   :ry fmt-deg)
    (bind-slider! "rotZ"   :rz fmt-deg)
    (bind-slider! "lightX" :lx fmt-pos)
    (bind-slider! "lightY" :ly fmt-pos)
    (bind-slider! "lightZ" :lz fmt-pos)
    (.addEventListener canvas "mousedown" on-mousedown)
    (.addEventListener js/window "mousemove" on-mousemove)
    (.addEventListener js/window "mouseup"   on-mouseup)
    (.addEventListener canvas "wheel" on-wheel #js {:passive false})
    (js/requestAnimationFrame #(frame ctx))))

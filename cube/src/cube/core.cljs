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

;; --- 3x3 rotation matrices --------------------------------------------------
;; A matrix is [[r0c0 r0c1 r0c2] [r1c0 r1c1 r1c2] [r2c0 r2c1 r2c2]].
;; This lets drag compose world-axis rotations directly onto an accumulated
;; matrix instead of poking Euler angles, which would gimbal-lock once two
;; axes are non-zero.

(def ^:const identity-mat [[1 0 0] [0 1 0] [0 0 1]])

(defn mat-rot-x [a]
  (let [c (Math/cos a) s (Math/sin a)]
    [[1 0       0]
     [0 c       (- s)]
     [0 s       c]]))

(defn mat-rot-y [a]
  (let [c (Math/cos a) s (Math/sin a)]
    [[c        0 s]
     [0        1 0]
     [(- s)    0 c]]))

(defn mat-rot-z [a]
  (let [c (Math/cos a) s (Math/sin a)]
    [[c (- s) 0]
     [s c     0]
     [0 0     1]]))

(defn mat-mul [a b]
  (let [[[a00 a01 a02] [a10 a11 a12] [a20 a21 a22]] a
        [[b00 b01 b02] [b10 b11 b12] [b20 b21 b22]] b]
    [[(+ (* a00 b00) (* a01 b10) (* a02 b20))
      (+ (* a00 b01) (* a01 b11) (* a02 b21))
      (+ (* a00 b02) (* a01 b12) (* a02 b22))]
     [(+ (* a10 b00) (* a11 b10) (* a12 b20))
      (+ (* a10 b01) (* a11 b11) (* a12 b21))
      (+ (* a10 b02) (* a11 b12) (* a12 b22))]
     [(+ (* a20 b00) (* a21 b10) (* a22 b20))
      (+ (* a20 b01) (* a21 b11) (* a22 b21))
      (+ (* a20 b02) (* a21 b12) (* a22 b22))]]))

(defn mat-apply
  [[[m00 m01 m02] [m10 m11 m12] [m20 m21 m22]] [x y z]]
  [(+ (* m00 x) (* m01 y) (* m02 z))
   (+ (* m10 x) (* m11 y) (* m12 z))
   (+ (* m20 x) (* m21 y) (* m22 z))])

(def ^:const deg->rad (/ Math/PI 180))
(def ^:const rad->deg (/ 180 Math/PI))

(defn euler->mat
  "Slider Euler triple -> rotation matrix, in the same convention the older
   render used: M = Rz(rz) * Ry(ry) * Rx(rx)."
  [rx-deg ry-deg rz-deg]
  (mat-mul (mat-rot-z (* rz-deg deg->rad))
           (mat-mul (mat-rot-y (* ry-deg deg->rad))
                    (mat-rot-x (* rx-deg deg->rad)))))

(defn mat->euler
  "Inverse of euler->mat: pull a Z-Y-X Euler triple (degrees) out of a
   rotation matrix so the sliders can mirror drag state."
  [[[m00 _ _] [m10 m11 m12] [m20 m21 m22]]]
  (let [sy (- m20)
        cy (Math/sqrt (+ (* m21 m21) (* m22 m22)))]
    (if (< cy 1.0e-6)
      ;; Gimbal lock at ry = ±90°: collapse rz into rx.
      [(* rad->deg (Math/atan2 (- m12) m11))
       (* rad->deg (Math/asin (max -1.0 (min 1.0 sy))))
       0]
      [(* rad->deg (Math/atan2 m21 m22))
       (* rad->deg (Math/atan2 sy cy))
       (* rad->deg (Math/atan2 m10 m00))])))

(defn deg-mod [x] (mod (Math/round x) 360))

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
  (atom (let [rx 25 ry 35 rz 0]
          {:rx rx :ry ry :rz rz
           :rmat (euler->mat rx ry rz)
           :lx 2.2 :ly 2.4 :lz 3.0
           :cam 6
           :drag nil
           :w 800 :h 600})))

(defn resize! [canvas]
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (swap! state assoc :w w :h h)))

;; --- rendering --------------------------------------------------------------

(defn face-render-data [rmat {:keys [idx normal]}]
  (let [verts (mapv (fn [i] (mat-apply rmat (nth cube-vertices i))) idx)
        n     (mat-apply rmat normal)
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

(defn render! [ctx {:keys [rmat lx ly lz cam w h]}]
  (let [light-pos [lx ly lz]]
    (set! (.-fillStyle ctx) "#0a0d12")
    (.fillRect ctx 0 0 w h)
    (draw-light-glow! ctx w h cam light-pos)
    (let [faces (->> cube-faces
                     (map #(face-render-data rmat %))
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

(defn fmt-deg [v] (str v "°"))
(defn fmt-pos [v] (.toFixed v 1))

(defn sync-slider!
  "Push a state-driven change back into the slider DOM (drag and slider feed
   the same atom, but programmatic .value changes don't fire 'input', so
   there's no feedback loop)."
  [id v fmt]
  (when-let [el (.getElementById js/document id)]
    (set! (.-value el) v))
  (when-let [out (.getElementById js/document (str id "-out"))]
    (set! (.-textContent out) (fmt v))))

(defn bind-slider! [id k fmt]
  (let [el  (.getElementById js/document id)
        out (.getElementById js/document (str id "-out"))]
    (when el
      (.addEventListener
       el "input"
       (fn [e]
         (let [v (js/parseFloat (.. e -target -value))]
           (set! (.-textContent out) (fmt v))
           (swap! state
                  (fn [s]
                    (let [s* (assoc s k v)]
                      (if (#{:rx :ry :rz} k)
                        (assoc s* :rmat (euler->mat (:rx s*) (:ry s*) (:rz s*)))
                        s*))))))))))

(defn on-mousedown [e]
  (.preventDefault e)
  (let [s @state]
    (swap! state assoc :drag {:sx (.-clientX e) :sy (.-clientY e)
                              :rmat0 (:rmat s)})))

(defn on-mousemove [e]
  (when-let [d (:drag @state)]
    (let [dx (- (.-clientX e) (:sx d))
          dy (- (.-clientY e) (:sy d))
          ;; Compose world-axis rotations onto the matrix snapshot taken at
          ;; mousedown. Left-multiplying means dx/dy always rotate around the
          ;; screen's Y/X axes regardless of current orientation — no gimbal.
          rmat* (mat-mul (mat-rot-x (* dy drag-sens deg->rad))
                         (mat-mul (mat-rot-y (* dx drag-sens deg->rad))
                                  (:rmat0 d)))
          [erx ery erz] (mat->euler rmat*)
          rx* (deg-mod erx)
          ry* (deg-mod ery)
          rz* (deg-mod erz)]
      (swap! state assoc :rmat rmat* :rx rx* :ry ry* :rz rz*)
      (sync-slider! "rotX" rx* fmt-deg)
      (sync-slider! "rotY" ry* fmt-deg)
      (sync-slider! "rotZ" rz* fmt-deg))))

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

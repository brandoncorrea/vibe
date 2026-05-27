(ns cube.core-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [cube.core :as c]))

(def ^:private eps 1.0e-9)

(defn- approx=
  ([a b] (approx= a b eps))
  ([a b tol] (< (Math/abs (- a b)) tol)))

(defn- vec-approx=
  ([v1 v2] (vec-approx= v1 v2 eps))
  ([v1 v2 tol]
   (and (= (count v1) (count v2))
        (every? true? (map #(approx= %1 %2 tol) v1 v2)))))

(defn- mat-approx=
  ([m1 m2] (mat-approx= m1 m2 eps))
  ([m1 m2 tol]
   (every? true? (map #(vec-approx= %1 %2 tol) m1 m2))))

;; --- vector helpers --------------------------------------------------------

(deftest v-subtract
  (is (= [1 2 3] (c/v- [4 5 6] [3 3 3])))
  (is (= [0 0 0] (c/v- [1 2 3] [1 2 3])))
  (is (= [-1 -1 -1] (c/v- [0 0 0] [1 1 1]))))

(deftest v-scale
  (is (= [2 4 6] (c/v* [1 2 3] 2)))
  (is (= [0 0 0] (c/v* [1 2 3] 0)))
  (is (= [-1 -2 -3] (c/v* [1 2 3] -1))))

(deftest dot-product
  (is (= 32 (c/vdot [1 2 3] [4 5 6])))
  (is (= 0 (c/vdot [1 0 0] [0 1 0])))      ; orthogonal
  (is (= 1 (c/vdot [1 0 0] [1 0 0]))))     ; parallel

(deftest length
  (is (approx= 1 (c/vlen [1 0 0])))
  (is (approx= 5 (c/vlen [3 4 0])))
  (is (approx= 0 (c/vlen [0 0 0]))))

(deftest normalize
  (is (vec-approx= [1 0 0] (c/vnorm [5 0 0])))
  (is (vec-approx= [0 0 0] (c/vnorm [0 0 0])) "zero vector stays zero")
  (let [n (c/vnorm [3 4 0])]
    (is (vec-approx= [0.6 0.8 0] n))
    (is (approx= 1 (c/vlen n)))))

;; --- matrices --------------------------------------------------------------

(deftest identity-matrix
  (is (vec-approx= [1 2 3] (c/mat-apply c/identity-mat [1 2 3]))))

(deftest rot-x-90deg
  (let [m (c/mat-rot-x (/ Math/PI 2))]
    (is (vec-approx= [0  0  1] (c/mat-apply m [0 1 0])))   ; +Y -> +Z
    (is (vec-approx= [0 -1  0] (c/mat-apply m [0 0 1])))   ; +Z -> -Y
    (is (vec-approx= [1  0  0] (c/mat-apply m [1 0 0]))))) ; X unchanged

(deftest rot-y-90deg
  (let [m (c/mat-rot-y (/ Math/PI 2))]
    (is (vec-approx= [0 0 -1] (c/mat-apply m [1 0 0])))    ; +X -> -Z
    (is (vec-approx= [1 0  0] (c/mat-apply m [0 0 1])))    ; +Z -> +X
    (is (vec-approx= [0 1  0] (c/mat-apply m [0 1 0]))))) ; Y unchanged

(deftest rot-z-90deg
  (let [m (c/mat-rot-z (/ Math/PI 2))]
    (is (vec-approx= [ 0 1 0] (c/mat-apply m [1 0 0])))    ; +X -> +Y
    (is (vec-approx= [-1 0 0] (c/mat-apply m [0 1 0])))    ; +Y -> -X
    (is (vec-approx= [ 0 0 1] (c/mat-apply m [0 0 1]))))) ; Z unchanged

(deftest mul-with-identity
  (let [m (c/mat-rot-x 0.7)]
    (is (mat-approx= m (c/mat-mul c/identity-mat m)))
    (is (mat-approx= m (c/mat-mul m c/identity-mat)))))

(deftest mul-composes-rotations
  ;; Rotating around Y by 90° twice = 180° = flip [1 0 0] to [-1 0 0]
  (let [ry90  (c/mat-rot-y (/ Math/PI 2))
        ry180 (c/mat-mul ry90 ry90)]
    (is (vec-approx= [-1 0  0] (c/mat-apply ry180 [1 0 0])))
    (is (vec-approx= [ 0 0 -1] (c/mat-apply ry180 [0 0 1])))))

;; --- Euler conversions -----------------------------------------------------

(deftest euler-zero-is-identity
  (is (mat-approx= c/identity-mat (c/euler->mat 0 0 0))))

(deftest euler-roundtrip
  ;; Values stay within the non-gimbal range (|ry| < 90°).
  (doseq [triple [[0 0 0]
                  [30 0 0]
                  [0 45 0]
                  [0 0 60]
                  [20 30 40]
                  [-15 25 -45]
                  [89 0 0]
                  [0 -89 0]]]
    (let [[rx ry rz] triple
          [erx ery erz] (c/mat->euler (c/euler->mat rx ry rz))]
      (is (approx= rx erx 1.0e-6) (str "rx for " triple " got " erx))
      (is (approx= ry ery 1.0e-6) (str "ry for " triple " got " ery))
      (is (approx= rz erz 1.0e-6) (str "rz for " triple " got " erz)))))

(deftest mat->euler-gimbal-lock
  ;; At ry = 90°, cos(ry) = 0 — exercise the fallback branch and confirm ry.
  (let [m (c/euler->mat 0 90 0)
        [_ ery _] (c/mat->euler m)]
    (is (approx= 90 ery 1.0e-6))))

;; --- deg-mod ---------------------------------------------------------------

(deftest deg-mod-cases
  (is (= 0   (c/deg-mod 0)))
  (is (= 0   (c/deg-mod 360)))
  (is (= 0   (c/deg-mod 720)))
  (is (= 359 (c/deg-mod -1)))
  (is (= 180 (c/deg-mod 540)))   ; 540 mod 360 = 180
  (is (= 46  (c/deg-mod 45.7)))  ; rounds first
  (is (= 45  (c/deg-mod 45.4))))

;; --- project ---------------------------------------------------------------

(deftest project-origin-to-canvas-center
  (let [[x y depth] (c/project 800 600 6 [0 0 0])]
    (is (approx= 400 x))
    (is (approx= 300 y))
    (is (approx= 6   depth))))

(deftest project-perspective
  ;; A point closer to the camera (larger z, since camera sits at +cam) has
  ;; smaller depth and therefore larger screen-space scale.
  (let [[_ _ d-near] (c/project 800 600 6 [0 0  1])
        [_ _ d-far]  (c/project 800 600 6 [0 0 -1])]
    (is (< d-near d-far))))

(deftest project-y-axis-flip
  ;; +Y in world should land ABOVE canvas midline (smaller screen-y).
  (let [[_ y-pos _] (c/project 800 600 6 [0  1 0])
        [_ y-neg _] (c/project 800 600 6 [0 -1 0])]
    (is (< y-pos 300))
    (is (> y-neg 300))))

(deftest project-clamps-degenerate-depth
  ;; A vertex at z == cam-distance would give zero depth; project should clamp
  ;; instead of dividing by zero.
  (let [[_ _ d] (c/project 800 600 6 [0 0 6])]
    (is (>= d 0.01))))

;; --- shade -----------------------------------------------------------------

(deftest shade-face-toward-light
  ;; Light directly above face center along normal: full diffuse contribution.
  (let [[r g b] (c/shade [0 0 5] [0 0 1] [0 0 0])
        [lr lg lb] c/light-color
        [ar ag ab] c/ambient
        [br bg bb] c/base-face]
    (is (approx= (min 1 (+ ar (* br lr))) r))
    (is (approx= (min 1 (+ ag (* bg lg))) g))
    (is (approx= (min 1 (+ ab (* bb lb))) b))))

(deftest shade-face-away-from-light
  ;; Normal pointing away from the light: diffuse term clamps to 0,
  ;; so only ambient remains.
  (let [[r g b] (c/shade [0 0 5] [0 0 -1] [0 0 0])
        [ar ag ab] c/ambient]
    (is (approx= ar r))
    (is (approx= ag g))
    (is (approx= ab b))))

(deftest shade-clamps-to-1
  ;; Light right on top of the surface can blow out; channels still ≤ 1.
  (let [[r g b] (c/shade [0 0 0.001] [0 0 1] [0 0 0])]
    (is (<= r 1))
    (is (<= g 1))
    (is (<= b 1))))

;; --- rgb->css --------------------------------------------------------------

(deftest rgb-to-css-string
  (is (= "rgb(0,0,0)"       (c/rgb->css [0 0 0])))
  (is (= "rgb(255,255,255)" (c/rgb->css [1 1 1])))
  ;; 0.5 * 255 = 127.5, Math.round rounds half toward +∞ → 128
  (is (= "rgb(0,128,255)"   (c/rgb->css [0 0.5 1]))))

;; --- face-render-data ------------------------------------------------------

(deftest face-render-data-identity
  (let [face {:idx [4 5 6 7] :normal [0 0 1]}
        {:keys [verts normal center]} (c/face-render-data c/identity-mat face)]
    (is (= [(nth c/cube-vertices 4)
            (nth c/cube-vertices 5)
            (nth c/cube-vertices 6)
            (nth c/cube-vertices 7)]
           verts))
    (is (vec-approx= [0 0 1] normal))
    (is (vec-approx= [0 0 1] center) "front face center is at z=+1")))

(deftest face-render-data-rotated
  ;; After 180° Y rotation, front face's outward normal flips to -Z.
  (let [m (c/mat-rot-y Math/PI)
        face {:idx [4 5 6 7] :normal [0 0 1]}
        {:keys [normal]} (c/face-render-data m face)]
    (is (vec-approx= [0 0 -1] normal))))

;; --- geometry consts -------------------------------------------------------

(deftest cube-geometry-is-well-formed
  (is (= 8 (count c/cube-vertices)))
  (is (= 6 (count c/cube-faces)))
  (doseq [{:keys [idx normal]} c/cube-faces]
    (is (= 4 (count idx)) "each face has 4 corners")
    (is (approx= 1 (c/vlen normal)) "each face normal is unit length"))
  (doseq [{:keys [idx]} c/cube-faces
          i idx]
    (is (and (>= i 0) (< i 8)) "vertex indices are in range")))

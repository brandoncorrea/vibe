(ns ball.config
  "Static knobs: physics constants and visual/kind registries.")

;; --- Physics ----------------------------------------------------------------

(def ^:const gravity 0.6)
(def ^:const wall-damping 0.92)
(def ^:const max-trail 80)

;; --- Player -----------------------------------------------------------------

(def ^:const player-w 32)
(def ^:const player-h 44)
(def ^:const player-speed 7)
(def ^:const player-jump 15)
(def ^:const player-gravity 0.8)
(def ^:const invuln-frames 60)
(def ^:const max-lives 5)

;; --- Projectiles ------------------------------------------------------------

(def ^:const proj-radius 6)
(def ^:const proj-speed 22)
(def ^:const max-drag 200)
(def ^:const min-drag 12)
(def ^:const fire-cooldown 8)

;; --- Power-ups --------------------------------------------------------------

(def ^:const powerup-size 28)
(def ^:const powerup-gravity 0.35)
(def ^:const powerup-ttl 480)
(def ^:const drop-rate 0.35)
(def ^:const multishot-frames 360)
(def ^:const multishot-spread 0.26)

;; --- Visual & kind registries -----------------------------------------------

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

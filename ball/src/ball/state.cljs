(ns ball.state
  "Pure constructors for the game-state map."
  (:require [ball.config :as cfg]))

(defn ball [kind x y dir]
  (let [{:keys [palette radius hp speed bounce-gain max-speed homing]}
        (cfg/ball-kinds kind)]
    {:kind        kind
     :palette     palette
     :radius      radius
     :hp          hp
     :max-hp      hp
     :bounce-gain bounce-gain
     :max-speed   max-speed
     :homing      (or homing 0)
     :x           x
     :y           y
     :vx          (* dir speed)
     :vy          0
     :trail       '()}))

(defn fresh-player [w h]
  {:x        (/ w 2)
   :y        (- h cfg/player-h)
   :vx       0
   :vy       0
   :on-floor true
   :invuln   0
   :facing   1})

(defn starting-balls [w]
  [(ball :basic 200 100 1)
   (ball :fast (- w 200) 200 -1)
   (ball :armored (/ w 2) 80 1)
   (ball :homing (/ w 3) 260 -1)])

(defn fresh-state [w h]
  {:balls       (starting-balls w)
   :player      (fresh-player w h)
   :projectiles []
   :powerups    []
   :buffs       {}
   :aim         nil
   :keys        #{}
   :lives       3
   :pops        0
   :score       0
   :cooldown    0
   :phase       :playing
   :w           w
   :h           h})

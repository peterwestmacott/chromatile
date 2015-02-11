(ns cljstemplate.core
  (:use [cljstemplate.constance :only [PI TAU TAU_3RD TAU_4TH TAU_6TH TAU_8TH TAU_12TH
                                       ROOT_TWO ROOT_THREE]]
        [cljstemplate.shapeconstance :only [square-pad square-radius
                                            hex-pad hex-radius
                                            tri-pad tri-radius]]
        [cljstemplate.logging :only [logger log-when-changes]]
        [cljstemplate.shape :only [level-1 render check-connections do-rotations]]
        [cljstemplate.levels :only [get-level shuffle-shapes]]
        )
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.browser.repl :as repl]
            [goog.dom :as dom]
            [goog.events :as events]
            [cljs.core.async :refer [put! chan <! close!]])
  (:import [goog Uri]))


;; (repl/connect "http://localhost:9000/repl")


(def log (logger :core))

;;;;;;;;;;

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
                   (fn [e] (put! out e)))
    out))

;;;;;;;;;;

(def pointer-state (atom nil))

(defn handle-click [event]
  (let [rect (.getBoundingClientRect (dom/getElement "theCanvas"))]
    (reset! pointer-state {:x (- (.-clientX event) (.-left rect))
                           :y (- (.-clientY event) (.-top rect))
                           :clicked true})))

(let [clicks (listen (dom/getElement "theCanvas") "click")]
  (go (while true
        (handle-click (<! clicks)))))

(defn handle-moves [event]
  (let [rect (.getBoundingClientRect (dom/getElement "theCanvas"))]
    (swap! pointer-state merge {:x (- (.-clientX event) (.-left rect))
                                :y (- (.-clientY event) (.-top rect))})))

(let [moves (listen (dom/getElement "theCanvas") "mousemove")]
  (go (while true
        (handle-moves (<! moves)))))

;;;;;;;;;;

;(defn load-next []
;  (start))

(let [clicks (listen (dom/getElement "nextButton") "click")]
  (go (while true
        (<! clicks)
        (level-up inc))))

;;;;;;;;;

(defn done-fn []
  (set! (.-visibility (.-style (dom/getElement "nextButton"))) "visible"))

(def this-level-id (atom 0))
(def this-level (atom nil))
(def level-checked (atom false))
(def shuffles-so-far (atom 0))
(def done (atom false))

;;;;;;;;;

(defn reset-canvas []
  (let [width (. js/window -innerWidth)
        height (. js/window -innerHeight)
        c (dom/getElement "theCanvas")]
    (set! (.-width c) width)
    (set! (.-height c) height)
    (set! (.-width (.-style c)) width)
    (set! (.-height (.-style c)) height)
    (def canvas [(.getContext c "2d") width height])))

(reset-canvas)
;
(let [resizes (listen js/window "resize")]
  (go (while true
        (<! resizes)
        (reset-canvas))))

;;;;;;;;;;

(defn fill-rect [[surface] [x y width height] [r g b]]
  (set! (. surface -fillStyle) (str "rgb(" r "," g "," b ")"))
  (.fillRect surface x y width height))

(defn clear [[surface w h] color]
  (fill-rect [surface w h] [0 0 w h] color))

;;;;;;;;;;




(defn per-frame-processing [timestamp]
  (clear canvas (first (:colours @this-level)))
  (swap! this-level check-connections)
  (swap! this-level (partial do-rotations timestamp))
  (let [{x :x y :y clicked :clicked} @pointer-state
        was-done @done
        canvas (if @level-checked canvas [(first canvas) 1 1])]

    (swap! this-level #(render canvas % [x y clicked timestamp] timestamp done))

    (cond
      (and @level-checked @done was-done) nil
      (and @level-checked @done (not was-done)) (done-fn)
      (and (not @level-checked) (not @done)) (reset! level-checked true)
      (and (not @level-checked) @done) (if (< 3 (swap! shuffles-so-far inc))
                                         (level-up identity)
                                         (do
                                           (log "Shuffling")
                                           (swap! this-level shuffle-shapes)
                                           (reset! done false)))
      :else nil))

  (swap! pointer-state dissoc :clicked))

(defn animate [timestamp]
  (per-frame-processing timestamp)

  ;(js/setTimeout
  ;  (fn [] (.requestAnimationFrame js/window animate)) 330)

  (.requestAnimationFrame js/window animate)
  )


;(defn start []
;  (let [uri (Uri. (.-location js/window))
;        level-str (.getParameterValue uri "level")
;        level (if (and level-str (re-matches #"\d+" level-str))
;                (js/parseInt level-str)
;                0)
;        next-uri (.setParameterValue uri "level" (str (inc level)))]
;    (def this-level (atom (get-level level)))
;    (def next-location next-uri)
;    (log (str "Level is: " level))
;    (log (str @this-level)))
;  (.requestAnimationFrame js/window animate))


(defn level-up [level-fn]
  (set! (.-visibility (.-style (dom/getElement "nextButton"))) "hidden")
  (swap! this-level-id level-fn)
  (log (str "Loading level " @this-level-id))
  (reset! this-level (get-level @this-level-id))
  (reset! level-checked false)
  (reset! done false)
  (reset! shuffles-so-far 0)

  (log (str @this-level))

  )


(defn begin []
  (level-up (fn [x] 0))
  (.requestAnimationFrame js/window animate))

(begin)

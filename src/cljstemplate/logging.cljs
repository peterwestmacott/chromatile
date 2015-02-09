(ns cljstemplate.logging)


(def config {:core true
             :shape true
             :changes true})

(defn logger [log-name]
  (if (config log-name)
    (fn [x] (.log js/console (str "<" (name log-name) ">") x) x)
    identity))


(def change-log (logger :changes))

(def value-store (atom {}))

(defn log-when-changes [key value]
  (if (= value (@value-store key))
    value
    (do
      (swap! value-store assoc key value)
      (change-log (str key " : " value)))))
(ns url-shortener.core
  (:gen-class)
  (:require
   [jvm-utils.core :as jvm]))


(defn -main [& args]
  (jvm/merge-properties))


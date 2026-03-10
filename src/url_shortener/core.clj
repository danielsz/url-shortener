(ns url-shortener.core
  (:gen-class)
  (:require
   [jvm-utils.core :as jvm]
   [system.repl :refer [set-init! go]]
   [url-shortener.system :refer [prod]]))


(defn -main [& args]
  (jvm/merge-properties)
  (set-init! #'prod)
  (go))





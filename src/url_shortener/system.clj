(ns url-shortener.system
  (:require
   [url-shortener.handler :refer [default-handler middleware ring-handler]]
   [url-shortener.analytics :refer [map->AnalyticsConsumer]]
   [com.stuartsierra.component :as component]
   
   [clojure.java.io :as io]
   [clojure.core.async :refer [chan]]
   [system.components
    [maxminddb :refer [new-maxmind-db]]
    [endpoint :refer [new-endpoint]]
    [handler :refer [new-handler]]
    [redis :refer [new-redis]]
    [http-kit :refer [new-http-kit]]
    [repl-server :refer [new-repl-server]]
    [core-async-pubsub :refer [new-pubsub]]]))

(defn base []
  (component/system-map
   :redis (new-redis :spec {:db 0})
   :pubsub (new-pubsub  (fn [_] (chan 1024)) :topic)
   :geoip (new-maxmind-db :db (io/file (System/getProperty "geoip.db")))
   :analytics (component/using (map->AnalyticsConsumer {}) [:pubsub :geoip])
   :endpoint (component/using (new-endpoint :routes ring-handler) [:redis :geoip])
   :handler (component/using (new-handler :default-handler default-handler :options {:middleware middleware}) [:redis :endpoint :pubsub])
   :httpd (component/using (new-http-kit :port (Integer. (System/getProperty "http.port"))) [:handler])))


(defn prod
  "Assembles and returns components for a production deployment"
  []
  (merge (base)
         (component/system-map
          :repl-server (new-repl-server :port (Integer. (System/getProperty "repl.port")) :bind (System/getProperty "repl.ip") :with-cider false)
          :cider-repl-server (new-repl-server :port (inc (Integer. (System/getProperty "repl.port"))) :bind (System/getProperty "repl.ip") :with-cider true))))

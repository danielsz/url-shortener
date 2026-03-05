(ns url-shortener.system
  (:require
   [url-shortener.handler :refer [default-handler ring-handler middleware]]
   [com.stuartsierra.component :as component]
   [system.components
    [endpoint :refer [new-endpoint]]
    [handler :refer [new-handler]]
    [redis :refer [new-redis]]
    [http-kit :refer [new-http-kit]]]))

(defn base []
  (component/system-map
   :redis (new-redis :spec {:db 0})
   :endpoint (component/using (new-endpoint :routes ring-handler) [:redis])
   :handler (component/using (new-handler :default-handler default-handler :options {:middleware middleware}) [:endpoint])
   :httpd (component/using (new-http-kit :port (Integer. (System/getProperty "http.port"))) [:handler])))


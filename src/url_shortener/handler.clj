(ns url-shortener.handler
  (:require
   [url-shortener.shortener :refer [create-short-url handle-redirect]]
   [reitit.ring :as ring]
   [ring.util.response :refer [header status]]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [muuntaja.middleware :refer [wrap-params wrap-format]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [clojure.tools.logging :as log]))


(defn ring-handler [{redis :redis}]
  (ring/router [["/shorten" {:post create-short-url
                             :options (fn [_] (header (status 204) "Allow" "OPTIONS, POST"))}]]))

(defn default-handler [_]
  (ring/routes
   (ring/create-resource-handler {:path "/" :root ""})
   (ring/ring-handler (ring/router ["/:path" {:get handle-redirect
                                              :head handle-redirect
                                              :options (fn [_] (header (status 204) "Allow" "OPTIONS, GET, HEAD"))}]))
   (ring/create-default-handler)))

(def middleware [[wrap-defaults api-defaults]
                 wrap-format
                 wrap-params
                 wrap-forwarded-remote-addr])

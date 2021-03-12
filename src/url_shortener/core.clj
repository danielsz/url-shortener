(ns url-shortener.core
  (:gen-class)
  (:require
    [org.httpkit.server :refer [run-server]] ; Web server
    [taoensso.carmine :as redis] ; Redis client
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
    [clojure.tools.logging :as log]
    [jvm-utils.core :as jvm])
  (:import
    clojure.lang.Murmur3 ; Look what I found!
    org.apache.commons.validator.routines.UrlValidator))

(def validator (UrlValidator. (into-array ["http" "https"])))
(def hash-url (comp (partial format "%x")
                    #(Murmur3/hashUnencodedChars %)))

(defn create-short-url [path]
  (let [rand-str (hash-url path)]
    (redis/wcar nil
      (redis/set (str "/" rand-str) path))
    (str (System/getProperty "unshorten.endpoint") rand-str)))

(defn handle-create [{params :params :as request}]
  (let [url (get params "url")]
    (if (.isValid validator url)
      {:status 200 :body (create-short-url url)}
      {:status 401 :body "Invalid Url provided (tuppu.net)"})))

;; publish host details from request headers on a pub/sub channel whose topic is the shortened link
(defn handle-redirect [{path :uri :as request}]
  (let [remote-addr (:remote-addr request)
        [rc url] (redis/wcar nil
                             (redis/publish path remote-addr)
                             (redis/get path))]
    (if url
      {:status 301 :body "" :headers {"Location" url}}
      {:status 404 :body "Unknown destination."})))

(defn handler [{method :request-method :as req}]
  (case method
    :get (handle-redirect req)
    :head (handle-redirect req)
    :post (handle-create req)
    :options {:status 204 :headers {"Allow" "OPTIONS, GET, HEAD, POST"}}
    {:status 405})) ; Method Not Allowed

(defn -main [& args]
  (jvm/merge-properties)
  (run-server (-> handler wrap-params wrap-forwarded-remote-addr) {:port (Integer. (System/getProperty "listening.port"))}))


(ns url-shortener.shortener
  (:require [taoensso.carmine :as redis]
            [clojure.tools.logging :as log]
            [url-shortener.analytics :refer [write-analytics!]]
            [url-shortener.shared :refer [hash-url url-validator]]
            [url-shortener.schema :refer [user-key TTL-LINK]]
            [ring.util.response :refer [response bad-request not-found redirect]]))


(defn shorten [{{:keys [url user] :or {user "anonymous"}} :params :as request}]
  (if (.isValid url-validator url)
    (let [path (hash-url url)]
      (redis/wcar nil
                  (redis/hset path "url" url "user" user)
                  (redis/hsetnx path "clicks" 0)  
                  (redis/expire path TTL-LINK)
                  (redis/sadd  (user-key user) path))
      (response (str (System/getProperty "shortener.service") path)))
    (bad-request "Invalid Url provided (tuppu.net)")))

(defn handle-redirect [geoip {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        [rc url] (redis/wcar nil
                      (redis/hincrby path "clicks" 1)
                      (redis/hget    path "url"))]
    (log/debug "rc" rc)
    (when url
      (future (write-analytics! geoip path remote-addr referer)))
    (if url
      (redirect url)
      (not-found "Unknown destination."))))

(defn handle-redirect-with-legacy [geoip {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        legacy-path  (str "/" path)
        type (redis/wcar nil (redis/type path))]
    (cond
          (= type "hash")
          (let [[_ url] (redis/wcar nil
                                    (redis/hincrby path "clicks" 1)
                                    (redis/hget    path "url"))]
            (when url (future (write-analytics! geoip path remote-addr referer)))
            (if url (redirect url) (not-found "Unknown destination.")))

          (= type "none")
          (let [[_ legacy-url] (redis/wcar nil
                                         (redis/publish legacy-path {:remote-addr remote-addr :referer referer})
                                         (redis/get legacy-path))]
            (if legacy-url
              (redirect legacy-url)
              (not-found "Unknown destination (legacy).")))

          :else
          (not-found "Unknown destination (including legacy)."))))


(defn legacy [{path :uri :as request}]
  (let [remote-addr (:remote-addr request)
        referer (get (:headers request) "referer")
        [rc url] (redis/wcar nil
                             (redis/publish (str "/" path) {:remote-addr remote-addr :referer referer})
                             (redis/get (str "/" path)))]
    (log/debug "rc" rc)
    (if url
      {:status 301 :body "" :headers {"Location" url}}
      {:status 404 :body "Unknown destination."})))

(ns url-shortener.shortener
  (:require [taoensso.carmine :as redis]
            [clojure.core.async :as a :refer [<!! >!! >! <! chan thread timeout go]]
            [clojure.tools.logging :as log]
            [url-shortener.analytics :refer [write-analytics!]]
            [url-shortener.shared :refer [hash-url url-validator]]
            [url-shortener.schema :refer [user-key]]
            [ring.util.response :refer [response bad-request not-found redirect]]))


(defn shorten [{{:keys [url user description] :or {user "anonymous" description ""}} :params :as request}]
  (if (.isValid url-validator url)
    (let [path (hash-url url)]
      (redis/wcar nil
                  (redis/hset path "url" url "user" user "description" description)
                  (redis/hsetnx path "clicks" 0)  
                  (redis/sadd  (user-key user) path)
                  (redis/sadd   "all-links" path))
      (response (str (System/getProperty "shortener.service") path)))
    (bad-request "Invalid Url provided (tuppu.net)")))


(defn handle-redirect [{redis :redis pubsub :pubsub} {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        [rc url] (redis/wcar nil
                      (redis/hincrby path "clicks" 1)
                      (redis/hget    path "url"))]
    (log/debug "rc" rc)
    (when url
      (thread (>!! (:channel pubsub) {:topic :click :path path :remote-addr remote-addr :referer referer})))
    (if url
      (redirect url)
      (not-found "Unknown destination."))))


(defn handle-redirect-with-legacy [{redis :redis pubsub :pubsub} {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        legacy-path  (str "/" path)
        type (redis/wcar nil (redis/type path))]
    (cond
          (= type "hash")
          (let [[_ url] (redis/wcar nil
                                    (redis/hincrby path "clicks" 1)
                                    (redis/hget    path "url"))]
            (when url
              (thread (>!! (:channel pubsub) {:topic :click :path path :remote-addr remote-addr :referer referer})))
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




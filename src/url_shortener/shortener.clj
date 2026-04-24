(ns url-shortener.shortener
  (:require [taoensso.carmine :as redis]
            [clojure.core.async :as a]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [url-shortener.shared.utils :refer [hash-url url-validator epoch-now]]
            [url-shortener.schema :refer [owner-key group-links-key default-group-id group-key owner-groups-key targets-key]]
            [ring.util.response :refer [response bad-request not-found redirect]]))


(defn shorten [{{:keys [url owner-id group-id description target]
                 :or   {owner-id "anonymous" description ""}} :params}]
  (if (.isValid url-validator url)
    (let [path     (hash-url url)
          group-id (if group-id
                     (str owner-id ":" group-id)
                     (default-group-id owner-id))
          targets  (->> (if (sequential? target) target [target])
                        (remove str/blank?)
                        set)]
      (redis/wcar nil
        (redis/hset   path "url"         url
                           "owner-id"    owner-id
                           "group-id"    group-id
                           "description" description)
        (redis/hsetnx path "clicks" 0)
        (when (seq targets)
          (redis/sadd (targets-key path) targets))
        (redis/hsetnx (group-key group-id) "owner-id" owner-id)
        (redis/hsetnx (group-key group-id) "created"  (epoch-now))
        (redis/sadd   (owner-groups-key owner-id) group-id)
        (redis/sadd   (group-links-key group-id) path)
        (redis/sadd   "all-links" path)
        (redis/sadd   "all-groups" group-id))
      (response (str (System/getProperty "shortener.service") path)))
    (bad-request "Invalid URL provided")))

(defn handle-redirect [{redis :redis pubsub :pubsub} {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        [rc url group-id] (redis/wcar nil
                                      (redis/hincrby path "clicks" 1)
                                      (redis/hget    path "url")
                                      (redis/hget    path "group-id"))]
    (log/debug "rc" rc)
    (when url
      (a/thread (a/>!! (:channel pubsub) {:topic :click :path path :group-id group-id :remote-addr remote-addr :referer referer})))
    (if url
      (redirect url)
      (not-found "Unknown destination."))))


(defn handle-redirect-with-legacy [{redis :redis pubsub :pubsub} {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        legacy-path  (str "/" path)
        type (redis/wcar nil (redis/type path))]
    (cond
          (= type "hash")
          (let [[_ url group-id] (redis/wcar nil
                                    (redis/hincrby path "clicks" 1)
                                    (redis/hget    path "url")
                                     (redis/hget   path "group-id"))]
            (when url
              (a/thread (a/>!! (:channel pubsub) {:topic :click :path path :remote-addr remote-addr :group-id group-id :referer referer})))
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

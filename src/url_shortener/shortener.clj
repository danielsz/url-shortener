(ns url-shortener.shortener
  (:require [taoensso.carmine :as redis]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response bad-request header status not-found redirect]])
  (:import
   clojure.lang.Murmur3 ; Look what I found!
   org.apache.commons.validator.routines.UrlValidator
   org.apache.commons.validator.routines.InetAddressValidator
   java.time.Instant
   java.time.LocalDate
   java.net.InetAddress))


(def ^:private TTL-LINK     (* 90 86400))
(def ^:private TTL-ANALYTICS (* 90 86400))


(def ^:private ip-validator  (InetAddressValidator/getInstance))
(def ^:private url-validator (UrlValidator. (into-array ["http" "https"])))
(def ^:private hash-url (comp (partial format "%x")
                    #(Murmur3/hashUnencodedChars %)))


(defn- ips-key       [path] (str path ":ips"))
(defn- referrers-key [path] (str path ":referrers"))
(defn- daily-key     [path] (str path ":daily"))

(defn- user-key      [user] (str "user:" user ":links"))


(defn- write-analytics! [geoip path remote-addr referer]
  (log/debug path remote-addr referer)
  (log/debug (.get @(:client geoip) (java.net.InetAddress/getByName "2600:3c18::f03c:92ff:fe84:1930") java.util.Map))
  (try
    (when (.isValid ip-validator remote-addr)
      (redis/wcar nil
                  (redis/zadd  (ips-key path) (.getEpochSecond (Instant/now)) remote-addr)
                  (redis/expire (ips-key path) TTL-ANALYTICS)
                  (redis/hincrby (daily-key path) (str (LocalDate/now)) 1)
                  (redis/expire  (daily-key path) TTL-ANALYTICS)))
    (when referer
      (redis/wcar nil
        (redis/lpush  (referrers-key path) referer)
        (redis/expire (referrers-key path) TTL-ANALYTICS)))
    (catch Exception e
      (log/error e "analytics write failed" path))))


(defn create-short-url [{{:keys [url user] :or {user "anonymous"}} :params :as request}]
  (if (.isValid url-validator url)
    (let [path (hash-url url)]
      (redis/wcar nil
                  (redis/hset path "url" url "user" user)
                  (redis/hsetnx path "clicks" 0)  
                  (redis/expire path TTL-LINK)
                  (redis/sadd  (user-key user) path))
      (response (str (System/getProperty "shorten.endpoint") path)))
    (bad-request "Invalid Url provided (tuppu.net)")))

(defn handle-redirect [geoip {{path :path} :path-params remote-addr :remote-addr headers :headers :as request}]
  (let [referer (get headers "referer")        
        [rc url] (redis/wcar nil
                      (redis/hincrby path "clicks" 1)
                      (redis/hget    path "url"))]
    (when url
      (future (write-analytics! geoip path remote-addr referer)))
    (if url
      (redirect url)
      (not-found "Unknown destination."))))

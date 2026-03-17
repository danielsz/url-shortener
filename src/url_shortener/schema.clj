(ns url-shortener.schema)

;; -- Key schema ---------------------------------------------------------------
;;
;; <hash>            hash    url, user, description, clicks TTL-LINK
;; <hash>:ips        zset    ip → last-seen epoch      TTL-ANALYTICS
;; <hash>:referrers  list    referer strings           TTL-ANALYTICS
;; <hash>:daily      hash    "yyyy-MM-dd" → count      TTL-ANALYTICS
;; <hash>:reports    set     share tokens              no TTL
;; <hash>:countries  hash    "ISO-code" → count        TTL-ANALYTICS
;; user:<u>:links     set     paths owned by user       no TTL
;; report:<token>     hash    owner, path, created      TTL-REPORT

(defn user-key      [user] (str "user:" user ":links"))
(defn ips-key       [path] (str path ":ips"))
(defn referrers-key [path] (str path ":referrers"))
(defn daily-key     [path] (str path ":daily"))
(defn countries-key [path] (str path ":countries"))
(defn reports-key   [path] (str path ":reports"))
(defn report-key    [tok]  (str "report:" tok))

(def TTL-REPORT (* 14 86400))
(def TTL-ANALYTICS (* 90 86400))


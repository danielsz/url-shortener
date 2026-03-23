(ns url-shortener.schema)

;; -- Key schema ---------------------------------------------------------------
;;
;; Links
;; <hash>               hash    url, group-id, owner-id, description, clicks    no TTL
;; <hash>:ips           zset    ip → last-seen epoch                            TTL-ANALYTICS
;; <hash>:referrers     list    referer strings                                 TTL-ANALYTICS
;; <hash>:daily         hash    "yyyy-MM-dd" → count                           TTL-ANALYTICS
;; <hash>:weekly        hash    "yyyy-Www" → count     TTL-ANALYTICS
;; <hash>:monthly       hash    "yyyy-MM" → count      TTL-ANALYTICS
;; <hash>:countries     hash    "ISO-code" → count                             TTL-ANALYTICS
;; <hash>:reports       set     report tokens                                   no TTL
;;
;; Groups
;; group:<group-id>             hash    owner-id, created                 no TTL
;; group:<group-id>:links       set     link hashes                             no TTL
;; group:<group-id>:daily       hash    "yyyy-MM-dd" → count                   TTL-ANALYTICS
;; group:<id>:weekly    hash    "yyyy-Www" → count     TTL-ANALYTICS
;; group:<id>:monthly   hash    "yyyy-MM" → count      TTL-ANALYTICS
;; group:<group-id>:countries   hash    "ISO-code" → count                     TTL-ANALYTICS
;; group:<group-id>:ips         zset    ip → last-seen epoch                   TTL-ANALYTICS
;; group:<group-id>:reports     set     report tokens                           no TTL
;;
;; Owners
;; owner:<owner-id>             hash    name, email, plan, created              no TTL
;; owner:<owner-id>:groups      set     group IDs                               no TTL
;;
;; API keys
;; apikey:<key>                 hash    owner-id, group-id, name, created, active   no TTL
;;
;; Reports
;; report:<token>               hash    subject created                       TTL-REPORT
;;
;; Indexes
;; all-links                    set     all link hashes                         no TTL
;; all-groups                   set     all group hashes                        no TTL
;; -- TTLs ---------------------------------------------------------------------

(def TTL-REPORT    (* 14 86400))
(def TTL-ANALYTICS (* 90 86400))

;; -- Link key helpers ---------------------------------------------------------

(defn ips-key        [path] (str path ":ips"))
(defn referrers-key  [path] (str path ":referrers"))
(defn daily-key      [path] (str path ":daily"))
(defn weekly-key        [path]     (str path ":weekly"))
(defn monthly-key       [path]     (str path ":monthly"))
(defn countries-key  [path] (str path ":countries"))
(defn reports-key    [path] (str path ":reports"))

;; -- Group key helpers --------------------------------------------------------

(defn group-key          [group-id] (str "group:" group-id))
(defn group-links-key    [group-id] (str "group:" group-id ":links"))
(defn group-daily-key    [group-id] (str "group:" group-id ":daily"))
(defn group-weekly-key  [group-id] (str "group:" group-id ":weekly"))
(defn group-monthly-key [group-id] (str "group:" group-id ":monthly"))
(defn group-countries-key [group-id] (str "group:" group-id ":countries"))
(defn group-ips-key      [group-id] (str "group:" group-id ":ips"))
(defn group-reports-key  [group-id] (str "group:" group-id ":reports"))

;; -- All ------------------------------------------------------------------------

(def all-groups-key "all-groups")
(def all-links-key  "all-links")

;; -- Owner key helpers --------------------------------------------------------

(defn owner-key        [owner-id] (str "owner:" owner-id))
(defn owner-groups-key [owner-id] (str "owner:" owner-id ":groups"))

;; -- API key helpers ----------------------------------------------------------

(defn apikey-key [key] (str "apikey:" key))

;; -- Report key helpers -------------------------------------------------------

(defn report-key [tok] (str "report:" tok))

;; -- Default group ------------------------------------------------------------

(defn default-group-id [owner-id] (str "default:" owner-id))



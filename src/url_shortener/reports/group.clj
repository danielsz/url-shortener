(ns url-shortener.reports.group
  (:require   
   [taoensso.carmine :as redis]
   [url-shortener.shared.utils :refer [find-or-create-report!]]
   [clojure.tools.logging :as log]
   [ring.util.response :refer [response status]]
   [url-shortener.shared.pages.v2 :as pages]
   [url-shortener.schema :refer [group-key group-reports-key]]))


(defn create-group-report! [owner-id group-id]
  (let [actual-owner (redis/wcar nil (redis/hget (group-key group-id) "owner-id"))]
    (if (= actual-owner owner-id)
      (response (str (System/getProperty "shortener.service") "report/"
                     (find-or-create-report! group-id group-reports-key)))
      (status 403))))


(defn- group-report-page [token group-id]
  (pages/group-dashboard-page group-id
    (str "@get('/report/" token "/stream')")
    nil))


(defn handle-group-report [token report]
  (let [group-id   (get report "subject")]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (group-report-page token group-id)}))



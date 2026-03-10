(ns url-shortener.shared
  (:import org.apache.commons.validator.routines.InetAddressValidator
           org.apache.commons.validator.routines.UrlValidator
           clojure.lang.Murmur3))

(def ip-validator  (InetAddressValidator/getInstance))
(def url-validator (UrlValidator. (into-array ["http" "https"])))

(def hash-url (comp (partial format "%x")
                 #(Murmur3/hashUnencodedChars %)))

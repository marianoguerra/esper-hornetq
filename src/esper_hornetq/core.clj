(ns esper-hornetq.core
  (:gen-class)
  (:require 
    [clj-esper.core :as esper]
    [clojure.tools.logging :as log]
    [clojure.walk :refer [stringify-keys]]
    [immutant.messaging :refer [listen publish respond]]))

;; utilities

(defn- now []
  (.getTime (java.util.Date.)))

(defn- ^java.util.Properties as-properties
  "Convert any seq of pairs to a java.utils.Properties instance.
  Uses as-str to convert both keys and values into strings."
  [m]
  (let [p (java.util.Properties.)]
    (doseq [[k v] m]
      (.setProperty p (name k) (name v)))
    p))

(defn event-to-clj [event]
  (into {} (.getProperties event)))

;; state

(def services (atom {}))

;; public api

(def event-topic "/topic/event")
(def esper-create-query "/queue/esper/create-query")
(def esper-delete-query "/queue/esper/delete-query")

(defrecord EsperService [id name username out-channel domain service config
                         events statements event-ids])

(defn new-event [event-name attributes]
  {:name event-name
   :attributes attributes})

(defn new-service [id sname username out-channel domain]
  (let [config (esper/create-configuration [])
        service (esper/create-service sname config)]
    (->EsperService id sname username out-channel domain service config
                    (atom []) (atom []) (atom #{}))))

(defn register-event [{:keys [config service events event-ids] :as eservice}
                      {:keys [name attributes] :as event}]
  (.addEventType config name (as-properties attributes))
  (.setConfiguration service config)
  (.initialize service)
  (swap! events conj event)
  (swap! event-ids conj name)
  eservice)

(defn register-statement [{:keys [service statements] :as eservice}
                          statement listener]
  (let [esper-statement (esper/create-statement service statement)
        esper-listener (esper/create-listener listener)]
    (swap! statements conj statement)
    (esper/attach-listener esper-statement esper-listener)
    eservice))

(defn handle-event [{:keys [service event-ids] :as eservice}
                    {:keys [username channel value] :as event}]
  (let [event-type (str username "_" channel)]
    ; TODO: doesn't scale
    (when (contains? @event-ids event-type)
      (esper/send-event service (stringify-keys value) event-type))))

(defn create-simple-service [service-id sname event-name attrs statement
                             username out-channel domain]
  (let [topic event-topic
        eservice (new-service service-id sname username out-channel domain)
        event (new-event event-name attrs)
        handler (fn [event]
                  (publish topic {:channel out-channel
                                  :username username
                                  :timestamp (now)
                                  :auth {:username username :domain domain}
                                  :value (event-to-clj event)}))]

    (log/info "creating service" sname ":" username event-name out-channel
              domain statement)
    (register-event eservice event)
    (register-statement eservice statement handler)
    ; TODO: listen only once and call the handlers with event-type
    (listen topic #(handle-event eservice %) :host "localhost" :port 5445)
    (swap! services #(assoc % service-id eservice))
    service-id))

(defn remove-service [service-id]
  (log/info "remove service" service-id)
  (swap! services #(dissoc % service-id))
  service-id)

(defn init []
  (respond esper-create-query (partial apply create-simple-service))
  (respond esper-delete-query (partial apply remove-service)))

(defn main [& args]
  (comment (create-simple-service "ef-service" "EF Service"
                         "mariano_myevent"
                         {:state :string :name :string}
                         "SELECT state, name FROM mariano_myevent"
                         "mariano" "outevent" "public" services))

  (init)
  @(promise))

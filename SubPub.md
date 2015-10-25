Pub/Sub Among Nginx Worker Processes
=================

When `worker_processes  > 1` in nginx.conf, there're more than one JVM instances viz. Nginx worker processes
and requests from the same session perhaps will be handled by different JVM instances. If we want to publish
message to all worker processes there're two way:

1. PubSubTopic(Clojure)/NginxPubSubTopic(Java) -- A high level, shared map based, topic oriented API (since 0.4.3)
2. broadcast -- A low level, message limited API. (since 0.2.5)

Use PubSubTopic(Clojure)/NginxPubSubTopic(Java)
-----------

### Prepare

In nginx.conf we need declare a shared map named `PubSubTopic` first.
Messages will be temperately stored in it when the message is sent to all subscribers it will be removed.

```nginx.conf
shared_map PubSubTopic tinymap?space=1M&entries=1024;
```

### Build A Topic


* **Clojure**

```clojure
  (require '[nginx.clojure.core :as ncc])
  
  ;;build a topic
  (def my-topic (ncc/build-topic! "my-topic"))
  
  (def msg-counter (atomic 0))
```

* **Java**


```java
NginxPubSubTopic myTopic = new NginxPubSubTopic("my-topic");
AtomicInteger msgCounter = new AtomicInteger(0);
```

### Subscribe to the Topic

* **Clojure**

```clojure
  
  (defn sub-handler[msg msg-counter]
    (println "received :" msg ", times=" (swap counter inc)))
  
  ;;subscribe the topic so that we can get all messages of the topic from all jvm instances
  ;;it returns an unsubscribing function for removal.
  (def usub-fun (ncc/sub! my-topic msg-counter sub-handler))
```

* **Java**

```java
PubSubListenerData pd = myTopic.subscribe(, new NginxPubSubListener<AtomicInteger>() {
				@Override
				public void onMessage(String msg, AtomicInteger messageCounter) throws IOException {
				  System.out.println("received: " + msg + ", times=" + messageCounter.increaseAndGet());
				}
			});

```

### Publish Message to The Topic

* **Clojure**

```clojure
(ncc/pub! my-topic "Hello!")
```

* **Java/Groovy**

```java
myTopic.pubish("Hello!");
```


### Unsubscribe 

* **Clojure**

```clojure
;; usub-fun is a return value from sub!
(usub-fun)
```

* **Java**

```java
//pd is a return value from subscribe
myTopic.unsubscribe(pd);
```

Use Broadcast API
-----------

Broadcast API is a low level API which supports two kinds of events to broadcast, simple events and complex events.

1. A simple event only has a event id which is a long integer and must be less than 0x0100000000000000L, it hasn't  any  body or its  body is stored in some external stores,  e.g. SharedHashMap, Memcached, Redis  etc.
2. A complex event has a message with a length limitation `PIPE_BUF - 8`, generally on Linux/Windows is 4088, on MacosX is 504.

Here's a simple Sub/Pub Service based on API of broadcast & Server Sent Events. More details can be found from issue #38 and its comments [nginx-clojure broadcast API][]

* **Clojure**

```clojure

(def sse-subscribers (atom {}))
(def sse-event-tag (int (+ 0x80 10)))

(def init-broadcast-event-listener
  (delay 
    (on-broadcast-event-decode!
      ;;tester
      (fn [{tag :tag}] 
        (= tag sse-event-tag))
      ;;decoder
      (fn [{:keys [tag data offset length] :as e}]
        (assoc e :data (String. data offset length "utf-8"))))
    (on-broadcast! 
      (fn [{:keys [tag data]}]
        (log "#%s ring_handlers_for_test: onbroadcast {%d %s} %s" process-id tag data @sse-subscribers)
        (condp = tag
          sse-event-tag 
            (doseq [ch (keys @sse-subscribers)]
              (send! ch (str "data: " data "\r\n\r\n") true (= "finish!" data) ))
            nil)))))
            

  ;;server sent events publisher
  (defn sse-publisher [req]
         (broadcast! {:tag sse-event-tag, :data (:query-string req)})
         {:body "OK"})

  ;;server sent events subscriber
  (defn sse-sub [^NginxRequest req]
         @init-broadcast-event-listener
         (let [ch (hijack! req true)]
           (on-close! ch ch 
                      (fn [ch] (log "channel closed. id=%d" (.nativeRequest req))
                         (log "#%s sse-sub: onclose arg:%s, sse-subscribers=%s" process-id ch (pr-str @sse-subscribers))
                         (swap! sse-subscribers dissoc ch)))
           (swap! sse-subscribers assoc ch req)
           (send-header! ch 200 {"Content-Type", "text/event-stream"} false false)
           (send! ch "retry: 4500\r\n" true false)))
```

* **Java**

```java

	public static class Init implements NginxJavaRingHandler, Listener {

		public static final int SEVER_SENT_EVENTS = POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START + 1;

		public static Set<NginxHttpServerChannel> serverSentEventSubscribers;
		
		public Init() {
		}
		
		public void doInit() {
			serverSentEventSubscribers = Collections.newSetFromMap(new ConcurrentHashMap<NginxHttpServerChannel, Boolean>());
			NginxClojureRT.getAppEventListenerManager().addListener(this);
		}
		
		@Override
		public void onEvent(PostedEvent event) {
			if (event.tag != LONGPOLL_EVENT && event.tag != SEVER_SENT_EVENTS) {
				return;
			}
			String message = new String((byte[])event.data, event.offset, event.length, DEFAULT_ENCODING);
      if (event.tag == SEVER_SENT_EVENTS) {
				for (NginxHttpServerChannel channel : serverSentEventSubscribers) {
					if ("shutdown!".equals(message)) {
						channel.send("data: "+message+"\r\n\r\n", true, true);
					}else if ("shutdownQuite!".equals(message)) {
						channel.close();
					}else {
						channel.send("data: "+message+"\r\n\r\n", true, false);
					}
				}
			}
			
		}
	}
	public static class SSESub implements NginxJavaRingHandler {

		public void SSESub() {
				new Init().doInit();
		}

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxHttpServerChannel channel = r.hijack(true);
			channel.addListener(channel, new ChannelListener<NginxHttpServerChannel>() {
				@Override
				public void onClose(NginxHttpServerChannel data) {
					Init.serverSentEventSubscribers.remove(data);
					NginxClojureRT.getLog().info("closing...." + data.request().nativeRequest());
				}

				@Override
				public void onConnect(long status, NginxHttpServerChannel data) {
				}
			});
			Init.serverSentEventSubscribers.add(channel);
			channel.sendHeader(200, ArrayMap.create("Content-Type", "text/event-stream").entrySet(), true, false);
			channel.send("retry: 4500\r\n", true, false);
			return null;
		}
	}
	
	public static class SSEPub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			/*
			 * Or use NginxClojureRT.broadcastEvent(Init.SEVER_SENT_EVENTS, request.get(QUERY_STRING).toString());
			 */
			PostedEvent event = new PostedEvent(Init.SEVER_SENT_EVENTS, request.get(QUERY_STRING).toString());
			NginxClojureRT.getAppEventListenerManager().broadcast(event);
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}

```
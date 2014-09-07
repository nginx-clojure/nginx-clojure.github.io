3. More about Nginx-Clojure
=================

3.1 Handle Multiple Coroutine Based Sockets Parallel
-----------------

Sometimes we need invoke serveral remote services before completing the ring  response. For better performance we need a way to handle multiple sockets parallel in sub coroutines.

e.g. fetch two page parallel by clj-http

```clojure
   (let [[r1, r2] 
                (co-pvalues (client/get "http://service1-url") 
                            (client/get "http://service2-url"))]
    ;println bodies of two remote response
    (println (str (:body r1) "====\n" (:body r2) ))
```

Here `co-pvalues` is also non-blocking and coroutine based. In fact it will create two sub coroutines to handle two sockets.

For Java/Groovy, we can use `NginxClojureRT.coBatchCall` to do the same thing. Here 's a simple example for Groovy.

```groovy
     def (r1, r2) = NginxClojureRT.coBatchCall( 
       {"http://mirror.bit.edu.cn/apache/httpcomponents/httpclient/".toURL().text},
       {"http://mirror.bit.edu.cn/apache/httpcomponents/httpcore/".toURL().text})
     return [200, ["Content-Type":"text/html"], r1 + r2];

```

3.2 Shared Map among Nginx Workers
-----------------

Generally use redis or memorycached is the better choice to implement a shared map among Nginx workers. We can do some initialization of the 
shared map by following the guide of [2.2 Initialization Handler for nginx worker](#22-initialization-handler-for-nginx-worker).
If you like shared map managed in nginx  processes better than redis or memcached, you can choose 
[SharedHashMap/Chronicle-Map] which is fast and based on Memory Mapped File so that it can store  
large amout of records and won't need too much java heap memory.

3.3 User Defined Http Method
-----------------

Some web services need user defined http request method to define special operations beyond standard http request methods. 

e.g. We use `MYUPLOAD` to upload a file and overwrite the one if it exists.  The `curl` command maybe is 

```bash
curl   -v  -X MYUPLOAD  --upload-file post-test-data \
"http://localhost:8080/myservice" 
```

In the nginx.conf, we can use `always_read_body on;` to force nginx to read http body.

```nginx

location /myservice {
         handler_type 'clojure';
         always_read_body on;
         handler_code '....';
}

```

3.4 Long Polling & Server Sent Events (SSE)
-----------------

Since v0.2.5, nginx-clojure provides union form of [hijack API][] to do with Long Polling & Server Sent Events (SSE).

### Hijack the Request

We can hijack the request to get a http server channel to sent some messages later. After hijacking the return result from ring handler will be ignore so we can finely control when & what to be sent to the client.

For Clojure

```clojure

(fn my-handler[req]
         (let [ch (hijack! req true)]
          ;;; save channel ch to use it later     
))
```
The complete `hijack!` description is below 

```clojure

(defn hijack! 
  "Hijack a nginx request and return a server channel.
   After being hijacked, the ring handler's result will be ignored.
   If ignore-nginx-filter? is true all data output to channel won't be filtered
   by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
   We can use this function to implement long polling / Server Sent Events (SSE) easily."
  [^NginxRequest req ignore-nginx-filter?])
```

For Java

```java

	public  class MyHandler implements NginxJavaRingHandler {
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = ((NginxJavaRequest)request);
			NginxHttpServerChannel channel = r.handler().hijack(r, true);			
			//save channel ch to use it later    
			//nginx-clojure will ignore this return because we have hijacked the request.
			return null;
		}
	}

```

### Send a Complete Response for Long Polling

When some event happen which let a complete response must be sent to the Long Polling request client we can use `send-response!`(Clojure) or sendResponse (Java/Groovy) to send a complete response. This action is non-blocking and after completion the channel will be closed automatically.

For Clojure

```clojure

(send-response! ch {:status 200, :headers {"content-type" "text/plain"}, :body data}
```

For Java

```Java

channel.sendResponse(new Object[] { NGX_HTTP_OK,
				ArrayMap.create("content-type", "text/json"),
				message});
```

### Send Messages for Server Sent Events (SSE)

First we can use `send-headers!`(Clojure) or `sendHeaders` (Java/Groovy) to send a SSE header. Then we
can use `send!` (Clojure) or `send` (Java/Groovy) to send later messages. The last two parameters of send
function is used to flush message or close channel after sending current message.

For Clojure:

```Clojure

 ;;; send header and retry hint of SSE
 (send-header! ch 200 {"Content-Type", "text/event-stream"} false false)
 (send! ch "retry: 4500\r\n" true false)

 ;;; send the message and do flush 
 (send! ch "data: Are you ok?\r\n" true false)

 ;;; send the last message, identical to (send! ch data true false) (close! ch)
 (send! ch "data: Bye, bye.\r\n" true true)

``` 

For Java:

```Java

//send header and retry hint of SSE
channel.sendHeader(200, ArrayMap.create("Content-Type", "text/event-stream").entrySet(), true, false);
channel.send("retry: 4500\r\n", true, false);

//send the message and do flush 
channel.send("data: Are you ok?\r\n", true, false)

//send the last message, identical to channel.send(data true false); channel.close();
channel.send("data: Bye, bye.\r\n", true, true)
```

### Listener about the Closed Event of Channel

A closed event will happen immediately when channel is closed by either of these three cases:

* channel close function/method is invoked on this channel, e.g. (close! ch)
* inner unrecoverable error happens with this channel, e.g. not enough memory to read/write
* remote client connection is closed or broken.

For Clojure

```clojure

(on-close! ch {:ch ch :desc "this is a event attachement"}
 (fn[att] (info "closed channel from request :" (.request (:ch att)))))
```

For Java

```java

channel.addListener(channel, new ChannelListener<NginxHttpServerChannel>() {
				@Override
				public void onClose(NginxHttpServerChannel data) {
					Init.serverSentEventSubscribers.remove(data);
					info("closed " + data.request().nativeRequest());
				}

				@Override
				public void onConnect(long status, NginxHttpServerChannel data) {
				}
			});
```

3.5 Sub/Pub & broadcast Event
-----------------

Suppose our Nginx instance has 3 workers (worker process not jvm_workers which is just thread number of thread pool in jvm). Now we want to provide sub/pub service. e.g.

1. Client A connected to nginx worker A and subscribed to uri `/mychannel/sub`
2. Client B connected to nginx worker B and subscribed to uri `/mychannel/sub`
3. Client C connected to nginx worker C and publish a message to uri `/mychannel/pub`

So the service at endpoint of  `/mychannel/pub` must broadcast pub event to Client A and Client B.
Although for large-scale application we can use sub/pub service from Redis on nginx-clojure ,  for small-scale or medium-scale application this feature will make the dev life easier.

This feature supports two kinds of events to broadcast, simple events and complex events.

1. A simple event only has a event id which is a long integer and must be less than 0x0100000000000000L, it hasn't  any  body or its  body is stored in some external stores,  e.g. SharedHashMap, Memcached, Redis  etc.
2. A complex event has a message with a length limitation `PIPE_BUF - 8`, generally on Linux/Windows is 4088, on MacosX is 504.

Here's a simple Sub/Pub Service based on API of broadcast & Server Sent Events. More details can be found from issue #38 and its comments [nginx-clojure broadcast API][]

For Clojure

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

For Java

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
			NginxHandler handler = r.handler();
			NginxHttpServerChannel channel = handler.hijack(r, true);
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

3.6 Asynchronous Channel
-----------------

Asynchronous Channel is wrapper of Asynchronous Socket for more easier usage. 
So far Asynchronous Channel *cann't* work with thread pool mode. The Asynchronous Channel 
API is a little like Java 7 NIO.2 Asynchronous Channel and more details can be found from issue #37 and it comments
[Asynchronous Channel API][].

Here 's an example which is to get content from mirror.bit.edu.cn:8080 and sent it to client. 

* [Clojure Example](https://github.com/nginx-clojure/nginx-clojure/blob/master/test/clojure/nginx/clojure/asyn_channel_handlers_for_test.clj)
* [Java Example](https://github.com/nginx-clojure/nginx-clojure/blob/master/test/java/nginx/clojure/net/SimpleHandler4TestNginxClojureAsynChannel.java)


[nginx-clojure broadcast API]: https://github.com/nginx-clojure/nginx-clojure/issues/38
[SharedHashMap/Chronicle-Map]: https://github.com/OpenHFT/Chronicle-Map
[hijack API]: https://github.com/nginx-clojure/nginx-clojure/issues/41
[Asynchronous Channel API]: https://github.com/nginx-clojure/nginx-clojure/issues/37

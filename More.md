3. More about Nginx-Clojure
=================


3.0 More about APIs
-----------------

### Request & Response 

For Clojure the request map and response map are defined by the ring SEPC at https://github.com/ring-clojure/ring/blob/master/SPEC .

For Java/Groovy, the request map contains serveral parts:

1. server-port (Required, Integer) The port on which the request is being handled.
1. server-name (Required, String) The resolved server name, or the server IP address.
1. remote-addr (Required, String) The IP address of the client or the last proxy that sent the request.
1. uri (Required, String) The request URI, excluding the query string and the "?" separator. Must start with "/".
1. query-string (Optional, String) The query string, if present.
1. scheme (Required, String) The transport protocol, must be http or https.
1. request-method (Required, String) The HTTP request method, must be a lowercase keyword corresponding to a HTTP request method, such as :get or :post.
1. content-type **DEPRECATED** (Optional, String) The MIME type of the request body, if known.
1. content-length **DEPRECATED** (Optional, Integer) The number of bytes in the request body, if known.
1. character-encoding **DEPRECATED** (Optional, String) The name of the character encoding used in the request body, if known.
1. sl-client-cert (Optional, X509Certificate) The SSL client certificate, if supplied. This value  is not **supported** yet.
1. headers (Required, Map) A map of header name Strings to corresponding header value Strings.
1. body (Optional, InputStream) An InputStream for the request body, if present.

The return response is an array of object, e.g

```java

 [200, //http status 200 
   ArrayMap.create("Content-Type", "text/html", "", "" ), //headers map
   "Hello, Java & Nginx!" //response body can be string, File or Array/Collection of string or File ]; 
```

>Note that if the rewrite/access handler returns phase-done (Clojure) or Constants.PHRASE_DONE (Groovy/Java), nginx will continue to next phases (e.g. invoke proxy_pass or content ring handler). If the rewrite handler returns a general response, nginx will send this response to the client and stop to continue to next phases.


3.1 Handle Multiple Coroutine Based Sockets Parallel
-----------------

Sometimes we need to invoke serveral remote services before completing the ring  response. For better performance we need a way to handle multiple sockets parallel in sub coroutines.

e.g. fetch two page parallel by clj-http

```clojure
   (let [[r1, r2] 
                (co-pvalues (client/get "http://service1-url") 
                            (client/get "http://service2-url"))]
    ;println bodies of two remote response
    (println (str (:body r1) "====\n" (:body r2) ))
```

Here `co-pvalues` is also non-blocking and coroutine based. In fact it will create two sub coroutines to handle two sockets.

For Java/Groovy, we can use `NginxClojureRT.coBatchCall` to do the same thing. Here's a simple example for Groovy.

```groovy
     def (r1, r2) = NginxClojureRT.coBatchCall( 
       {"http://mirror.bit.edu.cn/apache/httpcomponents/httpclient/".toURL().text},
       {"http://mirror.bit.edu.cn/apache/httpcomponents/httpcore/".toURL().text})
     return [200, ["Content-Type":"text/html"], r1 + r2];

```

3.2 Shared Map among Nginx Workers
-----------------

See [Shared Map & Session Store](sharedmap.html).

3.3 User Defined Http Method
-----------------

Some web services need a user defined http request method to define special operations beyond standard http request methods. 

e.g. We use `MYUPLOAD` to upload a file and overwrite the one if it exists.  The `curl` command could be 

```bash
curl   -v  -X MYUPLOAD  --upload-file post-test-data \
"http://localhost:8080/myservice" 
```

In the nginx.conf, we can use `always_read_body on;` to force nginx to read http body.

```nginx

location /myservice {
         handler_type 'clojure';
         always_read_body on;
         content_handler_code '....';
}

```


3.4 Server Channel for Long Polling & Server Sent Events (SSE)
-----------------

Since v0.2.5, nginx-clojure provides a union form of [hijack API][] to work with Long Polling & Server Sent Events (SSE).

### Hijack the Request

We can hijack the request to get a http server channel to send some messages later. After hijacking the return result from ring handler, will be ignored so we can finely control when & what to be sent to the client.

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
	
The complete java doc about hijack is below

```java
	/**
	 * Get a hijacked Server Channel used to send message later typically in another thread
	 * If ignoreFilter is true all data output to channel won't be filtered
	 * by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
	 * @param req the request object
	 * @param ignoreFilter whether we need ignore nginx filter or not.
	 * @return hijacked channel used to send message later
	 */
	public NginxHttpServerChannel hijack(NginxRequest req, boolean ignoreFilter);
```


### Send a Complete Response for Long Polling

When an event happens which produces a complete response it must be sent to the Long Polling request client. We can use `send-response!`(Clojure) or sendResponse (Java/Groovy) to send a complete response. This action is non-blocking and after completion the channel will be closed automatically.

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
can use `send!` (Clojure) or `send` (Java/Groovy) to send later messages. The last two parameters of the send
function is used to flush a message or close the channel after sending the current message.

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

A closed event will happen immediately when a channel is closed by either of these three cases:

* channel close function/method is invoked on this channel, e.g. (close! ch)
* inner unrecoverable error happens with this channel, e.g. not enough memory to read/write
* remote client connection is closed or broken.

For Clojure

```clojure

(on-close! ch {:ch ch :desc "this is an event attachement"}
 (fn[att] (info "closed channel from request :" (.request (:ch att)))))
```

For Java

```java

channel.addListener(channel, new ChannelCloseAdapter<NginxHttpServerChannel>() {
				@Override
				public void onClose(NginxHttpServerChannel data) {
					Init.serverSentEventSubscribers.remove(data);
					info("closed " + data.request().nativeRequest());
				}
			});
```

3.5 Sub/Pub & broadcast Event
-----------------

Suppose our Nginx instance has 3 workers (worker process not jvm_workers which is just thread number of thread pool in jvm). Now we want to provide a sub/pub service. e.g.

1. Client A connected to nginx worker A and subscribed to uri `/mychannel/sub`
2. Client B connected to nginx worker B and subscribed to uri `/mychannel/sub`
3. Client C connected to nginx worker C and publish a message to uri `/mychannel/pub`

So the service at endpoint of  `/mychannel/pub` must broadcast a pub event to Client A and Client B.
Although for large-scale application we can use a sub/pub service from Redis on nginx-clojure. For small-scale or medium-scale applications this feature will make the dev's life easier.

More details can be found from [Pub/Sub Among Nginx Worker Processes](subpub.html)

3.6 Asynchronous Client Channel
-----------------

Asynchronous Client Channel is a wrapper of Asynchronous Client Socket made for more easier usage. 
So far Asynchronous Channel *can't* work with thread pool mode. The Asynchronous Channel 
API is similar to Java 7 NIO.2 Asynchronous Channel and more details can be found from issue #37 and its comments
[Asynchronous Channel API][].

Here's an example which is retrieving content from mirror.bit.edu.cn:8080 and sending it to the client. 

* [Clojure Example](https://github.com/nginx-clojure/nginx-clojure/blob/master/test/clojure/nginx/clojure/asyn_channel_handlers_for_test.clj)
* [Java Example](https://github.com/nginx-clojure/nginx-clojure/blob/master/test/java/nginx/clojure/net/SimpleHandler4TestNginxClojureAsynChannel.java)


3.7  About Logging
-----------------

For logging with nginx-clojure there are several ways

1. Using System.err.print/println will write a log to nginx error.log. This way is simplest but logging information will be mixed if you have more than one nginx worker.
2. Using clojure tools.logging + logback or slf4j +  logback, we can get one log file per nginx worker.

e.g

in nginx.conf

```nginx

 jvm_options "-DMYPID=#{pno}";
 ```



in logback.xml

```xml

 <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <!-- daily rollover -->
      <fileNamePattern>logs/myapp.%d{yyyy-MM-dd}-${MYPID}.log</fileNamePattern>
      <!-- keep 30 days' worth of history -->
      <maxHistory>30</maxHistory>
    </rollingPolicy>

    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %-10contextName %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
 ```

Then we'll get log files whose name is just like myapp.2014-09-12-1.log, myapp.2014-09-12-2.log and so on.

3.8  Sever Side WebSocket
-----------------

###3.8.1 Echo Service Example
Sever Side WebSocket, like long polling/Server Sent Events, also use hijack API to get a NginxHttpServerChannel to send / receive messages. 
Here we give a echo service example.

In nginx.conf

```nginx
location /my-ws {
        auto_upgrade_ws on;
        content_handler_type java; ###or clojure,groovy
        content_handler_name 'nginx.clojure.java.WSEcho'; ###or ring handler for clojure
        .....
}
```

The directive `auto_upgrade_ws on` is equivalent to 

```java
    //NginxHttpServerChannel sc = r.hijack(true);
		
		//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
		if (!sc.webSocketUpgrade(true)) {
			return null;
		}
```


For clojure

```clojure
(defn echo [^NginxRequest req]
         (-> req
             (hijack! true)
             (add-listener! { :on-open (fn [ch] (log "uri:%s, on-open!" (:uri req)))
                              :on-message (fn [ch msg rem?] (send! ch msg (not rem?) false))
                              :on-close (fn [ch reason] (log "uri:%s, on-close:%s" (:uri req) reason))
                              :on-error (fn [ch error] (log "uri:%s, on-error:%s" (:uri req)  error))
                             })))
```

For java 

```java
package nginx.clojure.java;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import nginx.clojure.MessageAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

public class WSEcho implements NginxJavaRingHandler {

	@Override
	public Object[] invoke(Map<String, Object> request) {
		NginxJavaRequest r = (NginxJavaRequest)request;
		NginxHttpServerChannel sc = r.hijack(true);
		sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
			int total = 0;
			@Override
			public void onOpen(NginxHttpServerChannel data) {
				NginxClojureRT.log.debug("WSEcho onOpen!");
			}

			@Override
			public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) throws IOException {
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.log.debug("WSEcho onTextMessage: msg=%s, rem=%s", message, remaining);
				}
				total += message.length();
				sc.send(message, !remaining, false);
			}
			
			@Override
			public void onBinaryMessage(NginxHttpServerChannel sc, ByteBuffer message, boolean remining) throws IOException {
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.log.debug("WSEcho onBinaryMessage: msg=%s, rem=%s, total=%d", message, remining, total);
				}
				total += message.remaining();
				sc.send(message, !remining, false);
			}
			
			@Override
			public void onClose(NginxHttpServerChannel req, long status, String reason) {
				if (NginxClojureRT.log.isDebugEnabled()) {
				  NginxClojureRT.log.info("WSEcho onClose2: total=%d, status=%d, reason=%s", total, status, reason);
				}
			}
			
			@Override
			public void onError(NginxHttpServerChannel data, long status) {
				if (NginxClojureRT.log.isDebugEnabled()) {
					  NginxClojureRT.log.info("WSEcho onError: total=%d, status=%d", total, status);
					}
			}

		});
		return null;
	}

}
```

###3.8.1 Use Access Handler For WebSocket Security

In the example below we return 404 for non WebSocket request 

```nginx
location /my-ws {
        auto_upgrade_ws on;
        access_handler_type java;
        access_handler_name 'my.WSAccessHandler';
        content_handler_type java; ###or clojure,groovy
        content_handler_name 'nginx.clojure.java.WSEcho'; ###or ring handler for clojure
        .....
}
```

```java
package my;
import nginx.clojure.java.NginxJavaRingHandler;
import static nginx.clojure.java.Constants.*;

public class WSAccessHandler implements NginxJavaRingHandler {
  public Object[] invoke(Map<String, Object> request) {
    if (GET != request.get("request-method") 
        || !"websocket".equals(request.get("headers").get("upgrade"))) {
      return new Object[]{404, null, null};
    }
    return PHASE_DONE;
  }
}

```


3.9  Java standard RESTful web services with Jersey
-----------------

* **Get Jar File**

We can get the released version from [clojars](https://clojars.org/nginx-clojure/nginx-jersey) or 
the jar in [nginx-clojure binary release](https://sourceforge.net/projects/nginx-clojure/files/) 

To get the latest version from the github source

```shell
git clone https://github.com/nginx-clojure/nginx-clojure
cd nginx-clojure/nginx-jersey
lein jar
```

*  **Configuration**

in nginx.conf

```nginx
      location /jersey {
          
          content_handler_type java;
          content_handler_name 'nginx.clojure.bridge.NginxBridgeHandler';
          
          ##we can set system properties ,e.g. m2rep
          #content_handler_property system.m2rep '/home/who/.m2/repository';
          
          ##we can put jars into some dir then all of their path will be appended into the classpath
          #content_handler_property bridge.lib.dirs 'my-jersey-libs-dir:myother-dir';
          
          ##the path of nginx-jersey-x.x.x.jar must be included in the below classpath or one of above #{bridge.lib.dirs}
          ##we can use maven assembly plugin to get a all-in-one jar file with dependencies, e.g. json-jackson-example-with-dependencies.jar.
          content_handler_property bridge.lib.cp 'jars/nginx-jersey-0.1.0.jar:myjars/json-jackson-example-with-dependencies.jar';
          content_handler_property bridge.imp 'nginx.clojure.jersey.NginxJerseyContainer';
          
          ##aplication path usually it is the same with nginx location 
          content_handler_property jersey.app.path '/jersey';
          
          ##application resources which can be either of JAX-RS resources, providers
          content_handler_property jersey.app.resources '
                org.glassfish.jersey.examples.jackson.EmptyArrayResource,
                org.glassfish.jersey.examples.jackson.NonJaxbBeanResource,
                org.glassfish.jersey.examples.jackson.CombinedAnnotationResource,
                org.glassfish.jersey.examples.jackson.MyObjectMapperProvider,
                org.glassfish.jersey.examples.jackson.ExceptionMappingTestResource,
                org.glassfish.jersey.jackson.JacksonFeature
          ';
          gzip on;
          gzip_types application/javascript application/xml text/plain text/css 'text/html;charset=UTF-8'; 
      }
```

All sources about this example can be found from jersey github repository's example [json-jackson](https://github.com/jersey/jersey/tree/2.17/examples/json-jackson/src/main/java/org/glassfish/jersey/examples/jackson).

Then we test the JAX-RS services by curl

```shell
$ curl  -v http://localhost:8080/jersey/emptyArrayResource
> GET /jersey/emptyArrayResource HTTP/1.1
> User-Agent: curl/7.35.0
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Date: Sat, 23 May 2015 17:47:14 GMT
< Content-Type: application/json
< Transfer-Encoding: chunked
< Connection: keep-alive
* Server nginx-clojure is not blacklisted
< Server: nginx-clojure
< 
{
  "emtpyArray" : [ ]
}
```

```shell
$ curl -v http://localhost:8080/jersey/nonJaxbResource
> GET /jersey/nonJaxbResource HTTP/1.1
> User-Agent: curl/7.35.0
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Date: Sat, 23 May 2015 17:46:17 GMT
< Content-Type: application/javascript
< Transfer-Encoding: chunked
< Connection: keep-alive
* Server nginx-clojure is not blacklisted
< Server: nginx-clojure
< 
callback({
  "name" : "non-JAXB-bean",
  "description" : "I am not a JAXB bean, just an unannotated POJO",
  "array" : [ 1, 1, 2, 3, 5, 8, 13, 21 ]
* Connection #0 to host localhost left intact
})
```


3.10 Embeding Tomcat
-----------------

### Which Version?

Apache Tomcat version | Nginx-Clojure version|Nginx-Tomcat8 version
------------ | -------------|-------------
8.0.20 | >=0.4.x|0.1.x
8.0.23,8.0.24 | >=0.4.x|0.2.x

### Get Jar File

We can get the released version from [clojars](https://clojars.org/nginx-clojure/nginx-tomcat8) or 
the jar in [nginx-clojure binary release](https://sourceforge.net/projects/nginx-clojure/files/) 

To get the latest version from the github source

```shell
git clone https://github.com/nginx-clojure/nginx-clojure
cd nginx-clojure/nginx-tomcat8
lein jar
```

### Configuration

in nginx.conf

```nginx
      location / {
      
          content_handler_type java;
          content_handler_name 'nginx.clojure.bridge.NginxBridgeHandler';
          
          ##Tomcat 8 installation path
          content_handler_property system.catalina.home '/home/who/share/apps/apache-tomcat-8.0.20';
          content_handler_property system.catalina.base '#{catalina.home}';
          
          ##uncomment this to disable websocket perframe-compression
          #content_handler_property system.org.apache.tomcat.websocket.DISABLE_BUILTIN_EXTENSIONS true;
          
          ##log manger
          content_handler_property system.java.util.logging.manager 'org.apache.juli.ClassLoaderLogManager';
          
          ## all jars or direct child directories will be appended into the classpath of this bridge handler's class-loader
          content_handler_property bridge.lib.dirs '#{catalina.home}/lib:#{catalina.home}/bin';
          
          ##set nginx tomcat8 bridge implementation jar and other jars can also be appended here
          content_handler_property bridge.lib.cp 'my-jar-path/nginx-tomcat8-x.x.x.jar';
          
          ##The implementation class of nginx-clojure bridge handler for Tomcat 8
          content_handler_property bridge.imp 'nginx.clojure.tomcat8.NginxTomcatBridge';
          
          ##ignore nginx filter, default is false
          #content_handler_property ignoreNginxFilter false;
          
          ##when dispatch is false tomcat servlet will be executed in main thread. By default dispatch is false
          ##when use websocket with tomcat it must be set true otherwise maybe deadlock will happen.
          #content_handler_property dispatch false;
          
          gzip on;
          gzip_types text/plain text/css 'text/html;charset=ISO-8859-1' 'text/html;charset=UTF-8'; 
          
          ##if for small message, e.g. small json/websocket message write_page_size can set to be a small value
          #write_page_size 2k;
      }
```

## Session Management

If `worker_processes` > 1 there will be more than one jvm instances viz. more tomcat instances so to get synchronized session information we cannot use the default tomcat session manger.
Instead we may consider using one of the following:

1. Cookied based Session Store  viz. storing all session attribute information into cookies.
1. Shared HashMap among processes in the same machine ,e.g. nginx-clojure built-in [Shared Map][], OpenHFT [Chronicle Map][]
1. External Session Store,  e.g.  Redis / MySQL / Memcached Session Store


### For Performance

#### Disable Tomcat Access Log

When we need the access log, use Nginx's access log instead of Tomcat's access log.

In server.xml comment out AccessLogValve configuration to disable Tomcat access log.

```xml
<!--
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt"
               pattern="%h %l %u %t &quot;%r&quot; %s %b" />
-->
```

#### Disable logging to console

Because Tomcat console log is duplicated with other logs such as catalina log, manager log, etc, it can be disabled.
In conf/logging.properties remove all `java.util.logging.ConsoleHandler`

```shell
handlers = 1catalina.org.apache.juli.AsyncFileHandler, 2localhost.org.apache.juli.AsyncFileHandler, 3manager.org.apache.juli.AsyncFileHandler, 4host-manager.org.apache.juli.AsyncFileHandler

.handlers = 1catalina.org.apache.juli.AsyncFileHandler
```

#### Don't Enable Tomcat Compression

By default compression is off, do not turn it on.

```xml
<Connector port="8080" protocol="HTTP/1.1" compression="off"
```
If we need compression use nginx gzip filter instead. e.g. In nginx.conf

```nginx
location /examples {
    gzip on;
    gzip_types text/plain text/css 'text/html;charset=ISO-8859-1' 'text/html;charset=UTF-8'; 
}
```
`gzip` can also appear under http and server blocks. More details can be found [HERE](http://nginx.org/en/docs/http/ngx_http_gzip_module.html)


3.11 More about Nginx Worker Process
-----------------
The number of Nginx worker processes can be configured by nginx directive [worker_processes](http://nginx.org/en/docs/ngx_core_module.html#worker_processes).
e.g. We use 8 Nginx worker processes:

```nginx
worker_processes 8;
```

Because a JVM instance will be embeded into every Nginx worker process, if there are more than one Nginx worker prcocesses then there will be more than one JVM instances.
So if we want to have synchronized session information we cannot use the default tomcat session manger. Instead we may consider using one of the following: 

1. Cookie based Session Store  viz. storing all session attribute information into cookies.
1. Shared HashMap among processes in the same machine ,e.g. nginx-clojure built-in [Shared Map][], OpenHFT [Chronicle Map][]
1. External Session Store,  e.g.  Redis / MySQL / Memcached Session Store

When there are is than one Nginx worker process, sub/pub services also need to be taken care for. Subscribers may connect to JVM instances which may be different
than the JVM instance which the publisher sent a message to. So we need to use [broadcast API](more.html#35-subpub--broadcast-event) from nginx-clojure
and broadcast messages to all Nginx worker processes.

[nginx-clojure broadcast API]: https://github.com/nginx-clojure/nginx-clojure/issues/38
[Shared Map]: https://nginx-clojure.github.io/sharedmap.html
[Chronicle Map]: https://github.com/OpenHFT/Chronicle-Map
[hijack API]: https://github.com/nginx-clojure/nginx-clojure/issues/41
[Asynchronous Channel API]: https://github.com/nginx-clojure/nginx-clojure/issues/37
[2.2 Initialization Handler for nginx worker]: configuration.html#user-content-22-initialization-handler-for-nginx-worker


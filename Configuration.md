2. Configurations
=================

2.1 JVM Path , Class Path & Other JVM Options
-----------------

Setting JVM path and class path within `http {` block in  nginx.conf

```nginx

    ###define jvm path, auto for auto-detect jvm path or a real path
    ###for win32,  jvm_path maybe is "C:/Program Files/Java/jdk1.7.0_25/jre/bin/server/jvm.dll";
    ###for macosx, jvm_path maybe is "/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Libraries/libserver.dylib";
    ###for ubuntu, jvm_path maybe is "/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so";
    ###for centos, jvm_path maybe is "/usr/java/jdk1.6.0_45/jre/lib/amd64/server/libjvm.so";
    ###for centos 32bit, jvm_path maybe is "/usr/java/jdk1.7.0_51/jre/lib/i386/server/libjvm.so";
    
    jvm_path auto;
    
    ###define class paths
    ###for clojure, you should append clojure core jar
    ###for groovy, you should append groovy runtime jar
    ### when wildchar * is used after a path, all jars and sub-directories will appended to
    ### the jvm classpath. Note that on windows use `;` as separator instead of `:`
    jvm_classpath "mylibs1/*:mylibs2/*:/myclasses";
    
    
    ###define jvm options
    ###jvm_options can be repeated once per option.
    
    ###uncomment next two line to define jvm heap memory
    #jvm_options "-Xms1024m";
    #jvm_options "-Xmx1024m";
    
    ###for enable java remote debug uncomment next two lines
    ###the remote debug port will be 8401 ~ 840X .
    #jvm_options "-Xdebug";
    #jvm_options "-Xrunjdwp:server=y,transport=dt_socket,address=840#{pno},suspend=n";
````
###Reusable Variables

It 's a new feature since v0.2.5. We can define variables and reuse them in jvm_options to make configurations neat.

```nginx

    ###define nginx_clojure_jar
    jvm_var nginx_clojure_jar '/home/who/git/nginx-clojure/target/nginx-clojure-0.2.5.jar';
    ###reference variable nginx_clojure_jar, starts with #{ different from nginx variable
    jvm_options "-javaagent:#{nginx_clojure_jar}=mb";
    jvm_options "-Xbootclasspath/a:#{nginx_clojure_jar}:jars/clojure-1.5.1.jar"; 
    
    ###define jvm var `ncdev , `mrr, `ncjar, `ncdev is reused in `ncdev
    jvm_var ncdev '/home/who/git/nginx-clojure';
    jvm_var mrr '/home/who/.m2/repository';
    jvm_var ncjar '#{ncdev}/target/nginx-clojure-0.2.5.jar';

    jvm_options "-Djava.class.path=#{ncjar}:#{mrr}/clj-http/clj-http/0.7.8/clj-http-0.7.8.jar";
```

###Specify Debug Ports for JVMs

It 's a new feature since v0.2.5.
If the `worker_processes` > 1, the below code will cause error for multiple JVMs try listen on the same debug port `2400`.

```nginx

    jvm_options "-Xdebug";
    jvm_options "-Xrunjdwp:server=y,transport=dt_socket,address=2400,suspend=n";
```

Since v0.2.5 we can use a built-in jvm_var `pno` to make all JVMs have different debug ports,  `pno` is a dynamic variable and will be increased 
on creating every JVM.
e.g. When `worker_processes` is 8, the debug ports will range from port `2401` to `2408`

```nginx

worker_processes  8;

http {
...
    jvm_options "-Xdebug";
    jvm_options "-Xrunjdwp:server=y,transport=dt_socket,address=240#{pno},suspend=n";
}
```

###Advanced JVM Options for I/O

Check [this section](configuration.html#24-chose--coroutine-based-socket-or-asynchronous-socketchannel-or-thread-pool-for-slow-io-operations) for more deitals about choice and configuration about `thread pool` , `coroutine` based socket or `asynchronous socket/channel`.

###Some Useful Tips

These tips are really useful. Most of them are from real users. Thanks [Rickr Nook](https://github.com/rickr-nook) who give us some useful tips.

1. The number of embed JVMs is the same with Nginx `worker_processes`, so if `worker_processes` > 1 we maybe need [nginx-clojure broadcast API][], shared memory (e.g nginx-clojure built-in [Shared Map][], OpenHFT [Chronicle Map][]) or 
even external service(e.g. redis, database) to  coordinate the state or use cookie based session store to manage session information, e.g. [ring.middleware.session.cookie/cookie-store](https://github.com/mmcgrana/ring/wiki/Sessions).
1. When importing Swing We Must specifiy `jvm_options "-Djava.awt.headless=true"` , otherwise the nginx will hang.
1. By adding the location of your clojure source files to the classpath,then just issue "nginx -s reload" and changes to the sources get picked up!
1. You can remove clojure-1.5.1.jar from class path and point at your "lein uberjar" to pick up a different version of clojure. 
1. To use Java 7 on OSX, in nginx.conf your may set `jvm_path "/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home/jre/lib/server/libjvm.dylib";`

2.2 Initialization Handler for nginx worker
-----------------

You can embed clojure/groovy code in the `http { ` block to do initialization when nginx worker starting. e.g
    
For Clojure

```nginx
http {
......
    jvm_handler_type 'clojure'; 
    # or for external handler  we can use jvm_init_handler_name my.test/InitHandler;
    # below is a clojure example for incline clojure  handler
    jvm_init_handler_code '
	      (fn[ctx]
	        (.println System/err "init2 on http clojure context")
	        {:status 200}
	        )
    '; 
....
}
```

For Java/groovy

```nginx
http {
......
    jvm_handler_type 'java'; # or handler_type 'groovy'
    jvm_init_handler_name 'my.test/InitHandler'; 
....
}
```

```java
	public static class JVMInitHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> ctx) {
			NginxClojureRT.log.info("JVMInitHandler invoked!");
			return null; // or return new Object[] {500, null, null}; for  an error
		}
	}
```


The ring handler can use status 500 and body to report some errors or just return nothing.
For more detail example of ring handler please see the next secion.

Please Keep these in your mind:

* By default if the initialization failed the nginx won't start successfully and the worker will exit after reporting an error message in error log file but the master keep running and take the port.
* Because the maybe more than one nginx worker processes, so this code will run everytime per worker starting. 
* If you use nginx-clojure built-in [Shared Map][] or OpenHFT [Chronicle Map][]  to share data 
among nginx worker processes, Java file lock can be used to let only one nginx worker process do the initialization.
* If you [enabled coroutine support][], nginx maybe will start successfully even if your initialization failed after some socket operations. If you case it, you can 
use `nginx.clojure.core/without-coroutine` to wrap your handler, e.g.

For clojure

```nginx
	    handler_code '
	    (do
		    (use \'nginx.clojure.core)
		    (without-coroutine
		      (fn[ctx]
		        ....
		        )
		    ))
	    ';
```


2.3 Content Ring Handler for Location
-----------------

Within `location` block, 
* Directive `content_handler_type` is used to setting a type of handler.
* Directive `content_handler_code` is used to setting an inline Ring handler.
* Directive `content_handler_name` is used to setting an external Ring handler which is in a certain jar file included by your classpath.
* Directive `content_handler_property` is used to declare one or many properties for content handler which implements interface `nginx.clojure.Configurable`

###2.3.1 Inline Ring Handler

For Clojure : 

```nginx
       location /clojure {
          handler_type 'clojure';
          handler_code ' 
						(fn[req]
						  {
						    :status 200,
						    :headers {"content-type" "text/plain"},
						    :body  "Hello Clojure & Nginx!" ;response body can be string, File or Array/Collection/Seq of them
						    })
          ';
       }
```
Now you can start nginx and access http://localhost:8080/clojure, if some error happens please check error.log file. 

For Groovy :

```nginx
       location /groovy {
          content_handler_type 'groovy';
          content_handler_code ' 
               import nginx.clojure.java.NginxJavaRingHandler;
               import java.util.Map;
               public class HelloGroovy implements NginxJavaRingHandler {
                  public Object[] invoke(Map<String, Object> request){
                     return [200, //http status 200
                             ["Content-Type":"text/html"], //headers map
                             "Hello, Groovy & Nginx!"]; //response body can be string, File or Array/Collection of them
                  }
               }
          ';
       }
```

Now you can start nginx and access http://localhost:8080/groovy, if some error happens please check error.log file. 


###2.3.2 Reference of External Ring Handlers

Please make sure the external Ring handler is in a certain jar file or a directory included by your classpath.
It is also OK if you do not compile the Clojure/Groovy to java class file and just put the source of them in a certain jar file or a directory included by your classpath. 

For Clojure the exteranl Ring handler example is here

```clojure
(ns my.hello)
(defn hello-world [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   ;response body can be string, File or Array/Collection/Seq of them
   :body "Hello World"})

```
Then we can reference it in nginx.conf

```nginx
       location /myClojure {
          content_handler_type 'clojure';
          content_handler_name 'my.hello/hello-world';
       }
```
For more details and more useful examples for [Compojure](https://github.com/weavejester/compojure) which is a small routing library for Ring that allows web applications to be composed of small, independent parts. Please refer to https://github.com/weavejester/compojure

For Java

```java
package mytest;
import static nginx.clojure.MiniConstants.*;

import java.util.HashMap;
import java.util.Map;
public  class Hello implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			return new Object[] { 
					NGX_HTTP_OK, //http status 200
					ArrayMap.create(CONTENT_TYPE, "text/plain"), //headers map
					"Hello, Java & Nginx!"  //response body can be string, File or Array/Collection of them
					};
		}
	}
```
In nginx.conf

```nginx
       location /myJava {
          content_handler_type 'java';
          content_handler_name 'mytest.Hello';
       }
```

For Groovy

```groovy
   package mytest;
   import nginx.clojure.java.NginxJavaRingHandler;
   import java.util.Map;
   public class HelloGroovy implements NginxJavaRingHandler {
      public Object[] invoke(Map<String, Object> request){
         return 
         [200,  //http status 200
          ["Content-Type":"text/html"],//headers map
          "Hello, Groovy & Nginx!" //response body can be string, File or Array/Collection of them
          ]; 
      }
   }
```


You should set your  JAR files or directory to class path, see [2.1 JVM Path , Class Path & Other JVM Options][] .

2.4 Chose  Coroutine based Socket Or Asynchronous Socket/Channel Or Thread Pool for slow I/O operations
-----------------

If the http service should do some slow I/O operations such as access external http service, database, etc.  nginx worker will be blocked by those operations 
and the new  user  request even static file request will be blocked. It really sucks！ Before v0.2.0 the only choice is using thread pool but now we have 
three choice :

1. Coroutine based Socket
	* :smiley:It's Java Socket API Compatible and work well with largely existing java library such as apache http client, mysql jdbc drivers etc.
	* :smiley:It's non-blocking, cheap, fast and let one java main thread be able to handle thousands of connections.
	* :smiley:Your old code **_need not be changed_** and those plain and old java socket based code such as Apache Http Client, MySQL mysql jdbc drivers etc. will be on the fly with epoll/kqueue on Linux/BSD!
	* :worried:You must do some steps to get the right class waving configuration file and set it in the nginx conf file.
1. Asynchronous Client Socket/Channel
	* :smiley:It's the fastest among those three choice and you can controll it finely.
	* :smiley:It can work with default mode or Coroutine based Socket enabled mode but can't work with Thread Pool mode.
	* :worried:Your old code **_must be changed_** to use the event driven pattern.
1. Thread Pool
	* :smiley:It's a trade off choice and almost all Java server such as Jetty, Tomcat , Glassfish etc. use thread pool to handle http requests.
	* :smiley:Your old code **_need not be changed_**.
	* :worried:The nginx worker will be blocked after all threads are exhuasted by slow I/O operations.
	* :worried:Becase the max number of threads is always  more smaller than the total number of socket connections supported by Operation Systems and
thread in java is costlier than coroutine, facing large amount of connections this choice isn't as good as Coroutine based choice.

### 2.4.1 Enable Coroutine based Client Socket

#### 1. Get a User Defined Class Waving Configuration File for Your Web App

* Turn on Run Tool Mode
		
	```nginx
	http {
	...
	
	#To make sure generated Class Waving Configuration File won't be mixed by many workers. 
	worker_processes  1;
	
	#turn on run tool mode, t means Tool
	jvm_options "-javaagent:jars/nginx-clojure-0.2.7.jar=tmb";
	
	#for clojure, you should append clojure core jar, e.g -Djava.class.path=jars/nginx-clojure-0.2.7.jar:mypath-xxx/clojure-1.5.1.jar,please  replace ':' with ';' on windows
  jvm_options "-Xbootclasspath/a:jars/nginx-clojure-0.2.7.jar";
  ...
	}
	```
* Setting Output Path of Waving Configuration File
	
	```nginx
	#Optional The default value is $nginx-workdir/nginx.clojure.wave.CfgToolOutFile
	#Setting Output Path of Waving Configuration File, 
  jvm_options "-Dnginx.clojure.wave.CfgToolOutFile=/tmp/my-wave-cfg.txt";
	```
* Setting Dump Configuration Service

	```nginx
      location /dump {
         content_handler_type 'java';
         content_handler_name 'nginx.clojure.java.WaveConfigurationDumpHandler';       
      }
	```
* Start Nginx which Compiled with Nginx Clojure Module
* Run curl or httpclient based junit tests to access your http services which directly or indirectly use Java Socket API, e.g Apache Http Client, MySQL JDBC Driver etc.
* After All responses completed We'll get a generated class waving configuration file e.g `my-wave-cfg.txt` by access Dump Configuration Service.

	```bash
	/*use curl or just put the Dump Service url to browser and click GO!
	 *Dump Service will generate Waving Configuration File to the path defined by 
	 *java system property `nginx.clojure.wave.CfgToolOutFile`
	 */
	curl -v http://localhost:8080/dump
	```
	
	Don't foget reset `worker_processes` and turn off run tool mode for product enviroument after get class waving configuration
	
#### 2. Enable Coroutine Support

* Turn on Coroutine Support

	```nginx
	http {
	...
	#make sure it is reset to a normal number after  above step, e.g. 8 worker processes.
	worker_processes  8;
			
	#turn on coroutine mode
	jvm_options "-javaagent:jars/nginx-clojure-0.2.7.jar=mb";
	
	#append nginx-clojure &  clojure runtime jars to jvm bootclasspath 		
	#for win32, class path seperator is ";", e.g "-Xbootclasspath/a:jars/nginx-clojure-0.2.7.jar;jars/clojure-1.5.1.jar"
	jvm_options "-Xbootclasspath/a:jars/nginx-clojure-0.2.7.jar:jars/clojure-1.5.1.jar";
	
	#coroutine-udfs is a directory to put your User Defined Class Waving Configuration File
	#for win32, class path seperator is ";", e.g "-Djava.class.path=coroutine-udfs;YOUR_CLASSPATH_HERE"
	#Note: DON NOT put nginx-clojure &  clojure runtime jars here, because they have been appened to the jvm bootclasspath
	jvm_options "-Djava.class.path=coroutine-udfs:YOUR_CLASSPATH_HERE";
	
	
	#copy the waving configuration file generated from previous step to you any classpath dir e.g. coroutine-udfs
	#setting user defined class waving configuration files which are in the above boot classpath, the seperator is "," 
	jvm_options "-Dnginx.clojure.wave.udfs=my-wave-cfg.txt";
	...
	}
	```
	
* restart nginx or reload nginx
	
	Now every nginx worker can handle thousands of connections easily! 
	
	Those plain and old java socket based code such as Apache Http Client, MySQL mysql jdbc drivers etc. will be on the fly with epoll/kqueue on Linux/BSD!
	
	Nginx won't blocked until nginx connections exhuasted or jvm OutOfMemory!

### 2.4.2 Use Asynchronous Client Socket/Channel

Asynchronous Socket/Channel Can be used with default mode or coroutined enabled mode without any additional settings. It just a set of API.
It uses event driven pattern and works with a java callback handler or clojure function for callback. Asynchronous Channel is wrapper of Asynchronous Socket
for more easier usage. More examples can be found from this section [Asynchronous Socket/Channel][]. 


### 2.4.3 Use Thread Pool

If your tasks are often blocked by slow I/O operations, the thread pool method can make the nginx worker not blocked until
all threads are exhuasted. When facing large amount of connections this choice isn't as good as above coroutine based choice or asynchronous socket.

eg.

```nginx

#turn off coroutine mode,  n means do nothing. You can also comment this line to turn off coroutine mode 
jvm_options "-javaagent:jars/nginx-clojure-0.2.7.jar=nmb";

jvm_workers 40;
```
Now Nginx-Clojure will create a thread pool with fixed 40 threads  per JVM instance/Nginx worker to handle requests. If you get more memory, you can set
a bigger number.

2.5 Nginx Rewrite Handler
-----------------

A nginx rewrite handler can be used to set var or return errors before proxy pass or content ring handler. 
If the rewrite handler returns `phase-done` (Clojure) or  `PHASE_DONE` (Groovy/Java), nginx will continue to invoke proxy_pass or 
content ring handler.
If the rewrite handler returns a general response, nginx will send this response to the client and stop to continue to invoke proxy_pass or 
content ring handler.

> **Note:**
All rewrite directives,  such as `rewrite`, `set`,  will be executed after the invocation nginx clojure rewrite handler even if 
they are declared before nginx rewrtite handler.
So the below example maybe is wrong. For more details about Nginx Variable please check this  [nginx tutorial](http://openresty.org/download/agentzh-nginx-tutorials-en.html)  
which explains perfectly the variable scope.

```nginx

       location /myproxy {
          ## It maybe is WRONG!!!
          ## Because execution of directive `set` is after the execution of Nginx-Clojure rewrite handler
          set $myhost "";
          rewrite_handler_type 'clojure';
          rewrite_handler_code ' ....
          ';
          proxy_pass $myhost;
       }    

```

This example is right and there we declare variable $myhost at the outside of `location {` block.

```nginx
       set $myhost "";
       location /myproxy {
          rewrite_handler_type 'clojure';
          rewrite_handler_code ' ....
          ';
          proxy_pass $myhost;
       }    

```

### 2.5.1 Simple Example about Nginx rewrite handler

Here's a simple clojure example for Nginx rewrite handler :

```nginx

       set $myvar "";
       
       location /rewritesimple {
         rewrite_ handler_type 'clojure';
          rewrite_handler_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  (set-ngx-var! req "myvar" "Hello")
						  phase-done))
          ';
          handler_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  (set-ngx-var! req "myvar" 
						             (str (get-ngx-var req "myvar") "," "Xfeep!"))
						  {
						    :status 200,
						    :headers {"content-type" "text/plain"},
						    :body  (get-ngx-var req "myvar") 
						    }))
          ';
       }    

```

### 2.5.2 Simple Dynamic Balancer By Nginx rewrite handler

We can also  use this feature to complete a simple dynamic balancer , e.g.

```nginx

       set $myhost "";
       
       location /myproxy {
          rewrite_handler_type 'clojure';
          rewrite_handler_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  ;compute myhost (upstream name or real host name) based req & remote service, e.g.
						  (let [myhost (compute-myhost req)])
						  (set-ngx-var! req "myhost" myhost)
						  phase-done))
          ';
          proxy_pass $myhost;
       }    

```

The equivalent java code is here

```java

package my.test;

import static nginx.clojure.java.Constants.*;
	
	public static class MyRewriteProxyPassHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> req) {
			String myhost = computeMyHost(req);
			((NginxJavaRequest)req).setNGXVariable("myhost", myhost);
			return PHASE_DONE;
		}
		
		private String computeMyHost(Map<String, Object> req) {
			//compute a upstream name or host name;
		}
	}

```
Then we set the java rewrtite handler in nginx.conf

```nginx

       set $myhost "";
       
       location /myproxy {
          rewrite_handler_type 'java';
          rewrite_handler_name 'my.test.MyRewriteProxyPassHandler';
          proxy_pass $myhost;
       }    

```
	
	
### 2.5.3 Access request BODY in Nginx Rewrite Handler
	
Try `always_read_body on;`  where about the location you want to access the request body in a JAVA rewrite handler.
We also added an example(for unit testing) about this, in nginx.conf

```nginx
     
      set $myup "";

       location /javarewritebybodyproxy {
          always_read_body on;
          rewrite_handler_type 'java';
          rewrite_handler_name 'nginx.clojure.java.RewriteHandlerTestSet4NginxJavaRingHandler$SimpleRewriteByBodyHandler';
          proxy_pass http://$myup;
       }
```

The example java rewrite handler code can be found from https://github.com/nginx-clojure/nginx-clojure/blob/master/test/java/nginx/clojure/java/RewriteHandlerTestSet4NginxJavaRingHandler.java#L35  	
	
	
2.6 Nginx Access Handler
-----------------

Although we can do similar things within a rewrite handler but using Nginx Access Handler will further define roles of all kind of handlers.
Nginx Access Handler will run after Rewrite Handler and before Content Handler (e.g. general content ring handler ,  proxy_pass, etc.).
Access Handler has the same form with Rewrite Handler. When it returns `PHASE_DONE`, nginx will continue the next phase otherwise nginx will response
directly typically with some error information , e.g. `401 Unauthorized`, `403 Forbidden` .
e.g.

```nginx

          location /basicAuth {
               access_handler_type 'java';
	             access_handler_name 'my.BasicAuthHandler';
	             ....
	          }
```

```java

	/**
	 * This is an  example of HTTP basic Authentication.
	 * It will require visitor to input a user name (xfeep) and password (hello!) 
	 * otherwise it will return 401 Unauthorized or BAD USER & PASSWORD 
	 */
	public  class BasicAuthHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			String auth = (String) ((Map)request.get(HEADERS)).get("authorization");
			if (auth == null) {
				return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
						"<HTML><BODY><H1>401 Unauthorized.</H1></BODY></HTML>" };
			}
			String[] up = new String(DatatypeConverter.parseBase64Binary(auth.substring("Basic ".length())), DEFAULT_ENCODING).split(":");
			if (up[0].equals("xfeep") && up[1].equals("hello!")) {
				return PHASE_DONE;
			}
			return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
			"<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" };
		} 
	}
```

2.7 Nginx Header Filter
-----------------

We can use Nginx Header Filter written by  Java/Clojure/Groovy to do some useful things, e.g. 

1.  monitor the time cost of requests processed  
1.  modify the response header dynamically 
1.  write user defined log 

Header Filter Access Handler has the same return form with Rewrite Handler/ Access Handler.
 When it returns `PHASE_DONE`, nginx will continue the next phase otherwise nginx will response
 directly typically with some error information.

For Java/Groovy

```nginx
      location /javafilter {
	          header_filter_type 'java';
	          header_filter_name 'my.AddMoreHeaders';
	           ..................
	          }
```

```java
package my;

import nginx.clojure.java.NginxJavaRingHandler;
import nginx.clojure.java.Constants;

	public  class RemoveAndAddMoreHeaders implements NginxJavaHeaderFilter {
		@Override
		public Object[] doFilter(int status, Map<String, Object> request, Map<String, Object> responseHeaders) {
			responseHeaders.remove("Content-Type");
			responseHeaders.put("Content-Type", "text/html");
			responseHeaders.put("Xfeep-Header", "Hello2!");
			responseHeaders.put("Server", "My-Test-Server");
			return Constants.PHASE_DONE;
		}
	}
```

For Clojure

```nginx
      location /javafilter {
	          header_filter_type 'clojure';
	          header_filter_name 'my/remove-and-add-more-headers';
	           ..................
	          }
```

```clojure
(ns my
  (:use [nginx.clojure.core])
  (:require  [clj-http.client :as client]))
  
(defn remove-and-add-more-headers 
[status request response-headers]
  (dissoc!  response-headers "Content-Type") 
  (assoc!  response-headers "Content-Type"  "text/html")
  (assoc!  response-headers "Xfeep-Header"  "Hello2!")
  (assoc!  response-headers "Server" "My-Test-Server") 
  phase-done)
```

2.8 Nginx Body Filter
-----------------

We can use nginx body filter to change the response body.

* **Java**

A stream faced Java body filter should implement the interface NginxJavaBodyFilter which has this method:
```java
public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast)  throws IOException;
```

For one request this method can be invoked multiple times and at the last time the argument 
	 `isLast` will be true. Note that `bodyChunk` is valid only at its call scope and can
not be stored for later usage. 
The result returned must be an array which has three elements viz. {status, headers, filtered_chunk}.
If `status` is not null `filtered_chunk` will be used as the final chunk. `status` and `headers` will
be ignored when the response headers has been sent already.
`filtered_chunk` can be either of 

1. File, viz. java.io.File
1. String
1. InputStream
1. Array/Iterable, e.g. Array/List/Set of above types

A string faced Java body filter should extends the class StringFacedJavaBodyFilter which has one protected method to be overriden:
```java
protected Object[] doFilter(Map<String, Object> request, String body, boolean isLast) throws IOException
```
This method has the same return value with `NginxJavaBodyFilter.doFilter`.

e.g.

```nginx
location /hello {
  body_filter_type java;
  body_filter_name mytest.StringFacedUppercaseBodyFilter;
}

```

```java
public static class StringFacedUppercaseBodyFilter extends StringFacedJavaBodyFilter {
		@Override
		protected Object[] doFilter(Map<String, Object> request, String body, boolean isLast) throws IOException {
			if (isLast) {
				return new Object[] {200, null, body.toUpperCase()};
			}else {
				return new Object[] {null, null, body.toUpperCase()};
			}
		}
	}
```

* **Clojure**

```nginx
location /hello {
  body_filter_type clojure;
  body_filter_name mytest/uppercase-filter;
}

```

```clojure
(defn uppercase-filter [request body-chunk last?]
  (let [upper-body (.toUpperCase body-chunk)]
      (if last? {:status 200 :body upper-body}
        {:body upper-body})))
```


[nginx-clojure broadcast API]: https://github.com/nginx-clojure/nginx-clojure/issues/38
[Shared Map]: https://nginx-clojure.github.io/sharedmap.html
[Chronicle Map]: https://github.com/OpenHFT/Chronicle-Map
[Asynchronous Socket/Channel]: more.html#36-asynchronous-channel
[2.1 JVM Path , Class Path & Other JVM Options]: configuration.html#21-jvm-path--class-path--other-jvm-options
[enabled coroutine support]: configuration.html#24-chose--coroutine-based-socket-or-asynchronous-socketchannel-or-thread-pool-for-slow-io-operations

Quick Start
=============

Installation
--------------

1. Download the latest binaries release v0.5.2 from [here](https://sourceforge.net/projects/nginx-clojure/files/). 
1. Unzip the zip file downloaded then rename the file `nginx-${os-arc}` to `nginx`, eg. for linux is `nginx-linux-x64`

>If you want to compile it with your own nginx please check [HERE](installation.html)

Configuration
--------------
1. Open conf/nginx.conf file
1. Setting JVM path and class path within `http {` block in  nginx.conf

	```nginx
	### jvm dynamic library path
	### auto or a real path, e,g /usr/lib/jvm/java-8-oracle/jre/lib/amd64/server/libjvm.so
	jvm_path auto;
		
	### Set my app jars and resources, it must include nginx-clojure runtime jar,e.g. nginx-clojure-0.5.0.jar and 
	##  for clojure user clojure runtime jar is also needed.
	### See http://nginx-clojure.github.io/directives.html#jvm_classpath
	jvm_classpath 'libs/*'; #windows user should use ';' as the separator
	
	```
1. Setting Inline Http Service Handler

* **Clojure**

	```nginx
       ##Within `server {` block in nginx.conf
       location /clojure {
          content_handler_type 'clojure';
          content_handler_code ' 
						(fn[req]
						  {
						    :status 200,
						    :headers {"content-type" "text/plain"},
						    :body  "Hello Clojure & Nginx!"
						    })
          ';
       }
	```
	
* **Groovy**

	```nginx
       ##Within `server {` block in nginx.conf
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

* **Java**
	> **Note:**
	So far nginx-clojure has not supported inline java handler, please see the next section to learn how to use an external java handler.
	
1. Setting Compojure Router/External Http Service Handler

* **Clojure**

	```nginx
	##Within `server {` block in nginx.conf
	location / {
	  content_handler_type clojure;
	  content_handler_name 'example/my-app';
	}
	```

	Make sure that the below source is in the classpath.

  ```clojure
  ;;;my_app.clj
  (ns example
    (:require [compojure.core :refer :all]
            [compojure.route :as route]))

  (defroutes my-app
    (GET "/" [] "<h1>Hello World</h1>")
    (route/not-found "<h1>Page not found</h1>"))
  ```


* **Java**

	```nginx
       ##Within `server {` block in nginx.conf
       location /java {
          content_handler_type 'java';
          content_handler_name 'mytest.HelloService';
       }


	```

	Make sure that the class of the below source is in the classpath.
	```java
		package mytest;
		
		import java.util.Map;
		
		import nginx.clojure.java.ArrayMap;
		import nginx.clojure.java.NginxJavaRingHandler;
		import static nginx.clojure.MiniConstants.*;
		
		public  class HelloService implements NginxJavaRingHandler {
		
			@Override
			public Object[] invoke(Map<String, Object> request) {
				return new Object[] { 
						NGX_HTTP_OK, //http status 200
						ArrayMap.create(CONTENT_TYPE, "text/plain"), //headers map
						"Hello, Java & Nginx!"  //response body can be string, File or Array/Collection of string or File
						};
			}
		}
	```

-----------------------------------

> **Note:**
> For more advanced configurations such as enable coroutine based socket, thread pool  etc. Please check them from [HERE](configuration.html).

Start up
--------------


```nginx

$ cd nginx-clojure-0.5.2
$ ./nginx
``` 
If everything is ok, we can access our first http service by this url

```nginx
### For Clojure
http://localhost:8080/clojure

### For Clojure Compojure Router
http://localhost:8080


### For Groovy
http://localhost:8080/groovy

### For Java
http://localhost:8080/java
```

We can check the logs/error.log to see error information.

Reload
--------------

If we change some settings  we can reload the settings without stopping our services.

```nginx
$ ./nginx -s reload
```


Stop
--------------

```nginx
$ ./nginx -s stop
```

Examples
--------------

### [clojure-web-example](https://github.com/nginx-clojure/nginx-clojure/tree/master/example-projects/clojure-web-example)

A basic example about nginx-clojure & clojure web dev. It uses:
* [Compojure](https://github.com/weavejester/compojure) (for uri routing)
* [Hiccup](https://github.com/weavejester/hiccup) (for html rendering)
* [Websocket API](http://nginx-clojure.github.io/more.html#38--sever-side-websocket) & [Sub/Pub API]() (to demo a simple chatroom)
* ring.middleware.reload (for auto-reloading modified namespaces in dev environments)

See it on [github](https://github.com/nginx-clojure/nginx-clojure/tree/master/example-projects/clojure-web-example).


Quick Start
=============

Installation
--------------

1. Download the latest binaries release v0.3.0 from [here](https://sourceforge.net/projects/nginx-clojure/files/). 
1. Unzip the zip file downloaded then rename the file `nginx-${os-arc}` to `nginx`, eg. for linux is `nginx-linux-x64`

>If you want to compile it with your own nginx please check [HERE](installation.html)

Configuration
--------------
1. Open conf/nginx.conf file
1. Setting JVM path and class path within `http {` block in  nginx.conf

	```nginx
	### jvm dynamic library path
	jvm_path '/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so';
	
	### my app jars e.g. clojure-1.5.1.jar , groovy-2.3.4.jar ,etc.
	jvm_var my_other_jars 'my_jar_dir/clojure-1.5.1.jar';
		
	### my app classpath, windows user should use ';' as the separator
	jvm_options "-Djava.class.path=jars/nginx-clojure-0.2.5.jar:#{my_other_jars}";
	```
1. Setting inline Http Service Handler

	For Clojure:
	```nginx

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
	
	For Groovy:
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

For Java:

```nginx

       location /groovy {
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
> For more advanced configurations such as external Http Service Handler,  enable coroutine based socket, thread pool  etc. Please check them from [HERE](configuration.html).

Start up
--------------


```nginx

$ cd nginx-clojure-0.2.6/nginx-1.6.0
$ ./nginx
``` 
If everything is ok, we can access our first http service by this url

```nginx
### For Clojure
http://localhost:8080/clojure

### For Groovy
http://localhost:8080/groovy

### For Java
http://localhost:8080/java
```

We can check the logs/error.log to see error information.

Reload
--------------

If we change some settings  we can reload the settings without stoping our services.

```nginx
$ ./nginx -s reload
```


Stop
--------------

```nginx
$ ./nginx -s stop
```



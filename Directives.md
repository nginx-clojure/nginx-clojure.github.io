# Directives Reference

  * [jvm_path](#jvm_path)
  * [jvm_var](#jvm_var)
  * [jvm_classpath](#jvm_classpath)
  * [jvm_classpath_check](#jvm_classpath_check)
  * [jvm_workers](#jvm_workers)
  * [jvm_options](#jvm_options)
  * [jvm_handler_type](#jvm_handler_type)
  * [jvm_init_handler_name](#jvm_init_handler_name)
  * [jvm_init_handler_code](#jvm_init_handler_code)
  * [jvm_exit_handler_name](#jvm_exit_handler_name)
  * [jvm_exit_handler_code](#jvm_exit_handler_code)
  * [handlers_lazy_init](#handlers_lazy_init)
  * [auto_upgrade_ws](#auto_upgrade_ws)
  * [content_handler_type](#content_handler_type)
  * [content_handler_name](#content_handler_name)
  * [content_handler_code](#content_handler_code)
  * [rewrite_handler_type](#rewrite_handler_type)
  * [rewrite_handler_name](#rewrite_handler_name)
  * [rewrite_handler_code](#rewrite_handler_code)
  * [access_handler_type](#access_handler_type)
  * [access_handler_name](#access_handler_name)
  * [access_handler_code](#access_handler_code)
  * [header_filter_type](#header_filter_type)
  * [header_filter_name](#header_filter_name)
  * [header_filter_code](#header_filter_code)
  * [content_handler_property](#content_handler_property)
  * [rewrite_handler_property](#rewrite_handler_property)
  * [access_handler_property](#access_handler_property)
  * [header_filter_property](#header_filter_property)
  * [always_read_body](#always_read_body)
  * [shared_map](#shared_map)
  * [write_page_size](#write_page_size)


## jvm_path


* **Syntax**:	**jvm_path** auto | *path*;
* **Default**:	—
* **Context**:	http

Defines jvm shared library path. When `auto` is used it will auto-detect jvm path otherwise it should be a real jvm shared library path. e.g. 
* On Windows 32-bit it maybe is `C:/Program Files/Java/jdk1.7.0_25/jre/bin/server/jvm.dll`,
* On MacOSX it maybe is `/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Libraries/libserver.dylib` or `/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home/jre/lib/server/libjvm.dylib`;
* On Ubuntu, it maybe is `/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so`;
* On CentOS 64-bit, it maybe is `/usr/java/jdk1.6.0_45/jre/lib/amd64/server/libjvm.so`;
* On CentOS 32-bit, it maybe is `/usr/java/jdk1.7.0_51/jre/lib/i386/server/libjvm.so`;

## jvm_var

* **Syntax**:	**jvm_var** *name* *value*;
* **Default**:	—
* **Context**:	http
* **repeatable** true

Defines a varaible which can be reused in jvm related directives such as [jvm_var](#jvm_var), [jvm_classpath](#jvm_classpath), [jvm_options](#jvm_options).

e.g.

```nginx
jvm_var myRoot '/opt/javalibs';
jvm_var myAgent '#{myRoot}/agent.jar';
jvm_classpath '#{myAgent}:/opt/anotherLibs/*';
```

## jvm_classpath

* **Syntax**:	**jvm_classpath** *classpaths*;
* **Default**:	—
* **Context**:	http

Defines class paths. When '/*' is used after a directory path all jar files and sub-directories will be used as the jvm classpath. e.g. If /opt/mylibs has below structure.

```
/opt/mylibs/
-----------/a.jar
-----------/b.jar
-----------/classes   (sub-directory)
-----------/resources (sub-directory)
```

And we use below declaration.

```nginx
jvm_classpath /opt/mylibs/*;
## there are two equivalent declarations which use quote mark. It is useful when there are some special chars such as ' ', ';' etc.
## jvm_classpath '/opt/mylibs/*';
## jvm_classpath "/opt/mylibs/*";

```
It is equivalent to 

```nginx
jvm_options '-Djava.class.path=/opt/mylibs/a.jar:/opt/mylibs/b.jar:/opt/mylibs/classes:/opt/mylibs/resources'
```

We can also define serveral class paths by using separator `:` (UNIX like OS) or `;` (Windows), e.g.

```nginx
jvm_classpath /opt/mylibs/*:/opt/my-another-libs/*:/opt/my-resources;
## for windows user
# jvm_classpath c:/mylibs/*;c:/my-another-libs/*;c:/my-resources;
```


## jvm_classpath_check

* **Syntax**:	**jvm_classpath_check** on | off;
* **Default**:	on
* **Context**:	http

Enables/disables checking access permission of jvm classpath. Default is on.

## jvm_options

* **Syntax**:	**jvm_options** *jvm-option*;
* **Default**:	—
* **Context**:	http
* **repeatable** true

Defines one jvm option. e.g.

```nginx
## set initial Java heap size
jvm_options -Xms250m;

## set maximum Java heap size
jvm_options -Xmx1024m;

## set java thread stack size
jvm_options -Xss128k;

## set java system property
jvm_options -Djava.awt.headless=true;
```
 

## jvm_workers

* **Syntax**:	**jvm_var** *threads-num*
* **Default**:	0
* **Context**:	http

Enables thread pool mode and defines the threads number of thread pool. Default is disabled.

## jvm_handler_type

* **Syntax**:	**jvm_handler_type** clojure | java | groovy;
* **Default**:	—
* **Context**:	http

Defines the default handler type it will be inherited by child server/location/nested-location context and it will 
be overwrited by directives such as content_handler_type, access_handler_type, and so on.
When we use jvm init handler/exit handler we must define it. 

## jvm_init_handler_name

* **Syntax**:	**jvm_init_handler_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	http

e.g.

```nginx
jvm_handler_type java;
jvm_init_handler_name com.foo.handlers.MyHelloHandler;

## or for clojure
jvm_handler_type clojure;
jvm_init_handler_name foo.core/MyHelloHandler;
```

## jvm_init_handler_code

* **Syntax**:	**jvm_init_handler_code** *inline-init-handler-code*;
* **Default**:	—
* **Context**:	http
* **repeatable** false

e.g.

```nginx
jvm_handler_type clojure;
jvm_init_handler_code '(fn[_]
                       (do-some-initialization-work)
                       nil)
';
```

## jvm_exit_handler_name

* **Syntax**:	**jvm_exit_handler_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	http



## jvm_exit_handler_code

* **Syntax**:	**jvm_exit_handler_code** *inline-exit-handler-code*;
* **Default**:	—
* **Context**:	http

e.g.

```nginx
jvm_handler_type clojure;
jvm_init_handler_code '(fn[_]
                       (do-some-cleaning-work)
                       nil)
';
```

## handlers_lazy_init

* **Syntax**:	**handlers_lazy_init** on | off;
* **Default**:	off
* **Context**:	http, server, location

When `handlers_lazy_init` is on the related handler instance won't be created until the first related request comes.
be inherited by child server/location/nested-location context.

## auto_upgrade_ws

* **Syntax**:	**auto_upgrade_ws** on | off;
* **Default**:	off
* **Context**:	http, server, location

`auto_upgrade_ws on` is equivalent to 

```java
//NginxHttpServerChannel sc = r.hijack(true);
		
//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
if (!sc.webSocketUpgrade(true)) {
		return null;
}
```

## content_handler_type

* **Syntax**:	**content_handler_type** clojure | java | groovy;
* **Default**:	clojure
* **Context**:	location

## content_handler_name

* **Syntax**:	**content_handler_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	location

Defines an external content handler. e.g.

* **Clojure**

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

* **Java**

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

* **Groovy**

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
In nginx.conf

```nginx
       location /myJava {
          content_handler_type 'groovy';
          content_handler_name 'mytest.HelloGroovy';
       }
```

## content_handler_code

* **Syntax**:	**content_handler_code** *inline-content-handler-code*;
* **Default**:	—
* **Context**:	location

Defines an inline content handler. e.g.

* **Clojure**

```nginx
location /hello {
  content_handler_type clojure;
  content_handler_code '
    (fn[r] {:status 200, {:content-type "text/plain"}, "Hello, Nginx & Clojure!"} )
  ';
}
```

* **Groovy** 
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

## rewrite_handler_type

* **Syntax**:	**rewrite_handler_type** clojure | java | groovy;
* **Default**:	clojure
* **Context**:	location

## rewrite_handler_name

* **Syntax**:	**rewrite_handler_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	location

Specifies a rewrite handler by a full qualified name. A rewrite handler can be used to set Nginx variables or return errors before 
[proxy_pass](https://www.nginx.com/resources/admin-guide/reverse-proxy/) or content ring handler. 
If the rewrite handler returns `phase-done` (Clojure) or  `PHASE_DONE` (Groovy/Java), nginx will continue to invoke proxy_pass or 
content handler. Otherwise if the rewrite handler returns a general response, nginx will send this response to the client and 
stop to continue to invoke proxy_pass or content handler.

> **Note:**
All rewrite directives,  such as `rewrite`, `set`,  will be executed after the invocation rewrite handler even if 
they are declared before nginx rewrtite handler.
So the below example maybe is wrong. For more details about Nginx Variable please check this  
[nginx tutorial](http://openresty.org/download/agentzh-nginx-tutorials-en.html) which explains perfectly the variable scope.

```nginx

    location /myproxy {
          ## It maybe is WRONG!!!
          ## Because execution of directive `set` is after the execution of Nginx-Clojure rewrite handler
          set $myhost "";
          rewrite_handler_type clojure;
          rewrite_handler_name 'myns.handler/my-rewrite-handler';
          proxy_pass $myhost
       }    

```

This example is right and there we declare variable $myhost at the outside of `location {` block.

```nginx

    set $myhost "";
    location /myproxy {
      rewrite_handler_type 'clojure';
      rewrite_handler_code 'myns.handler/my-rewrite-handler';
      proxy_pass $myhost;
    }    

```

```clojure
(ns myns.handler
  (:require [nginx.clojure.core :as ncc]))
(defn my-rewrite-handler[req]
		;compute myhost (upstream name or real host name) based req & remote service, e.g.
	  (let [myhost (compute-myhost req)])
			  (ncc/set-ngx-var! req "myhost" myhost)
			  ncc/phase-done)
```

## rewrite_handler_code

* **Syntax**:	**rewrite_handler_code** *inline-rewrite-handler-code*;
* **Default**:	—
* **Context**:	location

Specifies a rewrite handler by a block of inline code.

e.g.

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

## access_handler_type

* **Syntax**:	**access_handler_type** clojure | java | groovy;
* **Default**:	clojure
* **Context**:	location

## access_handler_name

* **Syntax**:	**access_handler_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	location

Access handler runs after rewrite handler and before content handler (e.g. general ring handler,  proxy_pass, etc.).
Access handler has the same form with rewrite handle. When it returns `phase-done` (Clojure) or  `PHASE_DONE` (Groovy/Java)
, Nginx will continue the next phase otherwise nginx will response directly typically with some error information, 
such as `401 Unauthorized`, `403 Forbidden`, etc.

Here 's an example to implement a simple HTTP Basic Authentication.

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

## access_handler_code

* **Syntax**:	**access_handler_code** *inline-access-handler-code*;
* **Default**:	—
* **Context**:	location

Specifies a access handler by a block of inline code. See [access_handler_name](#access_handler_name).

## header_filter_type

* **Syntax**:	**header_filter_type** clojure | java | groovy;
* **Default**:	clojure
* **Context**:	location

## header_filter_name

* **Syntax**:	**header_filter_name** *full-qualified-name*;
* **Default**:	—
* **Context**:	location

Specifies a header filter by a full qualified name.

We can use header filter to do some useful things, e.g. 

1.  monitor the time cost of requests processed  
1.  modify the response header dynamically 
1.  write user defined log 

Header filter has the same return form with rewrite handler/ access handler.
 When it returns `phase-done` (Clojure) or  `PHASE_DONE` (Groovy/Java), Nginx will 
 continue the next phase otherwise Nginx will response directly typically with some error information.

This example will add more headers to the response.

* **Java/Groovy**

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

* **Clojure**

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

## header_filter_code

* **Syntax**:	**header_filter_code** *inline-header-filter-code*;
* **Default**:	—
* **Context**:	location


Specifies a header filter by a block of inline code. See [header_filter_name](#header_filter_name).

## content_handler_property

* **Syntax**:	**content_handler_property** name value;
* **Default**:	—
* **Context**:	location
* **repeatable** true

Defines one content handler property. All of those content handler properties belonged to one location will be pass to 
the related content handler 's method `config(properties)` if the content handler implements
the interface `nginx.clojure.Configurable`.

## rewrite_handler_property

* **Syntax**:	**rewrite_handler_property** name value;
* **Default**:	—
* **Context**:	location
* **repeatable** true

Defines one rewrite handler property. All of those rewrite handler properties belonged to one location will be pass to 
the related rewrite handler 's method `config(properties)` if the rewrite handler implements
the interface `nginx.clojure.Configurable`.

## access_handler_property

* **Syntax**:	**access_handler_property** name value;
* **Default**:	—
* **Context**:	location
* **repeatable** true

Defines one access handler property. All of those access handler properties belonged to one location will be pass to 
the related access handler 's method `config(properties)` if the access handler implements
the interface `nginx.clojure.Configurable`.

## header_filter_property

* **Syntax**:	**header_filter_property** name value;
* **Default**:	—
* **Context**:	location
* **repeatable** true

Defines one header filter property. All of those header filter properties belonged to one location will be pass to 
the related header filter 's method `config(properties)` if the header filter implements
the interface `nginx.clojure.Configurable`.

## always_read_body

* **Syntax**:	**always_read_body** on | off;
* **Default**:	off
* **Context**:	http, server, location

By default request body will not be read until invoke content handler.
We can try `always_read_body on;` where we want to access the request body in a Java rewrite handler. 
It can be inherited by child server/location/nested-location context.

e.g.

```nginx
   set $myup "";

   location /readBodyProxy {
      always_read_body on;
      rewrite_handler_type 'java';
      rewrite_handler_name 'my.SimpleRewriteByBodyHandler';
      proxy_pass http://$myup;
   }
```

Then we can get the request body in SimpleRewriteByBodyHandler.invoke.

```java
@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest req = (NginxJavaRequest) request;
			InputStream in = (InputStream) req.get(Constants.BODY);
			if (in != null) {
			  //...
			}
		}
```

## shared_map

* **Syntax**:	**shared_map** *name type?arg1=val1&arg2=val2...*;
* **Default**:	—
* **Context**:	http
* **repeatable** true

Shared map is used to share data among nginx worker processes without any other external services
 (e.g. redis, memorycached ) or libraries (e.g. SharedHashMap/Chronicle-Map, nginx lua shared dic). 
So far it has two implementations: tiny map & hash map both of which use MurmurHash3 32-bit to 
generate hash code. The key/value of shared hash map can be `int`,`long`,`String`, `byte array`.

**limitation**

type        | entry structure size(Bytes)| table structure size(Bytes)| space limit |entries limit| key limit| value limit| 
------------ | -----------|-----------|-------------|------------|--------------------|---------------------
tiny map  |24 |entries x 4| 4G (64-bit) or 2G (32-bit)| 2^31=2.14Billions | 16M | 4G (64-bit) or 2G (32-bit) |
hash map  |40(64-bit) or 28(32-bit)|entries x 8(64-bit) or 4 (32-bit)  |OS process memory limit| 2^63 (64-bit) or 2^31 (32-bit)|  OS process memory limit | OS process memory limit  |

But note that if needed memory size is less than half of OS page size the real allocated size of nginx slab only can be 
2^3 = 8, 2^4 = 16, 2^5 = 32,..., 2^(ngx_pagesize_shift - 1).So on 64-bit OS entry structure size of tiny map really
uses 32Bytes hash map uses 64Bytes. We'll do some optimize work in the future versions.

Neither tiny map nor hash map will rehash entries because both of them will have a constant number of buckets once they are initialized. 

```
${number of buckets}  =  round_up_to_power_of_2( ${entries} * 0.75 ) 
```

**Optimization for int/long**

Some optimization have been done about java int/long key/value, int/long key/value and they won't allocate additional memory 
because them are stored in the entry structure itself.
e.g. in a hash map for a java long key (64bits) we will store it into 

1.  entry.key (64-bit OS) or
1.  entry.key & entry.ksize (32-bit OS) because size of java long is constant we can reuse entry.ksize.

viz. use c code 

```c

*((uint64_t *)(void*)&entry->key) = ${64bits java long value};

```

**Example**

Here's an example to use shared map to count uri access times.

In nginx.conf

```nginx
    shared_map uri_access_counters  tinymap?space=1m&entries=8096;
```
* **clojure**

```clojure

;; be friendly to embeded nginx-clojure where we maybe def shared map before server starts
(def dsmap (delay (nginx.clojure.clj.ClojureSharedHashMap. "uri_access_counters")))

;; if uri not exists set 1 otherwise increase its count.
(when (zero? (.putIntIfAbsent @dsmap uri (int 1)))
  (.atomicAddInt @dsmap uri (int 1)))
```

* **Java**

```java
// get the shared map. Mostly we do this in a handler
// (e.g. jvm init handler, content handler) 's constructor.
NginxSharedHashMap smap = NginxSharedHashMap.build("uri_access_counters");

//if uri not exists set 1 otherwise increase its count.
//putIntIfAbsent is a faster version of putIfAbsent for int value
if (smap.putIntIfAbsent(uri, 1) != 0) {
  smap.atomicAddInt(uri, 1);
}
```



## write_page_size

* **Syntax**:	**write_page_size** *size*;
* **Default**:	4k
* **Context**:	location

When using http server channel if our response is very small we can set a smaller write page size for better
performance. e.g.

```nginx
location /small {
   write_page_size 1k;
   ...
}
```

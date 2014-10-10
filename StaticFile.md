Static File Best Practices on Nginx-Clojure 
===================


[Nginx-Clojure][1] make it possible to write HTTP services by Clojure/Java/Groovy on [Nginx][2] which is a free, open-source, high-performance HTTP server and reverse proxy, as well as an IMAP/POP3 proxy server. All benefits from [Nginx][2] can be used together with Nginx-Clojure. This article will discuss static file best practices on Nginx-Clojure.

----------


1. Enable gzip on Text Files
-------------

When we enable gzip on text files, such as .txt, .html, .js, .xml, .css etc, the file content will be compressed before send to the http client. Because text files can get high compress ratio enabling gzip on text files will reduce bandwidth and responses' transfer time.

```nginx
http {

...

gzip on;

#setting what type of content will be compressed
gzip_types text/plain text/css application/x-javascript text/
xml application/xml application/xml+rss text/javascript application/
javascript application/json;

#only compress files whose length is >= 4096
gzip_min_length 4096;

#disable gzip with IE6 which shouldn't receive a compressed response 
gzip_disable msie6;

}
```


> **Note:**
>  Now not only static files but also dynamic content from Clojure/Java/Groovy code will also be compressed if the  condition matches. If we want to only enable gzip on parts of context we can 
only put these gzip related directives into some `location {` blocks.   

The complete reference can be found from [Nginx GZip Module Page](http://nginx.org/en/docs/http/ngx_http_gzip_module.html)


2. Turn on Sendfile
-------------

`sendfile` is a Linux System Call and used to copy data between one file descriptor and another (typically socket descriptor). When we use it to send file to the network because this copying is done directly within the kernel, it is more efficient than the combination of read and write, which would require copying data to and from user space.

```nginx
http {
  ...
  sendfile on;
  tcp_nopush on;
}
```

`tcp_nopush` is only useful  after enabling `sendfile`. It enables or disables the use of the TCP_NOPUSH socket option on FreeBSD or the TCP_CORK socket option on Linux.  Enabling the option allows
 
- sending the response header and the beginning of a file in one packet, on Linux and FreeBSD 4.*;
- sending a file in full packets.

> **Note:**
>  When gzip enabled , the text files which match gzip rules won't be sent by sendfile because they must be compressed before sending.

3. Dynamically Combined files
-------------

Combined files can reduce the number of HTTP requests and speedup response. Of course we can combine files by static way before publishing our web site. But the static way maybe need much more disk space and is a little complex to handle modification time stamp and less flexible.
Dynamically combined files is quite easy with Nginx-Clojure, just return a ring response whose body is a seq of Files. e.g.

```clojure
(defn concat-a-and-b [req]
  ;file-a, file-a are instances of java.io.File
  {:status 200, :body [file-a, file-b], :headers {"Content-Type" "text/html"} })
```

With above example Nginx-Clojure will set the HTTP header`Last-Modified` to the newest modification time stamp of those combined files so  that Nginx can only return headers about 304 Not-Modified instead of the whole content when the browser requests the same file again and the file has not been changed since last access.

> **Note:**
>  When `gzip` & `sendfile`  are enabled, if the content type of combined result matches `gzip` rules combined result will be compressed too, otherwise multiple `sendfile` will be invoked to send those files one by one. 
>  

4. Track Static Html Files by Google Analytics
-------------

It's very useful to know access analyzing about our static files. For better performance we can store the code of  Google Analytics to a static file, e.g. files/ga-code.html.

```html
<html>
	<script type="text/javascript">
		var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl."
				: "http://www.");
		document
				.write(unescape("%3Cscript src='"
						+ gaJsHost
						+ "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
	</script>
	<script type="text/javascript">
		try {
			var pageTracker = _gat._getTracker("UA-XXXXXXX-X");
			pageTracker._trackPageview();
		} catch (err) {
		}
	</script>
</html>
```

The we can use dynamically combined files mentioned in the previous chapter to append the content to the feet or insert the content to the header.

```clojure

(defn ga-in-feeter [req]
 {:status 200,
  :headers {"Content-Type" "text/html"},
  ;;suppose the uri is prefixed with "/testfiles/"
  ;;and we do nothing about security for this simple example
  :body [(clojure.java.io/file "files/" (subs (:uri req) (.length "/testfiles/"))), 
         (clojure.java.io/file "files/ga-code.html")]})
```

If you have already got a ring handler which can handle some static html files we can wrap the handler by 

```clojure
(defn ga-wrapper 
"f is a ring handler"
[f]
(fn [req]
  (let [{:keys [status body headers] :as result} (f req)]
    (if body
      {:status status, :body [body, ga-code-file], :headers headers
      result}))))

```

5. Limit Download Speed
-------------

`limit_rate` can be used to limit download speed, e.g. For those uris are prefixed with `/downloads/`  files limit the download speed up to 256kB/s. 

```nginx

location /downlaods/ {
  ....
  limit_rate 256k;
}

```

If we want to set different download speed for different users, we can use rewrite handler and `set-ngx-var!` to set `limit_rate` dynamically for different users.   e.g.  VIPs get 500kB/s speed and the others get 100kB/s speed. 

```clojure
(ns my-simple-limiter
  (:use [nginx.clojure.core]))
(defn speed-limiter [req]
  (if (= "VIP" (compute-user-role req))
    (set-ngx-var! req "limit_rate" "500k")
    (set-ngx-var! req "limit_rate" "100k"))
  phrase-done)
```

```nginx

location /downlaods/ {
     ....
     handler_type 'clojure';
     rewrite_handler_name 'my-simple-limiter/speed-limiter';  
}
```



  [1]: http://nginx-clojure.github.io/
  [2]: http://nginx.org/

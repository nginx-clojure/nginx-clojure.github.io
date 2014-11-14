1. Installation
=============

The lastest release is 0.2.7. Please check the  [Update History](HISTORY.md) for more details.

1.1 Installation by Binary
-------------

1. First you can download  Release 0.2.7  from [here](https://sourceforge.net/projects/nginx-clojure/files/). 
The zip file includes Nginx-Clojure binaries about Linux x64, Linux i586, Win32, Win64 and Mac OS X.
1. Unzip the zip file downloaded then rename the file `nginx-${os-arc}` to `nginx`, eg. for linux is `nginx-linux-x64`


1.2 Installation by Source
-------------

Nginx-Clojure may be compiled successfully on Linux x64, Linux x86 32bit, Win32, Win64 and Mac OS X x64.

1. First download from [nginx site](http://nginx.org/en/download.html) or check out nginx source by hg from http://hg.nginx.org/nginx. 
For Win32 users MUST check out nginx source by hg because the zipped source doesn't contain Win32 related code.
1. Check out Nginx-Clojure source from github OR download the zipped source code from https://github.com/xfeep/nginx-clojure/releases
1. If you want to use Http SSL module, you should install openssl and openssl dev first.
1. Setting Java header include path in nginx-clojure/src/c/config

	```nginx
	#eg. on ubuntu
	JNI_HEADER_1="/usr/lib/jvm/java-7-oracle/include"
	JNI_HEADER_2="${JNI_HEADER_1}/linux"
	````
1. Add Nginx-Clojure module to Nginx configure command, here is a simplest example without more details about [InstallOptions](http://wiki.nginx.org/InstallOptions)

	```bash
	#If nginx source is checked out from hg, please replace ./configure with auto/configure
	$./configure \
		--add-module=nginx-clojure/src/c
	$ make
	$ make install
	```
1. Create the jar file about Nginx-Clojure

	Please check the lein version `lein version`, it should be at least 2.0.0.

	```bash
	$ cd nginx-clojure
	$ lein jar
	```
	Then you'll find nginx-clojure-${version}.jar (eg. nginx-clojure-0.2.7.jar) in the target folder. 
	The jar file is self contained. If your project use clojure  it naturally depends on the clojure core jar, e.g clojure-1.5.1.jar.
	If your project use groovy it naturally depends on the groovy runtime jar, e.g. groovy-2.3.4.jar.

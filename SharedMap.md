Shared Map & Session Store
=============

## Shared Map

Shared map is used to share data among nginx worker processes without any other external services
 (e.g. redis, memorycached ) or libraries (e.g. SharedHashMap/Chronicle-Map, nginx lua shared dic). 
So far it has two implementations: tiny map & hash map both of which use MurmurHash3 32-bit to 
generate hash code. The key/value of shared hash map can be `int`,`long`,`String`, `byte array`.

**limitation**

type        | entry structure size(Bytes)| table structure size(Bytes)| space limit |entries limit| key limit| value limit| 
------------ | -----------|-----------|-------------|------------|--------------------|---------------------
tiny map  |24 |entries x 4| 4G or 2G (32-bit)| 2^31=2.14Billions | 16M | 4G or 2G (32-bit) |
hash map  |40 or 28(32bit)|entries x 8 or 4 (32-bit)  |OS limit| 2^63 or 2^31 (32-bit)|  OS limit | OS limit  |

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

## Shared Map Based Ring Session Store

When worker_processes  > 1 in nginx.conf, we can not use the default in-memory session store
because there're more than one JVM instances and requests from the same session perhaps
will be handled by different JVM instances. We can try cookie store, or shared map based ring session store
and if we use redis to shared sessions we can try [carmine-store](https://github.com/ptaoussanis/carmine) or 
[redis session store](https://github.com/wuzhe/clj-redis-session).
Create a shared map based ring session store is very simple. e.g.

In nginx.conf

```nginx
shared_map my-session-store tinymap?space=10m&entries=1024;
```

* **Clojure**

```clojure
(def nginx.clojure.session/my-session-store (shared-map-store "my-session-store"))
```



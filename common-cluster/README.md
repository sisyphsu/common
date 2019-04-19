# common-cluster

Provider some useful features for distributed cluster system.

# Maven

You can add this library like this:

```xml
<dependency>
    <groupId>com.github.sisyphsu</groupId>
    <artifactId>common-cluster</artifactId>
    <version>1.0.4</version>
</dependency>
```

# Configuration

This library use `zookeeper` and `redis` as datasource:

```yaml
spring:

  zookeeper:
    addr: localhost:2181

  redis:
    host: localhost
    port: 6379
```

# Features

- **ClusterID**: allocate and lock one unrepeated global nodeID for the current node, which based on **ZooKeeper**.
- **DistributedLock**: provide multi-key's distributed lock implementation, which based on **Redis**.
- **SnowFlakeID**: provide an simple and efficient SnowFlakeID implementation, which based on **ClusterID**.
- **TickID**: provide another distributed incremental ID generator, which based on **ZooKeeper** or **Redis**.

## ClusterID

Compete and lock an unique global `ID` for every cluster's member, which based on **ZooKeeper**.

You can specify the `bitNum` of `ClusterID` based on your cluster size, The final `ID` will be distributed between `1<<0` and `1<<bitNum`. Remember to leave room for expansion and backup, because `ClusterID` will loop through to find the `coldest` ID.

The default `bitNum` was `8`, which means the `ClusterID` could be `[0, 256)`.

The `ClusterID` has three status:

- **NONE**: Didn't lock any ID, maybe starting or lost lock after reconnection.
- **LOCK**: Success, `ClusterID#get()` will return the unique id.
- **UNLOCK**: Has an unique id, but lose connection to `ZooKeeper`, the lock may be token by other node.

Example:

```java
public class ClusterIDTest extends SpringBaseTest {

    @Autowired
    private ClusterID clusterID;

    @Test
    public void testID() {
        log.info("clusterID: {}", clusterID.get());
    }

}
```

Notice: `ClusterID#get()` which will block if not ready.

## DistributedLock

This is a multi-key's distributed lock implementation, based on `Redis`'s `eval` and `pubsub` commands. 

The biggest feature is support for multiple keys, which is useful for batch operation or some complex logic.

For example, during some trade operation, one service need to lock `userId=1001` and `orderId=100000001` to prevent others modify the same data, it can do like this:

```java
public class DistributedLockTest extends SpringBaseTest {

    @Autowired
    private DistributedLock dlock;

    @Test
    public void runInTradeLock() throws Exception {
        dlock.runInLock(Arrays.asList("user:1001", "order:100000001"), () -> {
            System.out.println("do business");
        });
    }

    @Test
    public void runInTradeLock2() throws Exception {
        List<String> keys = Arrays.asList("user:1001", "order:100000001");
        if (!dlock.lock(keys, 3000)) {
            throw new TimeoutException("lock failed");
        }
        try {
            System.out.println("do business");
        } finally {
            dlock.unlock(keys);
        }
    }
}
```

The `DistributedLock` will try lock all keys, if not success, it will wait other's `unlock` notify and then retry lock until success or timeout.

## SnowFlakeID

What is `SnowFlake`? [check this](https://github.com/twitter-archive/snowflake).

Use this `SnowFlakeID` implementation, you can specified the bitNum of `timestamp`, `workId`, `sequenceId` by your business.

This `SnowFlakeId` dependent on `ClusterID`, because the `ClusterID` is an `interface`, so you can custom it easily.

And the `timestamp` is based on `2017-01-01 00:00:00 GMT+0800` to save 1 or 2 bit.

By default, the bitNum was configured like this:

- **timestamp**: 39 bits, support 2017 ~ 2051
- **workId**: 8 bits, support 256 nodes
- **sequence**: 6 bits, can generate 64 ids every millisecond and every node 

Why so small? 

1. Don't need too many ids, `64000` qps was enough for one single node.
2. Don't want id too big, `53-bits` was shorter (`1187276660375616`), which is compatible with `JavaScript`.

You can also specified different `bitNum` like this: 

```java
@Slf4j
public class SnowFlakeIDTest {

    @Test
    public void testOne() {
        int timestampBitNum = 40;
        int nodeBitNum = 10;
        int sequenceBitNum = 8;
        SnowFlakeID flakeID = new SnowFlakeID(new ClusterID() {
            @Override
            public int getBitNum() {
                return nodeBitNum;
            }

            @Override
            public int get() {
                return 1;
            }

            @Override
            public ClusterIDStatus getStatus() {
                return ClusterIDStatus.LOCK;
            }
        }, timestampBitNum, sequenceBitNum);

        System.out.println(flakeID.generate());
    }
    
}
```

## TickID

This is an another distributed incremental id solution.

Sometimes, we want some smaller id like `11872766` or `1187276660`, but not bigger id like `1187276660375616`.

In this case, `SnowFlakeID` is not suitable, because it based on `timestamp` and very large.

`TickID` was designed to solve this problem, it can generate smaller incremental id in distributed system.

It use `ZooKeeper` or `Redis` to persist global counter, and maintain 2 TokenPool, one for allocating id, another one for preparing.

You can use it like this:

```java
@Slf4j
public class TickIDTest {

    @Autowired
    private TickTemplate template;

    @Test
    public void testNormal() {
        TickID tickID = template.createTickID("user_id", 100);
        System.out.println(tickID.generate());
        tickID.close();
    }

}
```

# Notice

- `Curator` version must match your `ZooKeeper` version, [click for detail](http://curator.apache.org/zk-compatibility.html).

# License

MIT
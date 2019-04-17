# common

Some useful library for Java & SpringBootimplementation.

## common-cluster

Provider some useful features for distributed cluster system, include:

- **ClusterID**: allocate and lock one unrepeated global nodeID for the current node, which based on **ZooKeeper**.
- **DistributedLock**: provide multi-key's distributed lock implementation, which based on **Redis**.
- **SnowFlakeID**: provide an simple and efficient SnowFlakeID implementation, which based on **ClusterID**.
- **TickID**: provide another distributed incremental ID generator, which based on **ZooKeeper** or **Redis**.

## TODO
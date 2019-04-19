# common

Some useful library for Java & SpringBoot implementation.

## common-cluster

Provider some useful features for distributed cluster system, include:

- **ClusterID**: allocate and lock one unrepeated global nodeID for the current node, which based on **ZooKeeper**.
- **DistributedLock**: provide multi-key's distributed lock implementation, which based on **Redis**.
- **SnowFlakeID**: provide an simple and efficient SnowFlakeID implementation, which based on **ClusterID**.
- **TickID**: provide another distributed incremental ID generator, which based on **ZooKeeper** or **Redis**.

**[Click For Detail](./common-cluster)**

**[点击查看中文版文档](https://sulin.me/2019/17G1YB6.html)**

# License

MIT
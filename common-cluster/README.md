# 分布式相关组件



## 集群ClusterID

通过ZooKeeper为当前节点分配全局唯一ID，分配策略为环形遍历，支持通过比特位数量配置ClusterID的范围值。

比如默认8bit的有效值为[0,256)

## SnowFlakeID

可以基于已有的ClusterID，生成全局唯一的64bit趋势递增ID，具体算法策略支持自定义。

## TickID

此算法有别于SnowFlakeID，它不使用ClusterID及Timestamp计算递增ID，而是直接通过分布式竞争ID片段实现全局唯一且趋势递增的ID。

直观的区别就是，此算法产生的ID长度较短，对于ID长度敏感的业务场景比较适合。

比如OrderID可以是15~18位的长整数，采用SnowFlakeID非常适合。

然而PostID等社交应用场景更倾向于8~12位的长整数，采用TickID非常适合。

## 分布式锁

基于Redis实现的分布式锁，特点为支持多Key。

比如某个业务依赖[user:1001, order:100000001], 且需要在执行过程中锁定这两个资源，则可以直接通过此分布式锁实现。

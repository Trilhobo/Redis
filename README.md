# Redis 学习笔记  
### 1、什么是Redis  
    redis是一种基于键值对（key - value）的NoSQL数据库

### 2、Redis 有哪些数据结构？  
    五种基本的数据结构 : String、Hash、List、Set、ZSet
    中级玩家 : BitMap、HyperLogLog、Geo
    高级玩家之Redis Module : BloomFilter、RedisSearch、Redis-ML、JSON

### 3、请说说redis的线程模型？  
    Redis是非阻塞IO,多路复用。内部使用的是文件事件处理器file event handler,因为文件事件处理器是单线程的，所以基于它的Redis也是单线程模型。

![线程模型图](http://static2.iocoder.cn/images/Redis/2019_11_22/01.png)      
### 4、一个完整的redis通信过程  
    * 客户端Socket01向Redis的Server Socket请求建立连接，此时Server Socket会产生一个AE_READABLE事件  
    * IO多路复用程序监听到该事件后,压入队列之中。  
    * 文件事件分派器会从队列中获取事件，交给连接应答处理器。  
    * 连接应答处理器会创建一个可以与客户端通信的Socket01，并且将Socket01的AE_READABLE事件与命令请求处理器做关联  
    * 假设客户端现在发送一个set命令  
    * 此时Redis中的Socket 01会产生一个AE_READABLE事件，IO多路复用程序将之压入队列  
    * 事件分派器从队列中获取到该事件，由于前面 Socket01 的 AE_READABLE 事件已经与命令请求处理器关联，因此事件分派器将事件交给命令请求处理器来处理  
    * 命令处理器在内存中完成设值，它会将 Scket01 的 AE_WRITABLE 事件与令回复处理器关联  
    * 如果此时客户端准备好接收返回结果了，那么 Redis 中的 Socket01 会产生一个 AE_WRITABLE 事件，同样压入队列中，事件分派器找到相关联的命令回复处理器，由命令回复处理器对 socket01 输入本次操作的一个结果，比如 ok，之后解除 Socket01 的 AE_WRITABLE 事件与命令回复处理器的关联。  
      
### 5、为什么 Redis 单线程模型也能效率这么高？  
    1、C语言实现，众所周知C很快  
    2、纯内存操作  
    3、基于非阻塞的IO多路复用机制  
    4、单线程，避免了多线程频繁切换上下文的消耗  
    
### 6、什么是 Redis 事务？  
    事务 : 事务表示一组动作要么全部执行，要么全部不执行。  
    Redis的事务 : Redis提供了简单的事务功能，只是满足事务的隔离性，而不具备原子性。  
    
    事务相关的命令 : multi (表示事务开始)、exec（表示事务结束）、discard（取消事务）、watch（监控key）  

### 7、 如何实现 Redis CAS 操作  
    CAS: 对CAS的理解，CAS是一种无锁算法，CAS有3个操作数，内存值V，旧的预期值A，要修改的新值B。当且仅当预期值A和内存值V相同时，将内存值V修改为B，否则什么都不做。  
    
    10.185.0.120:6932> get test  
    "89"  
    10.185.0.120:6932> wacth test  
    OK  
    10.185.0.120:6932> multi  
    OK  
    10.185.0.120:6932> incr test  
    QUEUED  
    10.185.0.120:6932> exec  
    1) (integer) 90  
    
    注意: watch操作执行之后，multi之外任何操作都可以认为是other clinet在操作（即使仍然是在同一个客户端上操作），exec该事务也仍旧会失败.  
    
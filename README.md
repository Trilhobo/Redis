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
    
### 8、修改配置不重启 Redis 会实时生效吗？  
    CONFIG SET 命令可以动态地调整 Redis 服务器的配置(configuration)而无须重启。  
    例:config set parameter value  
    具体支持的参数，可通过config get * 获得  
    
### 9、Redis 有几种持久化方式？  
    Redis提供了两种持久化方式，分别是RDB和AOF。  
    
### 10、RDB分析  
#####    触发机制:   
    1、手动触发：save 和 bgsave 命令，bgsave是对save命令的优化    
                bgsave命令: Redis进程执行fork操作创建出子进程，RDB持久化交由子进程负责，完成后自动结束。  
                
    2、自动触发: -使用save m n配置，表示在m秒钟数据集存在n次修改  
                -如果存在从节点进行全量复制时，主节点自动执行bgsave生成RDB文件发给从节点  
                -执行debug reload命令重新加载redis，也会自动触发save操作  
                -默认情况下执行shutdowm命令时，如果没有开启AOF持久化功能则自动执行bgsave  
          
#####    RDB文件的处理    
    1、保存： RDB文件保存在dir配置指定的目录下，可以通过config set dir {newDir} 和 config set dbfilename {newFileName}运行期动态执行  
    2、校验： 如果Redis加载损坏的RDB文件是拒绝启动，可以使用redis-check-dump工具检测RDB文件并获取对应的错误报告  

#####   RDB流程说明  
   ![bgsave命令的运作流程](https://oscimg.oschina.net/oscnet/up-1e1f478e6e8242e4b22f91baef514c01929.JPEG)

#####    快照  
    调用fork()函数创建一个子进程(相当于当前进程的一个副本)，然后持久化的操作全都交给子进程来执行。父进程还是继续给客户端提供服务，这样一来万一父进程有了写的操作，就会影响到子进程的持久化。这时候想到的一个方案就是在创建子进程的时候，copy一份所有的数据给子进程。但是如果修改的内容不多，那么内存中就会存在大部分的重复冗余数据，实在是有点麻瓜。所以redis采用了COW（copy on write）技术,父进程和子进程共享内存空间，fork函数时，将父进程所有的内存页设置为read only,当父进程写内存的时候，CPU检测到内存页A（假设要写的内存页是A）是read only，触发页异常中断（page-fault），然后会单独创建一个页面A-copy（private page）给父进程，这样父子进程就各持有一份。子进程就能保证持久化的数据，是进程被创建是的那个数据快照。  
    [参考链接](https://juejin.im/post/5bd96bcaf265da396b72f855)
                
#####    RDB优点
    1、灵活设置备份频率和周期    
    2、RDB恢复数据远远快于AOF方式  
    3、性能最大化。对于Redis服务进程来说，持久化要做的只是fork出子进程。RDB对Redis对外提供读写服务的影响很小，可以保持高性能  
    
#####    RDB缺点  
    1、RDB方式数据没有办法做到实施持久化/秒级持久化。应为bgsave每次运行都要执行都要进行fork操作创建子进程，属于重量级操作，频繁执行成本过高。  

### 11、AOF    
#####   概念  
    以独立日志的方式记录每次写命令，重启时再重新执行AOF文件中的命令达到恢复数据的目的。AOF的主要用作是解决了数据持久化的实时性   
#####   工作流程
    1、命令写入（append）：所有的写入命令会追加到aof_buf（缓冲区中）  
    2、文件同步（sync）： AOF缓冲区根据对应的策略向硬盘做同步操作（appendfsync参数三种策略：always、everysec、no）  
    3、文件重写（rewrite）： 随着AOF日志越来越大，需要定期对AOF日志进行重写达到压缩的目的（bgrewriteaof手动触发，或者根据配置auto-aof_rewrite-min-size 和 auto-aof-rewrite-percentage参数确定自动触发时机）    
    4、重启加载（load）：当Redis服务器重启时，可以加载AOF文件进行数据恢复    
#####   优点
    1、AOF可以带来更高的数据安全性，即数据持久性  
#####   缺点
    1、相同的数据集的情况下，AOF文件通常要大于RDB文件
    2、AOF的运行效率往往要低于RDB模式
    3、数据恢复的速度要低于RDB    
    
### 12、 Pipeline
    1、Redis客户端执行一条命令分为四个过程：发送命令，命令排队，命令执行，返回结果。这一个流程称为Round Trip Time（RTT，往返时间）  
    2、Redis提供了批量操作命令（如mget，mset等），有效的节约了RTT。但大部分命令都不支持批量，假如要支持n次hgetall操作，那么就会增加往返的消耗  
       Pipeline它可以将一组Redis命令进行组装，通过一次RTT传给Redis  
          
### 13、Redis有几种数据过期策略？
    1、惰性删除：当客户端读取带有过期属性的key时，如果该key已经过期就执行删除操作返回空  
    2、定时任务删除：redis内部维护一个定时任务，默认每秒运行10次（可通过配置hz控制）  
           
### 14、内存溢出控制策略
    1、当Redis所用内存达到maxmemory上限时会触发相应的一处控制策略。  
##### 六种策略  
    1、noevication：默认策略，不会删除任何数据，拒绝所有写入操作并返回客户端错误信息OOM
    2、volatile-lru: 根据LRU算法删除设置了超时属性的键  
    3、allkeys-lru: 根据LRU算法删除键，不管数据有没有超时属性    
    4、volatile-random： 随机删除过期键    
    5、allkeys-random: 随机删除所有键    
    6、volatile-ttl： 根据键值对象的ttl属性，删除最近将要过期数据。  
    7、redis 4.0 之后增加了两个策略： volatile-lfu 、 allkeys-lfu            
              
### 15、什么是主从同步？  
    1、Redis的主从同步机制，允许Slave从Master那里，通过网络传输拷贝到完整的数据备份，从而达到主从机制。  
    2、主数据库可以进行读写操作，当发生写操作的时候自动将数据同步到从数据库，一般从数据库都是只读，并接收主数据库同步过来的数据。  
    3、第一次同步时，主节点做一次 bgsave 操作，并同时将后续修改操作记录到内存 buffer ，待完成后将 RDB 文件全量同步到复制节点，复制节点接受完成后将 RDB 镜像加载到内存。加载完成后，再通知主节点将期间修改的操作记录同步到复制节点进行重放就完成了同步过程。  
    
### 16、简单的主从配置步骤
    1、cp redis.conf redis6380.conf (复制一份端口位6380的配置文件)  
    2、vim redis6380.conf  
    3、修改port 6379 位 port 6380  
    4、修改pidfile/var/run/redis_6379.pid 为 pidfile/var/run/redis_6380.pid  
    5、在下面新增一条slaveof 192.168.32.88 6379  （192.168.32.88 为测试虚拟机的ip）
    6、客户端连接6379新增一个变量，然后再6380中校验。简单的主从就配置完成了

 
### 17、 Redis集群有哪些方案？  
    1、Redis Sentinel  
    2、Redis Cluster  
    ...  
    
### 18、Redis Sentinel  
    1、Redis Sentinel是一个分布式架构，其中包括若干个Sentinel节点和Redis数据节点  
    
![Sentinel架构](https://oscimg.oschina.net/oscnet/up-ff70e1012a355665ff9ec4cba0ab49a6b5f.png) 
#### 实现原理  
##### 三个定时任务  
    1、每隔10s，每个Sentinel节点会向主节点和从节点发送info命令获取最新的拓补图。  
    2、每隔2s，每隔Sentinel节点会向Redis数据节点的_sentinel_:hello频道发送该Sentinel节点对于主节点的判断以及Sentinel节点的信息。目的时发现新的Sentinel节点和作为客观下线以及领导者选举的依据。  
    3、每隔1s，每隔Sentinel节点向主节点，从节点，其余Sentinel节点发送ping命令来确认节点是否可达。  
    
##### 主观下线和客观下线
    1、主观下线 : 每隔1s，每隔Sentinel节点向主节点，从节点，其余Sentinel节点发送ping命令。当这些节点超过down-after-millseconds没有进行有效回复，Sentinel节点会对该节点做失败判定，这种行为被称为主观下线。  
    2、客观下线 ：当Sentinel主观下线的节点是主节点时，Sentinel节点会通过sentinel is-master-down-by-addr命令向其他Sentinel节点询问判断，当超过<quorum>个数时，Sentinel认为该主节点确实存在问题，判定为客观下线。  

##### 领导者Sentinel节点的选举
    1、当判定为客观下线后，Sentinel节点之间会选举出一个Sentinel节点作为领导者进行故障转移的工作。Redis使用了Raft算法实现领导者选举。  
    
##### 故障转移
    1、在从节点列表中选出一个节点作为新的主节点  
    2、Sentinel领导者对选举出来的从节点执行slaveof no one命令，让它称为新主节点  
    3、Sentinel领导者对其他的从节点发送命令，让他们称为新主节点的从节点，复制规则和parallel-syncs有关。（parallel-syncs控制同时有多少个从节点进行复制，最小为1.数字过大会造成主节点过多的IO消耗）  
    4、Sentinel节点集合会将原来的主节点更新为从节点，并且当它恢复的时候，命令它去复制新的主节点  
        
    
### 19、如何使用Redis实现消息队列
    1、使用list结构作为队列，rpush生产消息，lpop消费消息，当lpop没有消息的时候，适当的sleep一会再试。  
    2、如果对方追问可不可以不用 sleep 呢？list 还有个指令叫 blpop ，在没有消息的时候，它会阻塞住直到消息到来。  
    3、如果对方追问能不能生产一次消费多次呢？使用 pub / sub 主题订阅者模式，可以实现 1:N 的消息队列。  
    4、如果对方追问 pub / sub 有什么缺点？在消费者下线的情况下，生产的消息会丢失，得使用专业的消息队列如 rabbitmq 等。  
    5、如果对方追问 redis 如何实现延时队列？我估计现在你很想把面试官一棒打死如果你手上有一根棒球棍的话，怎么问的这么详细。但是你很克制，然后神态自若的回答道：使用 sortedset ，拿时间戳作为 score ，消息内容作为 key 调用 zadd 来生产消息，消费者用 zrangebyscore 指令获取 N 秒之前的数据轮询进行处理  
 
### 20、 Redis使用场景
    1、数据缓存  
    2、会话缓存  
    3、时效性数据  
    4、访问频率  
    5、计数器  
    6、社交列表  
    7、热门列表与排行榜  
    8、最新动态 
    9、消息队列  
    10、分布式锁 
    
### 21、 Redis集群-Cluster  
   [Redis-Cluster伪集群搭建](https://my.oschina.net/u/4055223/blog/3144545)
 
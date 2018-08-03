# SSM项目整合实战训练

+ Java高并发秒杀API项目--[慕课网免费课程](http://www.imooc.com/course/programdetail/pid/59)
+ 博客地址：http://yiminzheng.coding.me

项目效果图：
秒杀商品列表页：
![列表页](https://i.imgur.com/uTQeUnL.jpg)

商品详情页：
![详情页](https://i.imgur.com/XXMJi2v.jpg)

已登录账号的秒杀已结束：
![秒杀已结束](https://i.imgur.com/rYhOzPl.jpg)

cookie值保存：
![cookie值](https://i.imgur.com/AJnhrrC.jpg)

手机号判断：
![手机号判断](https://i.imgur.com/HWrysKP.jpg)

### day01--DAO层
+ 在IDEA创建Maven项目，在`web.xml`配置servlet版本，在`pom.xml`配置依赖，补全maven项目结构；
+ 在MySQL创建两张表，秒杀商品库存表-`Seckill`，主键是`seckillid`库存商品id和秒杀成功明细表`Successkilled`联合主键商品id和手机号；
+ 在java端根据数据库中表建立对应实体类，并分别创建DAO接口，库存表实体类对应dao接口提供减库存、查询商品的方法接口，明细表对应的dao接口提供插入明细、查询成功秒杀实体并携带商品对象；
+ 基于MyBatis实现dao接口，从两方面：mapper自动实现dao接口并用xml提供sql语句；
+ 配置mybatis全局配置文件`mybatis-congif`，比如表中属性对应的别名，驼峰命名法；
+ 将mybatis和spring整合，创建`spring-dao.xml`配置文件，按顺序配置：数据库->数据库连接池-->mybatis核心sqlsessionfactory-->动态实现dao接口；

## day02--service层
+ DAO层提供接口设计和sql语句，service层提供业务逻辑拼接；
+ 创建`seckillservice`接口，提供四个方法的接口，两个查询商品的方法，第三个是提供秒杀地址接口暴露的方法，第四个是执行秒杀操作返回秒杀结果的方法；
+ 因为方法3，4中数据在web层和service层之间传输，我们再创建dto包，且存在异常exception包存放相应的java类；
+ 实现`service`接口，方法1，2通过调用dao层实体方法得到数据库数据，方法3根据详情页时间判断是否给出接口暴露，url地址经过md5的加密，方法4用到方法3的接口地址，执行事务操作；
+ 方法4返回数据字典常量的结果，为了代码美好，提供枚举类封装结果；
+ 然后将service托管到spring，配置xml文件加载第三方类库，包扫描`package-scan`带注解的java类注入到spring容器中；
+ 因为方法4中执行秒杀操作是对库存的操作，减库存和插入明细是完整事务，这里用spring声明式事务；

## day03--web层
+ 提供前端页面编写--bootstrap+jQuery
+ 前端页面交互流程
  列表页
![列表页](https://i.imgur.com/m52mihq.jpg)

  详情页
![详情页](https://i.imgur.com/x1jOXSR.jpg)

+ 根据springmvc整合spring，springmvc围绕handler开发，这里就是我们编写的`seckillcontroller`类,根据业务service接口中方法开发；
+ 方法1，2获取列表页，获取详情页，方法3，4通过ajax请求返回json类型数据，为了统一返回数据类型，新建`seckillresult`存放json封装数据；
+ controller类中每一个方法对应系统中的一个资源url，在springmvc中，映射器，适配器都默认是注解形式，满足restful接口规范；
+ 前端页面开发基于bootstrap框架，详见课程视频内容；

## day04--并发优化
秒杀系统业务核心--库存问题；秒杀系统热点难点问题--竞争
+ 并发性上不去是因为多线程同时访问一行数据时，产生了事务，执行时会写锁，事务完成或回滚才会释放锁，这导致线程排队；
 
 秒杀系统高并发发生节点：
![高并发发生](https://i.imgur.com/eyeyj1r.jpg)

+ 详情页的高并发分析：为了争夺商品，用户会提前进入列表页或详情页等待不停刷新，造成服务器压力；
+ 部署时，我们将详情页列表页放在CDN中部署，CDN存放静态资源页面，即不会对服务器造成压力；
+ 因为详情页列表页在CDN中，进不了服务器也就取不到动态时间，无法判断秒杀开启，所以要单独获取系统时间；
+ 地址暴露接口是根据商品id和md5值动态加密生成，也无法放在CDN中，适合服务器端缓存，引入redis，可以组成集群，具有超时穿透和主动更新的优点，减少了服务器的压力；
+ 执行秒杀操作分为插入购买明细`insert`和更新库存`update`，insert可以并行，update是串行，可以先插入再更新，这样减少了网络延迟和GC；
+ 可以将事务操作放在mysql端执行--即存储过程，然后返回操作结果。
+优化总结：
前端控制--暴露接口，按钮防重复（`insert ignore to`）
动静态数据分离：CDN缓存，后端缓存(redis)；
事务竞争优化：减少事务锁时间。      
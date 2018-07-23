package org.seckill.dao.cache;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;
import org.seckill.entity.Seckill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 主要任务：拿到seckill秒杀商品对象
 * Created by zym on 2018/7/21.
 */
public class RedisDao {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    //相当于连接池类似
    private final JedisPool jedisPool;

    public RedisDao(String ip ,int port){
        jedisPool = new JedisPool(ip, port);
    }

    private RuntimeSchema<Seckill> schema = RuntimeSchema.createFrom(Seckill.class);

    public Seckill getSeckill(long seckillId){
        //redis操作逻辑
        try {
            Jedis jedis = jedisPool.getResource();
            try{
                String key = "seckill:" + seckillId;
                /**
                 * 没有实现内部序列化操作
                 * 采用自定义序列化
                 * protostuff:pojo;带有一般getset方法的
                 */
                byte[] bytes = jedis.get(key.getBytes());
                //缓存重获取到
                if (bytes != null){
                    Seckill seckill = schema.newMessage();
                    ProtostuffIOUtil.mergeFrom(bytes,seckill,schema);//seckill被反序列化
                }
            }finally{
                jedis.close();
            }
        } catch (Exception e) {
           logger.error(e.getMessage(),e);
        }
        return null;
    }

    public String putSeckill(Seckill seckill){
        //set Object(Seckill) ->序列化 ->byte[]
        try {
            Jedis jedis = jedisPool.getResource();
            try{
                String key = "seckill:" + seckill.getSeckillId();
                byte[] bytes = ProtostuffIOUtil.toByteArray(seckill,schema,
                        LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
                //超时缓存
                int timeout = 60 * 60;//1小时
                String result = jedis.setex(key.getBytes(),timeout,bytes);
                return result;
            }finally{
                jedis.close();
            }
        }catch (Exception e) {
            logger.error(e.getMessage(),e);
        }
        return null;
    }



}

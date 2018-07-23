package org.seckill.service.ServiceImpl;

import org.apache.commons.collections.MapUtils;
import org.seckill.dao.SeckillDao;
import org.seckill.dao.SuccessKilledDao;
import org.seckill.dao.cache.RedisDao;
import org.seckill.dto.Exposer;
import org.seckill.dto.SeckillExecution;
import org.seckill.entity.Seckill;
import org.seckill.entity.SuccessKilled;
import org.seckill.enums.SeckillStatEnum;
import org.seckill.exception.RepeatKillException;
import org.seckill.exception.SeckillCloseException;
import org.seckill.exception.SeckillException;
import org.seckill.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * SeckillService接口的实现类
 * Created by zym on 2018/7/13.
 */
@Service
public class SeckillServiceImpl implements SeckillService {

    //bug1: 应统一slf4j日志api 导包需要注意
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Mybatis与spring整合之后，所有的Dao对象都会通过mapper方式初始化好，并放入spring容器中
     * 所以我们只需要对其进行依赖注入即可
     * 依赖注入：@Autowired、@Resource
    */
    @Autowired
    private SeckillDao seckillDao;

    @Autowired
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RedisDao redisDao;

    //定义一个混淆的“盐值” 用于混淆MD5 ==> 防止用户根据md5逆向破解 ,尽量复杂
    private final String slat = "s~`d-=!dwqfGJHG@UY45faada?}{&%";


    public List<Seckill> getSecKillList() {

        return  seckillDao.queryAll(0,6);
    }

    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }

    public Exposer exportSecKillUrl(long seckillId) {
        /**
         *优化点：缓存优化,在超时的基础上维护一致性
         * 将数据缓存在redis中，先从cache中查找，若没有
         * 再从数据库查找并添加到redis中
         * 以上属于数据访问层逻辑，Dao，在dao层建cache目录
        */

        //1.访问redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        if (seckill  == null){
            //2.如果redis中没有数据，则访问数据库,根据id查询秒杀商品
            seckill = seckillDao.queryById(seckillId);
            if (seckill == null){
                //如果数据库中也没有就返回false，代表秒杀单不存在
                return new Exposer(false,seckillId);
            }else{
                //3.数据库中存在就放入redis
                redisDao.putSeckill(seckill);
            }
        }


        //秒杀商品记录为空
        if (seckill == null) {
            return new Exposer(false, seckillId);
        }

        //秒杀开始时间
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        //系统当前时间
        Date nowTime = new Date();
        if (nowTime.getTime() < startTime.getTime()
                || nowTime.getTime() > endTime.getTime()) {
            return new Exposer(false, seckillId, nowTime.getTime(),
                    startTime.getTime(), endTime.getTime());
        }

        String md5 = getMD5(seckillId);
        //秒杀成功
        return new Exposer(true, md5, seckillId);
    }

    //md5加密过程 自定义拼接规则和“盐值”扩充
    private String getMD5 (long seckillId) {
        String base = seckillId + "/" +slat;
        //使用spring的util类来进行md5加密
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }

    /**
     * 使用注解控制方法的优点：
     *  1.开发团队约定明确标注事务方法的风格；
     *  2.保证事务方法的执行时间尽可能短，不要穿插其他的网络操作:RPC/HTTP请求、或剥离到事务方法外部；
     *  3.不是所有的方法都需要事务，如果只有一条修改操作，只读操作不需要事务控制。
     */
    @Transactional
    public SeckillExecution executeSecKill(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {
        //md5为空 或者 与之前注册生成的md5值不匹配
        if (md5 == null || !md5.equals(getMD5(seckillId))) {
            throw new SeckillException("seckill data are rewrited");
        }
        //执行秒杀逻辑：减库存+记录购买行为
        //1 记录当前时间为创建的秒杀时间
        Date nowTime = new Date();
        try {
            /**简单优化
             * 调整更新和插入的顺序
             * 更新记录要占据行级索，调整insert在update之前，可以减少行级索占用时间
             * 以此来简单优化,降低了网络延迟和GC影响一倍的时间
             */
             //2.2 有更新记录，记录购买行为
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            //2.2.1 秒杀验证
            if (insertCount <= 0) {
                throw new RepeatKillException("seckill is repeated");
            } else {
                //减库存,热点商品竞争
                int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
                //2 判断更新的记录
                if (updateCount <= 0) {
                    //2.1 没有更新记录，秒杀结束
                    throw new SeckillCloseException("seckill is closed");
                } else {
                    //2.2.2 秒杀成功  ==> 传入枚举类型,
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, successKilled);
                }
            }
        } catch (SeckillCloseException seckillCloseException) {
            throw seckillCloseException;
        } catch (RepeatKillException repeatKillException) {
            throw repeatKillException;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            //将所有的编译期异常转换为运行期异常 ==> 方便spring执行rollback
            throw new SeckillException("seckill inner error: " + e.getMessage());
        }
    }

    public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5) {
        if (md5 == null || md5.equals(getMD5(seckillId))) {
            return new SeckillExecution(seckillId, SeckillStatEnum.DATA_REWRITE);
        }
        Date killTime = new Date();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("seckill", seckillId);
        map.put("phone", userPhone);
        map.put("killTime", killTime);
        map.put("result", null);
        //执行存储过程，result被赋值
        try{
            seckillDao.killByProcedure(map);
            // 获取result
            int result = MapUtils.getInteger(map, "result", -2);
            if (result == 1) {
                SuccessKilled sk =
                        successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, sk);
            } else {
                return new SeckillExecution(seckillId, SeckillStatEnum.stateof(result));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new SeckillExecution(seckillId, SeckillStatEnum.INNER_ERROR);
        }

    }
}

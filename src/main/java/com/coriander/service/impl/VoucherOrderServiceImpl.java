package com.coriander.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.dto.Result;
import com.coriander.entity.SeckillVoucher;
import com.coriander.entity.Voucher;
import com.coriander.entity.VoucherOrder;
import com.coriander.mapper.VoucherOrderMapper;
import com.coriander.service.ISeckillVoucherService;
import com.coriander.service.IVoucherOrderService;
import com.coriander.utils.RedisIdWorker;
import com.coriander.utils.SimpleRedisLock;
import com.coriander.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 姓陈的
 * 2023/7/26
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private  IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTE = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTE.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";

        @Override
        public void run() {

            while(true){
                try {
                    //获取消息队列中的订单信息XREADGROUPGROUP gl c1 cOUNT 1 BLOCK 2000 STREANS streams.order >（redis stream命令）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    //判断是否有消息
                    if(list == null || list.isEmpty()){
                        //获取失败，继续下一次循环
                        continue;
                    }

                    //解析消息
                    MapRecord<String,Object,Object> record = list.get(0);
                    Map<Object,Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认，SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log. error("处理订单异常",e);
                    handlePendingList();

                }
            }

        }

        private void handlePendingList() {

            while(true){
                try {
                    //获取pending-list中的订单信息XREADGROUPGROUP gl c1 cOUNT 1 BLOCK 2000 STREANS streams.order >（redis stream命令）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    //判断是否有消息
                    if(list == null || list.isEmpty()){
                        //获取失败，pending-list没有异常消息，结束循环
                        break;
                    }

                    //解析消息
                    MapRecord<String,Object,Object> record = list.get(0);
                    Map<Object,Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认，SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log. error("处理pending-list异常",e);

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }

                }
            }

        }
    }

    /*//使用jvm的阻塞队列实现异步秒杀
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {

            while(true){
                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log. error("处理订单异常",e);
                }
            }

        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //获取用户id
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if(!isLock){
            //失败
            log.error("不允许重复下单");
            return;
        }
//  单体服务下使用      synchronized (userId.toString().intern()){
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 使用redis stream 实现异步秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //创建订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        //2.判断结果是否为0
        System.out.println("====================================="+result);
        int r = result.intValue();
        if(r != 0){
            //2.1. 不为0，代表没有购买资格
            return Result.fail(r == 1?"库存不足":"不能重复下单");
        }

        //获取代理对象
        //注意，为了防止事物失效，需要获取其代理对象。
        //获取代理对象（事物）
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //返回订单id
        return Result.ok(orderId);

    }

   /*
   使用jvm的阻塞队列实现异步秒杀
   @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        System.out.println("====================================="+result);
        int r = result.intValue();
        if(r != 0){
            //2.1. 不为0，代表没有购买资格
            return Result.fail(r == 1?"库存不足":"不能重复下单");
        }
        //3. 为0，有购买资格，把下单信息保存到阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //创建订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象
        //注意，为了防止事物失效，需要获取其代理对象。
        //获取代理对象（事物）
         proxy = (IVoucherOrderService) AopContext.currentProxy();

        //返回订单id
        return Result.ok(orderId);

    }*/


    /**
     * 创建订单(旧，没有异步的创建订单)
     * @param
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //用户id
        Long userId = voucherOrder.getUserId();

        //一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("已购买！");
            return;
        }

        //5.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();

        if (!success) {
            log.error("库存不足");
            return ;
        }
        save(voucherOrder);
    }


  /*  @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("活动尚未开始");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //活动已结束
            return Result.fail("活动以结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if(!isLock){
            //失败
            return Result.fail("不允许重复下单");
        }
//  单体服务下使用      synchronized (userId.toString().intern()){
        try {
             //注意，为了防止事物失效，需要获取其代理对象。
            //获取代理对象（事物）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
//        }
    }*/

    /**
     * 创建订单(旧，没有异步的创建订单)
     * @param voucherId
     * @return
     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        //用户id
//        Long userId = UserHolder.getUser().getId();
//
//        //一人一单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            return Result.fail("已购买！");
//        }
//
//
//        //5.扣减库存
//        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).gt("stock", 0).update();
//
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
//
//        //6.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //创建订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//
//        voucherOrder.setUserId(userId);
//        //代金卷id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        //返回订单id
//        return Result.ok(orderId);
//    }
}

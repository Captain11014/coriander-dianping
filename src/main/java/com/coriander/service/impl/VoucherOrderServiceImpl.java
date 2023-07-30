package com.coriander.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.dto.Result;
import com.coriander.entity.SeckillVoucher;
import com.coriander.entity.VoucherOrder;
import com.coriander.mapper.VoucherOrderMapper;
import com.coriander.service.ISeckillVoucherService;
import com.coriander.service.IVoucherOrderService;
import com.coriander.utils.RedisIdWorker;
import com.coriander.utils.SimpleRedisLock;
import com.coriander.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 姓陈的
 * 2023/7/26
 */
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

    @Override
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

        //创建所对象
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
            /**
             * 注意，为了防止事物失效，需要获取其代理对象。
             */
            //获取代理对象（事物）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
//        }
    }

    /**
     * 创建订单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //用户id
        Long userId = UserHolder.getUser().getId();

        //一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已购买！");
        }


        //5.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0).update();

        if (!success) {
            return Result.fail("库存不足");
        }


        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //创建订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //返回订单id
        return Result.ok(orderId);
    }
}

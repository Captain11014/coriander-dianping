package com.coriander;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coriander.entity.Shop;
import com.coriander.entity.ShopType;
import com.coriander.service.IShopService;
import com.coriander.service.IShopTypeService;
import com.coriander.service.impl.ShopServiceImpl;
import com.coriander.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopTypeService shopTypeService;

    @Resource
    private IShopService shopService;

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("time = "+(end - begin));
    }


    @Test
    void testRedis() {

        stringRedisTemplate.opsForValue().set("k1", "芜湖111");
        String k1 = stringRedisTemplate.opsForValue().get("k1");

        System.out.println(k1);

    }


    @Test
    void testList() {

        shopServiceImpl.saveShop2Redis(1L, 10L);

    }


}

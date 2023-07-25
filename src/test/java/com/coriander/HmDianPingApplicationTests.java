package com.coriander;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coriander.entity.Shop;
import com.coriander.entity.ShopType;
import com.coriander.service.IShopService;
import com.coriander.service.IShopTypeService;
import com.coriander.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;

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

    @Test
    void testRedis(){

        stringRedisTemplate.opsForValue().set("k1","芜湖111");
        String k1 = stringRedisTemplate.opsForValue().get("k1");

        System.out.println(k1);

    }


    @Test
    void testList(){

        shopServiceImpl.saveShop2Redis(1L,10L);

    }






}

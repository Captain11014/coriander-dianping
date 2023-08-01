package com.coriander.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.coriander.dto.Result;
import com.coriander.entity.Shop;
import com.coriander.mapper.ShopMapper;
import com.coriander.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.utils.CacheClient;
import com.coriander.utils.RedisData;
import com.coriander.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.coriander.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿问题
//        Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,10L,TimeUnit.SECONDS);


        if (shop == null) {
            return Result.fail("商铺信息不存在");
        }

        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 使用逻辑过期解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.不存在则返回null
            return null;
        }
        //4.命中，吧json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return shop;
        }

        //6.过期，缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.1判断是否成功获取锁
        if(isLock){
            //获取成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //返回过期的数据
        return shop;
    }


    /**
     * 使用互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.redis查询到数据则返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson != null) {
            return null;
        }

        // 4.实现缓存重构
        //4.1 获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 判断否成功获取锁
            if (!isLock) {
                //4.3失败，休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 成功，查询数据库，重构缓存
            shop = getById(id);
            Thread.sleep(200);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 缓存穿透问题
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.redis查询到数据则返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson != null) {
            return null;
        }

        //3.redis没有，则查询数据库
        Shop shop = getById(id);
        //4.数据库没有商铺信息，则返回相应信息
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.数据库查询到了数据，则添加到redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    public void saveShop2Redis(Long id, Long expireSeconds) {

        //数据库查询数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }


    @Override
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商铺id不能为空");
        }
        //更新数据
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return null;
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis、按照距离排序、分页、结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //解析id
        if(results == null){
            return  Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            //没有下一页了
            return  Result.ok(Collections.emptyList());
        }
        //截取from - end
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for(Shop shop : shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }
}

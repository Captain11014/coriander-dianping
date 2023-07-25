package com.coriander.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.coriander.entity.ShopType;
import com.coriander.mapper.ShopTypeMapper;
import com.coriander.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.coriander.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public List<ShopType> getAll() {

        //1.从redis 查询是否有数据
        String listJson = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPE_KEY);
        if(StrUtil.isNotBlank(listJson)){
            //执行缓存中的数据
            return JSONUtil.toList(listJson,ShopType.class);
        }
        System.out.println("redis没有数据========================");
        //查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        //保存数据到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(list));
        return list;
    }
}

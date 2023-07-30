package com.coriander.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 姓陈的
 * 2023/7/29
 */
@Configuration
public class RedissonConfig {


    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://120.76.201.121:6379")
                .setPassword("@chen$captain_2001");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }


}

package com.coriander.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author 姓陈的
 * 2023/7/28
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true 代表取锁成功，false代表取锁失败
     */
    boolean tryLock(long timeoutSec);


    /**
     * 释放锁
     */
    void unlock();


}

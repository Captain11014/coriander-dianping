package com.coriander.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.coriander.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.coriander.utils.RedisConstants.LOGIN_USER_KEY;
import static com.coriander.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author 姓陈的
 * 2023/7/24 13:57
 */
public class RefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RefreshInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }

        //2.获取session中的用户
        String tokenkey = LOGIN_USER_KEY+token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenkey);
        //3.判断用户是否存在
        if(userMap.isEmpty()){
            //4.如果不存在则拦截
            return true;
        }
        //5.保存用户到ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        //6.刷新token有效期
        redisTemplate.expire(tokenkey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

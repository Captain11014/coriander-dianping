package com.coriander.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.coriander.dto.UserDTO;
import com.coriander.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.coriander.utils.RedisConstants.LOGIN_USER_KEY;
import static com.coriander.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author 姓陈的
 * 2023/7/24 13:57
 */
public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if(UserHolder.getUser() == null){
           response.setStatus(401);
           response.getWriter().print("no login");
           return false;
        }

        return true;

    }

}

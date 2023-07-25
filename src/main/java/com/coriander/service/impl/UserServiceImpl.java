package com.coriander.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.dto.LoginFormDTO;
import com.coriander.dto.Result;
import com.coriander.dto.UserDTO;
import com.coriander.entity.User;
import com.coriander.mapper.UserMapper;
import com.coriander.service.IUserService;
import com.coriander.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.coriander.utils.RedisConstants.*;
import static com.coriander.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCoode(String phone, HttpSession session) {

        //1.校验手机号码
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码格式错误");
        }

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到session
//        session.setAttribute("code",code);
        //3.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //1.校验手机号码
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码格式错误");
        }

        //2.校验验证码
        Object code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail("验证码错误！");
        }

        //3.查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        //4.判断用户是否存在，如果不存在，则新增用户信息。
        if(user == null){
            user = createUser(loginForm.getPhone());
        }

        //5.将用户信息保存到redis
        //生成token
        String token = UUID.randomUUID().toString(true);
        //将对象转换为hashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString())
        );
        //将数据存入redis
        String tokenkey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenkey,userMap);
        //设置过期时间
        stringRedisTemplate.expire(tokenkey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    /**
     * 创建新用户
     * @return
     */
    protected User createUser(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

}

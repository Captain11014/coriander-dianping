package com.coriander.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.entity.User;
import com.coriander.mapper.UserMapper;
import com.coriander.service.IUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}

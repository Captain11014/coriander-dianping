package com.coriander.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.coriander.dto.LoginFormDTO;
import com.coriander.dto.Result;
import com.coriander.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
public interface IUserService extends IService<User> {

    Result sendCoode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 签到
     * @return
     */
    Result sign();

    Result signCount();
}

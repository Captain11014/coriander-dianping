package com.coriander.service;

import com.coriander.dto.Result;
import com.coriander.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);
}

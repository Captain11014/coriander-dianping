package com.coriander.service;

import com.coriander.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
public interface IShopTypeService extends IService<ShopType> {

    List<ShopType> getAll();
}

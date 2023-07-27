package com.coriander.controller;


import com.coriander.dto.Result;
import com.coriander.entity.ShopType;
import com.coriander.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();

        List<ShopType> typeList = typeService.getAll();

        return Result.ok(typeList);
    }
}

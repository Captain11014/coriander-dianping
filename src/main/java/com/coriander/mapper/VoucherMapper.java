package com.coriander.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coriander.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}

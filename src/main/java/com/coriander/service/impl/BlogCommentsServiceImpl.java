package com.coriander.service.impl;

import com.coriander.entity.BlogComments;
import com.coriander.mapper.BlogCommentsMapper;
import com.coriander.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}

package com.coriander.service.impl;

import com.coriander.entity.Blog;
import com.coriander.mapper.BlogMapper;
import com.coriander.service.IBlogService;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}

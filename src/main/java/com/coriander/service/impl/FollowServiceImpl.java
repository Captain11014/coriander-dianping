package com.coriander.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coriander.dto.Result;
import com.coriander.dto.UserDTO;
import com.coriander.entity.Follow;
import com.coriander.mapper.FollowMapper;
import com.coriander.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coriander.service.IUserService;
import com.coriander.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.coriander.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
  * @author 姓陈的
 * 2023/7/26
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

        Long userId = UserHolder.getUser().getId();

        String key = FOLLOW_KEY + userId;

        //判断是关注还是取消关注
        if(isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //把关注的用户id，放入redis集合
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //取消关注
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id",userId).eq("follow_user_id",followUserId));
            if(isSuccess){
                //把关注的用户id，从redis删除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;

        //求交集
        String key2 = FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);

        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }
}

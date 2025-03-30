/*
 * @Author: weihua hu
 * @Date: 2025-03-21 00:44:47
 * @LastEditTime: 2025-03-29 23:33:49
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.service.impl;

import com.weihua.pojo.User;
import com.weihua.service.UserService;
import lombok.extern.log4j.Log4j2;

import java.util.Random;
import java.util.UUID;

@Log4j2
public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        // System.out.println("客户端查询了"+id+"的用户");
        log.info("客户端查询了" + id + "的用户");
        // 模拟从数据库中取用户的行为
        Random random = new Random();
        User user = User.builder().userName(UUID.randomUUID().toString())
                .id(id)
                .sex(random.nextBoolean()).build();
        return user;
    }

    @Override
    public Integer insertUserId(User user) {
        // System.out.println("插入数据成功"+user.getUserName());
        log.info("插入数据成功" + user.getUserName());
        return user.getId();
    }
}
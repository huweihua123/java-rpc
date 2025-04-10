/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:03:30
 * @LastEditTime: 2025-04-10 15:03:31
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.common.api;

import com.weihua.rpc.example.common.model.User;

import java.util.List;

/**
 * 用户服务接口
 * 定义服务提供者将要实现的API
 */
public interface UserService {

    /**
     * 根据ID获取用户
     * 
     * @param id 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    User getUserById(Long id);

    /**
     * 获取所有用户
     * 
     * @return 用户列表
     */
    List<User> getAllUsers();

    /**
     * 创建用户
     * 
     * @param user 用户信息
     * @return 创建后的用户（带ID）
     */
    User createUser(User user);

    /**
     * 更新用户信息
     * 
     * @param user 用户信息
     * @return 是否更新成功
     */
    boolean updateUser(User user);

    /**
     * 删除用户
     * 
     * @param id 用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long id);
}

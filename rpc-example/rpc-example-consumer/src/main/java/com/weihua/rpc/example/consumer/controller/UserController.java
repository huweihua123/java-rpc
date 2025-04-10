/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:05:55
 * @LastEditTime: 2025-04-10 15:05:57
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.consumer.controller;

import com.weihua.rpc.example.common.api.UserService;
import com.weihua.rpc.example.common.model.User;
import com.weihua.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器
 * 演示如何通过@RpcReference注解引用远程服务
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @RpcReference
    private UserService userService;

    @GetMapping("/{id}")
    public User getUserById(@PathVariable("id") Long id) {
        log.info("获取用户: id={}", id);
        return userService.getUserById(id);
    }

    @GetMapping
    public List<User> getAllUsers() {
        log.info("获取所有用户");
        return userService.getAllUsers();
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        log.info("创建用户: {}", user);
        return userService.createUser(user);
    }

    @PutMapping
    public boolean updateUser(@RequestBody User user) {
        log.info("更新用户: {}", user);
        return userService.updateUser(user);
    }

    @DeleteMapping("/{id}")
    public boolean deleteUser(@PathVariable("id") Long id) {
        log.info("删除用户: id={}", id);
        return userService.deleteUser(id);
    }
}

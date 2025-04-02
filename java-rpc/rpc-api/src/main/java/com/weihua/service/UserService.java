package com.weihua.service;


import com.weihua.annotation.Retryable;
import com.weihua.pojo.User;

public interface UserService {
    // 客户端通过这个接口调用服务端的实现类
    @Retryable
    User getUserByUserId(Integer id);
    //新增一个功能
    @Retryable
    Integer insertUserId(User user);
}
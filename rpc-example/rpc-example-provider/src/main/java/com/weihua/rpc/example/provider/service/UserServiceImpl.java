package com.weihua.rpc.example.provider.service;

import com.weihua.rpc.core.server.annotation.RateLimit;
import com.weihua.rpc.core.server.annotation.Retryable;
import com.weihua.rpc.example.common.api.UserService;
import com.weihua.rpc.example.common.model.User;
import com.weihua.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RpcService(interfaceClass = UserService.class)
@RateLimit(qps = 200) // 服务级限流配置
public class UserServiceImpl implements UserService {

    // 用于生成用户ID
    private final AtomicLong idGenerator = new AtomicLong(1);

    // 模拟用户数据存储
    private final Map<Long, User> userMap = new ConcurrentHashMap<>();

    public UserServiceImpl() {
        // 初始化一些测试用户数据
        initTestUsers();
    }

    private void initTestUsers() {
        createUser(User.builder()
                .username("admin")
                .realName("管理员")
                .email("admin@example.com")
                .phone("13800000000")
                .status(1)
                .build());

        createUser(User.builder()
                .username("zhangsan")
                .realName("张三")
                .email("zhangsan@example.com")
                .phone("13800000001")
                .status(1)
                .build());

        createUser(User.builder()
                .username("lisi")
                .realName("李四")
                .email("lisi@example.com")
                .phone("13800000002")
                .status(1)
                .build());
    }

    @Override
    @RateLimit(qps = 300) // 查询接口QPS更高
    @Retryable(description = "查询操作无副作用")
    public User getUserById(Long id) {
        log.info("获取用户信息: id={}", id);
        return userMap.get(id);
    }

    @Override
    @RateLimit(qps = 100) // 查询所有用户开销大，限制QPS
    @Retryable(maxRetries = 1, description = "查询操作无副作用但开销大，限制重试次数")
    public List<User> getAllUsers() {
        log.info("获取所有用户");
        return new ArrayList<>(userMap.values());
    }

    @Override
    @RateLimit(qps = 50) // 写操作限流更严格
    public User createUser(User user) {
        // 补充用户信息
        user.setId(idGenerator.getAndIncrement());
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        if (user.getStatus() == null) {
            user.setStatus(1);
        }

        // 保存用户
        userMap.put(user.getId(), user);
        log.info("创建用户成功: {}", user);

        return user;
    }

    @Override
    @RateLimit(qps = 50) // 写操作限流更严格
    public boolean updateUser(User user) {
        if (user == null || user.getId() == null || !userMap.containsKey(user.getId())) {
            log.warn("更新用户失败: 用户不存在");
            return false;
        }

        // 保留创建时间，更新其他字段
        User originalUser = userMap.get(user.getId());
        user.setCreateTime(originalUser.getCreateTime());
        user.setUpdateTime(new Date());

        // 更新用户
        userMap.put(user.getId(), user);
        log.info("更新用户成功: {}", user);

        return true;
    }

    @Override
    @RateLimit(qps = 30) // 删除操作限流最严格
    public boolean deleteUser(Long id) {
        if (userMap.remove(id) != null) {
            log.info("删除用户成功: id={}", id);
            return true;
        } else {
            log.warn("删除用户失败: 用户不存在, id={}", id);
            return false;
        }
    }
}

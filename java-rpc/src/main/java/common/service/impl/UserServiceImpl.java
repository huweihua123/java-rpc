package common.service.impl;

import common.pojo.User;
import common.service.UserService;

import java.util.Random;
import java.util.UUID;

//public class UserServiceImpl implements UserService {
//    public User getUserById(Integer id) {
//        System.out.println("客户端查询" + id + "的数据");
//        return User.builder()
//                .name("name_"+ UUID.randomUUID().toString())
//                .id(id)
//                .build();
//    }
//}


public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        System.out.println("客户端查询了"+id+"的用户");
        // 模拟从数据库中取用户的行为
        Random random = new Random();
        User user = User.builder().userName(UUID.randomUUID().toString())
                .id(id)
                .sex(random.nextBoolean()).build();
        return user;
    }

    @Override
    public Integer insertUserId(User user) {
        System.out.println("插入数据成功"+user.getUserName());
        return user.getId();
    }
}
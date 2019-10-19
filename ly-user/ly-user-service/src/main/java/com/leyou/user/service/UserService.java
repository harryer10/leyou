package com.leyou.user.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:verify:phone";

    public Boolean checkData(String data, Integer type) {
        User record = new User();
        // 判断数据类型
        switch (type) {
            case 1:
                record.setUsername(data);
                break;
            case 2:
                record.setPhone(data);
                break;
            default:
                throw new LyException(ExceptionEnum.INVALID_USER_DATA_TYPE);
        }
        return userMapper.selectCount(record) == 0;
    }

    public void sendCode(String phone) {
        // 生成key
        String key = KEY_PREFIX + phone;
        // 生成验证码
        String code = NumberUtils.generateCode(6);
        Map<String, String> msg = new HashMap<>(16);
        msg.put("phone",phone);
        msg.put("code", code);
        // 使用AMQP调用短信微服务发送验证码
        amqpTemplate.convertAndSend("ly.sms.exchange","sms.verify.code", msg);
        // 保存手机验证码(保存五分钟);
        redisTemplate.opsForValue().set(key,code,5, TimeUnit.MINUTES);
    }

    public void register(@Valid User user, String code) {
        // 校验短信验证码
        // 1.获取key
        String key = KEY_PREFIX + user.getPhone();
        // 2.通过key去redis里拿到验证码
        String codeCache = redisTemplate.opsForValue().get(key);
        // 3.将用户输入的code和redis里存的code进行对比，存在就验证通过
        if(!code.equals(codeCache)) {
            throw new LyException(ExceptionEnum.INVALID_VERIFY_CODE);
        }
        // 生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        // 对密码进行加密
        user.setPassword(CodecUtils.md5Hex(user.getPassword() , salt));
        // 写入数据库
        user.setCreated(new Date());
        userMapper.insert(user);
    }

    public User queryUser(String username, String password) {
        // 先查询用户
        User record = new User();
        record.setUsername(username);
        User user = userMapper.selectOne(record);
        // 判断是否有该用户
        if(user == null) {
            throw new LyException(ExceptionEnum.INVALID_USERNAME_PASSWORD);
        }
        // 校验密码
        if(!StringUtils.equals(user.getPassword(), CodecUtils.md5Hex(password, user.getSalt()))) {
            throw new LyException(ExceptionEnum.INVALID_USERNAME_PASSWORD);
        }
        return user;
    }
}

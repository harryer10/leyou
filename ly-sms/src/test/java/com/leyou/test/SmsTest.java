package com.leyou.test;

import com.leyou.LySmsApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = LySmsApplication.class)
public class SmsTest {

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Test
    public void test() {
        Map<String, String> msg = new HashMap<>(16);
        msg.put("phone","18221873298");
        msg.put("code", "654321");
        amqpTemplate.convertAndSend("ly.sms.exchange","sms.verify.code", msg);

        //Thread.sleep(10000);
    }


}


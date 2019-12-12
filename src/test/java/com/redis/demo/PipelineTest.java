package com.redis.demo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PipelineTest {

    @Autowired
    private RedisTemplate redisTemplate;

    @Test
    public void checkoutSenssion() {
        redisTemplate.executePipelined(new SessionCallback<Object>() {

            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                return null;
            }
        });
    }


    @Test
    public void checkoutRedisCallBack() {
        long begin = System.currentTimeMillis();
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                connection.openPipeline();
                for (int i = 0; i < 100000; i++) {
                    String key = "123" + i;
                    connection.zCount(key.getBytes(), 0, Integer.MAX_VALUE);
                }
                List<Object> objects = connection.closePipeline();
                return null;
            }
        });
        long end = System.currentTimeMillis();
        long x = end - begin;
        System.out.println(x);
    }
}

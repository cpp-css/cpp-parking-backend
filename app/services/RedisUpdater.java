package services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import models.CustomConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Singleton
public class RedisUpdater {
    private Jedis jedis;
    private CustomConfiguration configuration;

    @Inject
    public RedisUpdater(JedisPool jedisPool, CustomConfiguration configuration) {
        this.jedis = jedisPool.getResource();
        this.configuration = configuration;
    }

    public void updateParkingState(String field, int incrby) {
        jedis.hincrBy(configuration.getRedisKey(), field, incrby);
    }

}

package services;

import akka.actor.ActorRef;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import models.CustomConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import actors.messages.*;

import java.util.logging.Logger;

/**
 * Infinitely blocking thread waiting for redis messages
 */
@Singleton
public class RedisSubscriber extends JedisPubSub implements Runnable {
    private Logger logger;
    private Jedis jedis;
    private CustomConfiguration configuration;
    private ActorRef clientManager;

    @Inject
    public RedisSubscriber(Logger logger,
                           JedisPool jedisPool,
                           CustomConfiguration configuration,
                           @Named("clientManagerActor") ActorRef clientManager) {
        this.logger = logger;
        this.jedis = jedisPool.getResource();
        this.configuration = configuration;
        this.clientManager = clientManager;
    }

    /**
     * this is called whenever an update occurred to the cpp parking hashmap in redis
     * all of the other callbacks aren't really useful
     * @param channel
     * @param message
     */
    @Override
    public void onMessage(String channel, String message) {
        logger.info(String.format("Message: channel: %s, message: %s",channel,message));
        clientManager.tell(new NewState(), ActorRef.noSender());
    }


    @Override
    public void onPMessage(String pattern, String channel, String message) {
        logger.info(String.format("Message from: pattern: %s, channel: %s, message: %s",
                pattern, channel, message));
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        logger.info(String.format("Subscription: channel: %s, subscribedChannels: %s", channel, subscribedChannels));
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        logger.info(String.format("Unsubscribe: channel: %s, subscribedChannels: %s", channel, subscribedChannels));
    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        logger.info(String.format("Pattern Unsubscribe: pattern: %s, subscribedChannels: %s", pattern, subscribedChannels));
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        logger.info(String.format("Pattern Subscription: pattern: %s, subscribedChannels: %s", pattern, subscribedChannels));
    }

    @Override
    public void run() {
        try {
            jedis.subscribe(this, configuration.getRedisKeySpaceChannel());
        } finally {
            //should never reach here since subscribe blocks forever, but anyways...
            jedis.close();
        }
    }
}

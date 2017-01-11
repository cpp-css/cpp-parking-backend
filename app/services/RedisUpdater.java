package services;

import com.google.inject.Inject;
import models.CustomConfiguration;
import models.ParkingLot;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * wrapper around jedis w/convenience methods for the updates/reads we wish to perform
 */
public class RedisUpdater {
    private final Logger logger;
    private final Jedis jedis;
    private final CustomConfiguration configuration;
    private final Set<String> validParkingLotNames;
    private static final String REDIS_PARKING_FIELD = "occupancy";

    @Inject
    public RedisUpdater(Logger logger, JedisPool jedisPool, CustomConfiguration configuration) {
        this.logger = logger;
        this.jedis = jedisPool.getResource();
        this.configuration = configuration;
        this.validParkingLotNames = configuration.getParkingLots().stream().
                map(ParkingLot::getName).collect(Collectors.toSet());
    }

    /**
     * make a HINCRBY request on lot 'lotname',
     * i.e. atomically increments 'lotname' by 'incrby'
     *
     * @param lotName name of the cpp parking lot
     * @param incrby  amount changed
     */
    public void updateParkingLotOccupancy(String lotName, int incrby) {
        if (validParkingLotNames.contains(lotName)) {
            jedis.hincrBy(lotNameToKey(lotName), REDIS_PARKING_FIELD, incrby);
        } else {
            logger.warning(String.format("Request for update on nonexistent parking lot %s:", lotName));
        }
    }

    /**
     * @param lotName name of cpp parking lot
     * @return the current amount of cars in parking lot 'lotname'
     */
    public int getParkingLotOccupancy(String lotName) {
        return Integer.parseInt(
                jedis.hmget(lotNameToKey(lotName), REDIS_PARKING_FIELD).get(0));
    }

    /**
     * returns a new map of all cpp parking lot names, to their latest occupancy
     * using a fresh redis query
     * @return
     */
    public Map<String, Integer> getAllLotOccupancy() {
        List<Pair<String, Response<List<String>>>> redisResponse = new ArrayList<>();
        Map<String, Integer> result = new HashMap<>();

        //redis transaction: https://github.com/xetorthio/jedis/wiki/AdvancedUsage#transactions
        Transaction t = jedis.multi();
        for (String lotName : validParkingLotNames) {
            String keyName = lotNameToKey(lotName);
            redisResponse.add(new ImmutablePair<>(lotName, t.hmget(keyName, REDIS_PARKING_FIELD)));
        }
        t.exec();

        for (Pair<String, Response<List<String>>> pair : redisResponse) {
            String lotName = pair.getLeft();
            String occupancyResponse = pair.getRight().get().get(0);
            //this key isn't inside redis yet
            if (occupancyResponse == null) {
                continue;
            }
            int occupancy = Integer.parseInt(pair.getRight().get().get(0));
            result.put(lotName, occupancy);
        }

        return result;
    }


    /**
     * Note: this modifies the input map!!
     *
     * queries for all parking lot states,
     * iterates through input map, setting each parking lot object's new occupancy
     * <p>
     * if we discover any keys in redis that aren't in the input map,
     * we log the discrepancy
     *
     * @param oldLotStatus map of parking lot names to parking lot objects
     */
    public void updateParkingLots(Map<String, ParkingLot> oldLotStatus) {
        Map<String, Integer> latestStatus = this.getAllLotOccupancy();
        for (String lotName : latestStatus.keySet()) {
            if (!oldLotStatus.containsKey(lotName)) {
                logger.warning(String.format("Lotname field %s in redis hashmap not found in local config", lotName));
            } else {
                oldLotStatus.get(lotName).setOccupancy(latestStatus.get(lotName));
            }
        }
    }

    /**
     * convert lotname to a key in redis by prefixing with "cpp"
     * <p>
     * this makes it easy for redisSubscriber to see changes
     * on all cpp parking keys using a glob pattern
     *
     * @param lotName name of cpp parking lot
     * @return
     */
    private String lotNameToKey(String lotName) {
        return configuration.getRedisKey() + lotName;
    }

    /**
     * if you invoke this, all other redis queries sent by this object will then fail
     * only call this when you no longer need this object anymore
     */
    public void close() {
        this.jedis.close();
    }
}

package providers;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import models.CustomConfiguration;
import models.ParkingLot;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Looks up initial Redis state for all parking lots
 * If key and/or lot fields do not exist, then default to 0
 */
public class InitialParkingStateProvider implements Provider<Map<String, ParkingLot>> {

    private final Jedis jedis;
    private final CustomConfiguration configuration;
    private final Gson gson;
    private final Logger logger;

    @Inject
    public InitialParkingStateProvider(Logger logger, JedisPool jedisPool, CustomConfiguration configuration, Gson gson) {
        this.logger = logger;
        this.jedis = jedisPool.getResource();
        this.configuration = configuration;
        this.gson = gson;
    }

    @Override
    public Map<String, ParkingLot> get() {
        Map<String, ParkingLot> result = new HashMap<>();

        List<ParkingLot> initialState = configuration.getParkingLots();
        String redisHashmapKey = configuration.getRedisKey();

        for (ParkingLot lot : initialState) {
            result.put(lot.getName(), lot);
        }

        try {
            Map<String, String> lotStatus = jedis.hgetAll(redisHashmapKey);
            for (String lotName : lotStatus.keySet()) {
                if (!result.containsKey(lotName)) {
                    logger.warning(String.format("Lotname field %s in redis hashmap not found in local config", lotName));
                } else {
                    result.get(lotName).setNumParked(Integer.parseInt(lotStatus.get(lotName)));
                }
            }
        } finally {
            jedis.close();
        }
        return result;
    }
}

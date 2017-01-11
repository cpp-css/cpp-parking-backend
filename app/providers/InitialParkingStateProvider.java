package providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import models.CustomConfiguration;
import models.ParkingLot;
import services.RedisUpdater;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up initial Redis state for all parking lots
 * If key and/or lot fields do not exist, then default to 0
 */
public class InitialParkingStateProvider implements Provider<Map<String, ParkingLot>> {

    private final RedisUpdater redisUpdater;
    private final CustomConfiguration configuration;

    @Inject
    public InitialParkingStateProvider(RedisUpdater redisUpdater, CustomConfiguration configuration) {
        this.redisUpdater = redisUpdater;
        this.configuration = configuration;
    }

    @Override
    public Map<String, ParkingLot> get() {
        Map<String, ParkingLot> result = new HashMap<>();
        List<ParkingLot> initialState = configuration.getParkingLots();
        for (ParkingLot lot : initialState) {
            result.put(lot.getName(), lot);
        }

        try {
            redisUpdater.updateParkingLots(result);
        } finally {
            redisUpdater.close();
        }
        return result;
    }
}

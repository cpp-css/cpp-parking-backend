package models;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import play.Configuration;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Wrapper around normal Play Configuration with some convenience methods for
 * parsing some application-specific objects, like:
 * 1. the json list of initial lot capacities
 * 2. the redis key that all our fields are namespaced to
 */
public class CustomConfiguration {
    private Configuration configuration;
    private Gson gson;

    @Inject
    public CustomConfiguration(Configuration configuration, Gson gson) {
        this.configuration = configuration;
        this.gson = gson;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getRedisKey() {
        return configuration.getString("redis.hashmapkey");
    }

    public List<ParkingLot> getParkingLots() {
        // Super hacky, stupid Play Framework api doesn't let you parse stuff easily
        String jsonString = gson.toJson(configuration.asMap().get("lots"));
        Type type = new TypeToken<List<ParkingLot>>() {
        }.getType();
        return gson.fromJson(jsonString, type);
    }


    public String getRedisKeySpaceChannel() {
        return "__keyspace@" + String.valueOf(configuration.getInt("redis.database"))
                + "__:" + getRedisKey();
    }
}

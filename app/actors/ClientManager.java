package actors;

import actors.messages.*;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import annotations.AllParkingState;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import models.CustomConfiguration;
import models.ParkingLot;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Actor that
 * 1. supervises all ClientActors (each of which holds a websocket connection to a client),
 * 2. holds the current state of all parking lots
 * 3. refreshes the state of all lots whenever redis publishes an update
 * <p>
 * Clientactors should register ClientManager a ConnectionCreated message upon initialization,
 * and a ConnectionClosed message upon death
 * <p>
 * ClientManager will forward json messages of parking state to all registered clientActors
 * <p>
 * Jedis Subscriber should tell ClientManager whenever to update state
 */
@Singleton
public class ClientManager extends UntypedActor {

    private Logger logger;
    private Set<ActorRef> clients;
    private Gson gson;
    private Map<String, ParkingLot> allParkingLotState;
    private Jedis jedis;
    private CustomConfiguration configuration;


    @Inject
    public ClientManager(Logger logger,
                         Gson gson,
                         @AllParkingState Map<String, ParkingLot> allParkingLotState,
                         JedisPool jedisPool,
                         CustomConfiguration configuration) {
        this.logger = logger;
        this.gson = gson;
        this.clients = new HashSet<>();
        this.jedis = jedisPool.getResource();
        this.allParkingLotState = allParkingLotState;
        this.configuration = configuration;
    }

    /**
     * updates allParkingLotState map by querying redis
     * todo: refactor code duplication with providers.InitialParkingStateProvider
     */
    private void refreshState() {
        Map<String, String> lotStatus = jedis.hgetAll(configuration.getRedisKey());
        for (String lotName : lotStatus.keySet()) {
            if (!allParkingLotState.containsKey(lotName)) {
                logger.warning(String.format("Lotname field %s in redis hashmap not found in local config", lotName));
            } else {
                allParkingLotState.get(lotName).setNumParked(Integer.parseInt(lotStatus.get(lotName)));
            }
        }
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ConnectionCreated) {
            clients.add(getSender());
        } else if (message instanceof ConnectionClosed) {
            clients.remove(getSender());
        } else if (message instanceof NewState) {
            refreshState();
            JsonMessage parkingJson = new JsonMessage(
                    gson.toJson(new ArrayList<>(allParkingLotState.values())));
            logger.info(parkingJson.getJson());
            for (ActorRef client : clients) {
                client.tell(parkingJson, getSelf());
            }
        } else if (message instanceof CurrentStateRequest) {
            JsonMessage parkingJson = new JsonMessage(
                    gson.toJson(allParkingLotState));

            getSender().tell(parkingJson, getSelf());
        } else {
            unhandled(message);
        }
    }
}

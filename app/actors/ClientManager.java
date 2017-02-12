package actors;

import actors.messages.*;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import annotations.AllParkingState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import models.DiffUpdate;
import models.FullStateUpdate;
import models.KeepAliveMessage;
import models.ParkingLot;
import play.libs.Json;
import play.libs.akka.InjectedActorSupport;
import services.RedisUpdater;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;


/**
 * Actor that
 * 1. supervises all ClientActors (each of which holds a websocket connection to a client),
 * 2. holds the current state of all parking lots
 * 3. refreshes the state of the appropriate lot whenever redis publishes an update
 * <p>
 * ClientManager will forward json messages of parking state changes to all clientActors
 * <p>
 * Jedis Subscriber should tell ClientManager whenever to update state
 */
@Singleton
public class ClientManager extends UntypedActor implements InjectedActorSupport {

    private Logger logger;
    private Set<ActorRef> clients;
    private Map<String, ParkingLot> allParkingLotState;
    private RedisUpdater redisUpdater;
    private ClientActor.Factory clientActorFactory;


    @Inject
    public ClientManager(Logger logger,
                         ClientActor.Factory clientActorFactory,
                         @AllParkingState Map<String, ParkingLot> allParkingLotState,
                         RedisUpdater redisUpdater) {
        this.logger = logger;
        this.clientActorFactory = clientActorFactory;
        this.clients = new HashSet<>();
        this.allParkingLotState = allParkingLotState;
        this.redisUpdater = redisUpdater;
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ClientActorCreate) {
            ClientActorCreate create = (ClientActorCreate) message;
            ActorRef child = injectedChild(() -> clientActorFactory.create(create.getOut()), create.getId());
            sender().tell(child, getSelf());
        } else if (message instanceof ConnectionCreated) {
            clients.add(getSender());
            logger.info(String.format("Connection created, num connections: %d", clients.size()));
            getSender().tell(Json.toJson(new FullStateUpdate(allParkingLotState)), getSelf());
        } else if (message instanceof ConnectionClosed) {
            clients.remove(getSender());
            logger.info(String.format("Connection closed, num connections: %d", clients.size()));
        } else if (message instanceof RefreshState) {
            //todo rethink how this is done or remove it entirely?
            //the only real way for servers to be out of sync is network drop, but in that case,
            //we could just restart the server anyways

            //make a deep copy first
            Map<String, ParkingLot> currentStateClone = new HashMap<>();
            for (String lotName : allParkingLotState.keySet()) {
                currentStateClone.put(lotName, new ParkingLot(allParkingLotState.get(lotName)));
            }

            //call the updater, which modifies the passed in map
            redisUpdater.updateParkingLots(allParkingLotState);

            //compare with previous map check for any state difference
            //if there was any difference in state, one of two things happened:
            //  1. somehow our server missed a notification from redis (unlikely, but can happen w/temporary network outage?)
            //  2. false alarm: where there is actually a message about this updated state,
            //       but that message is later in the message queue (we haven't got to it yet)

            // so... the hack to check if this falls under 1., and not 2. is to schedule this
            // "RefreshState" update very infrequently and when we expect no changes
            // in parking state, e.g. ~midnight once every 24 hours
            for (String lotName : allParkingLotState.keySet()) {
                int latestOccupancy = allParkingLotState.get(lotName).getOccupancy();
                int earlierOccupancy = currentStateClone.get(lotName).getOccupancy();
                if (latestOccupancy != earlierOccupancy) {
                    logger.warning("Found state discrepancy when doing full update!\n");
                    logger.warning(String.format("previous: lot <%s>, occupancy <%d>", lotName, earlierOccupancy));
                    logger.warning(String.format("later: lot <%s>, occupancy <%d>", lotName, latestOccupancy));

                    //all clients must have been off by the same amount, let's update them
                    DiffUpdate diffUpdate = new DiffUpdate(lotName, latestOccupancy);
                    tellAllClients(diffUpdate);
                }
            }
        } else if (message instanceof NewLotState) {
            String lotName = ((NewLotState) message).getLot();
            int oldOccupancy = allParkingLotState.get(lotName).getOccupancy();
            int newOccupancy = redisUpdater.getParkingLotOccupancy(lotName);
            allParkingLotState.get(lotName).setOccupancy(newOccupancy);
            logger.info(String.format("lot %s occupancy updated from %d to: %d", lotName, oldOccupancy, newOccupancy));
            DiffUpdate diffUpdate = new DiffUpdate(lotName, newOccupancy);
            tellAllClients(diffUpdate);
        } else if (message instanceof CurrentStateRequest) {
            getSender().tell(Json.toJson(allParkingLotState), getSelf());
        } else if (message instanceof KeepAliveMessage) {
            tellAllClients(message);
        } else {
            unhandled(message);
        }

    }

    private void tellAllClients(Object object) {
        for (ActorRef client : clients) {
            client.tell(Json.toJson(object), getSelf());
        }
    }
}

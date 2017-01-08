package controllers;

import actors.messages.CurrentStateRequest;
import actors.messages.JsonMessage;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import annotations.RedisSubscriberRunnable;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import play.mvc.Controller;
import play.mvc.Result;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;
import services.RedisUpdater;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static akka.pattern.Patterns.ask;

@Singleton
public class ParkingController extends Controller {

    private final Logger logger;
    private final ActorSystem actorSystem;
    private final ActorRef clientManager;
    private final RedisUpdater redisUpdater;


    @Inject
    public ParkingController(Logger logger,
                             ActorSystem actorSystem,
                             @Named("clientManagerActor") ActorRef clientManager,
                             RedisUpdater redisUpdater,
                             @RedisSubscriberRunnable Runnable redisSubscriber) {
        this.logger = logger;
        this.clientManager = clientManager;
        this.actorSystem = actorSystem;
        this.redisUpdater = redisUpdater;
        this.actorSystem.scheduler().scheduleOnce(
                Duration.create(1, TimeUnit.NANOSECONDS),
                redisSubscriber,
                actorSystem.dispatcher()
        );
    }


    public Result update() {
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest("Expecting Json data");
        } else {
            String parkingLotName = json.findPath("name").textValue();
            int hincrby = json.findPath("diff").intValue();
            logger.info(String.format("Applying diff %d to lot %s", hincrby, parkingLotName));
            this.redisUpdater.updateParkingState(parkingLotName, hincrby);
            return ok();
        }
    }

    public CompletionStage<Result> status() {
        //taken straight from documentation
        //https://www.playframework.com/documentation/2.5.x/JavaAkka#Creating-and-using-actors
        return FutureConverters.toJava(
                ask(clientManager, new CurrentStateRequest(), 1000)
        ).thenApply(response -> ok(((JsonMessage) response).getJson()).as("application/json"));
    }
}

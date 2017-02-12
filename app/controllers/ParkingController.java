package controllers;

import actors.messages.ClientActorCreate;
import actors.messages.CurrentStateRequest;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import annotations.MidnightSyncRunnable;
import annotations.RedisSubscriberRunnable;
import annotations.WebsocketKeepAliveRunnable;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import models.IncomingLotUpdate;
import org.reactivestreams.Publisher;
import play.libs.F;
import play.libs.Json;
import play.mvc.*;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;
import services.RedisUpdater;
import utils.ExceptionUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static akka.pattern.Patterns.ask;

/**
 * This is the main class where all of our endpoints are defined
 * Also servers as instantiation point of 2 background services:
 *  1. redis subscriber thread
 *  2. the sync background thread in rare case of network drops
 *  to redis (which should never happen), we will monitor if this ever happens in production
 */
@Singleton
public class ParkingController extends Controller {

    private final Logger logger;
    private final ActorSystem actorSystem;
    private final ActorRef clientManager;
    private final RedisUpdater redisUpdater;
    private final Materializer materializer;


    @Inject
    public ParkingController(Logger logger,
                             ActorSystem actorSystem,
                             @Named("clientManagerActor") ActorRef clientManager,
                             RedisUpdater redisUpdater,
                             @RedisSubscriberRunnable Runnable redisSubscriber,
                             @MidnightSyncRunnable Runnable midnightSync,
                             @WebsocketKeepAliveRunnable Runnable keepalive,
                             Materializer materializer) {

        this.logger = logger;
        this.clientManager = clientManager;
        this.actorSystem = actorSystem;
        this.redisUpdater = redisUpdater;
        this.materializer = materializer;

        //schedule background thread to subscribe to Redis notifications
        this.actorSystem.scheduler().scheduleOnce(
                Duration.create(1, TimeUnit.NANOSECONDS),
                redisSubscriber,
                actorSystem.dispatcher()
        );

        //schedule keep alive thread to keep all websocket connections alive
        this.actorSystem.scheduler().scheduleOnce(
                Duration.create(1, TimeUnit.NANOSECONDS),
                keepalive,
                actorSystem.dispatcher()
        );

        //once a day, every midnight, perform a sync of redis state
        //just in case there was a missed notification day
        this.actorSystem.scheduler().schedule(
                Duration.create(timeTilMidnight(), TimeUnit.MILLISECONDS),
                Duration.create(24, TimeUnit.HOURS),
                midnightSync,
                actorSystem.dispatcher()
        );

    }

    /**
     * health check endpoint, should be up if server is up
     * @return 200 ok
     */
    public Result health() {
        return ok("Server is up!");
    }


    /**
     * raspberry pi update endpoint
     * get json from payload + send update to Redis
     * @return ok if json was parsed correctly + Redis update successful
     */
    public Result update() {
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest("Expecting Json data");
        } else {
            try {
                IncomingLotUpdate lotChange = Json.fromJson(json, IncomingLotUpdate.class);
                this.redisUpdater.updateParkingLotOccupancy(lotChange.getLot(), lotChange.getDiff());
                logger.info(String.format("updated lot %s by %d", lotChange.getLot(), lotChange.getDiff()));
                return ok();
            } catch (RuntimeException e) {
                logger.warning(ExceptionUtils.getStackTrace(e));
                return internalServerError();
            }
        }
    }

    /**
     * gets latest known state of all parking lots back in json
     * @return
     */
    public CompletionStage<Result> status() {
        //taken straight from documentation
        //https://www.playframework.com/documentation/2.5.x/JavaAkka#Creating-and-using-actors
        return FutureConverters.toJava(ask(clientManager, new CurrentStateRequest(), 1000)
        ).thenApply(response -> ok((JsonNode) response));
    }


    /**
     * websocket-actor is very confusing and not well documented
     * all of the code below this line was taken from:
     * https://github.com/playframework/play-websocket-java
     *
     * essentially, create a json websocket, tied to a new ClientActor
     * @return
     */
    public WebSocket ws() {
        return WebSocket.Json.acceptOrResult(request -> {
            final CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> future = wsFutureFlow(request);
            final CompletionStage<F.Either<Result, Flow<JsonNode, JsonNode, ?>>> stage = future.thenApplyAsync(F.Either::Right);
            return stage.exceptionally(this::logException);
        });
    }

    public CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> wsFutureFlow(Http.RequestHeader request) {
        // create an actor ref source and associated publisher for sink
        final Pair<ActorRef, Publisher<JsonNode>> pair = createWebSocketConnections();
        ActorRef webSocketOut = pair.first();
        Publisher<JsonNode> webSocketIn = pair.second();

        String id = String.valueOf(request._underlyingHeader().id());
        // Create a user actor off the request id and attach it to the source
        final CompletionStage<ActorRef> clientActorFuture = createClientActor(id, webSocketOut);

        // Once we have an actor available, create a flow...
        final CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> stage = clientActorFuture
                .thenApplyAsync(clientActor -> createWebSocketFlow(webSocketIn, clientActor));

        return stage;
    }

    public CompletionStage<ActorRef> createClientActor(String id, ActorRef webSocketOut) {
        // Use guice assisted injection to instantiate and configure the child actor.
        long timeoutMillis = 100L;
        return FutureConverters.toJava(
                ask(clientManager, new ClientActorCreate(id, webSocketOut), timeoutMillis)
        ).thenApply(stageObj -> (ActorRef) stageObj);
    }


    public Pair<ActorRef, Publisher<JsonNode>> createWebSocketConnections() {
        // Creates a source to be materialized as an actor reference.

        // Creating a source can be done through various means, but here we want
        // the source exposed as an actor so we can send it messages from other
        // actors.
        final Source<JsonNode, ActorRef> source = Source.actorRef(10, OverflowStrategy.dropTail());

        // Creates a sink to be materialized as a publisher.  Fanout is false as we only want
        // a single subscriber here.
        final Sink<JsonNode, Publisher<JsonNode>> sink = Sink.asPublisher(AsPublisher.WITHOUT_FANOUT);

        // Connect the source and sink into a flow, telling it to keep the materialized values,
        // and then kicks the flow into existence.
        final Pair<ActorRef, Publisher<JsonNode>> pair = source.toMat(sink, Keep.both()).run(materializer);
        return pair;
    }

    public Flow<JsonNode, JsonNode, NotUsed> createWebSocketFlow(Publisher<JsonNode> webSocketIn, ActorRef clientActor) {
        // http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#stream-materialization
        // http://doc.akka.io/docs/akka/current/scala/stream/stream-integrations.html#integrating-with-actors

        // source is what comes in: browser ws events -> play -> publisher -> clientActor
        // sink is what comes out:  clientActor -> websocketOut -> play -> browser ws events
        final Sink<JsonNode, NotUsed> sink = Sink.actorRef(clientActor, new Status.Success("success"));
        final Source<JsonNode, NotUsed> source = Source.fromPublisher(webSocketIn);
        final Flow<JsonNode, JsonNode, NotUsed> flow = Flow.fromSinkAndSource(sink, source);

        // Unhook the user actor when the websocket flow terminates
        // http://doc.akka.io/docs/akka/current/scala/stream/stages-overview.html#watchTermination
        return flow.watchTermination((ignore, termination) -> {
            termination.whenComplete((done, throwable) -> {
                logger.info(String.format("Terminating actor %s", clientActor));
                actorSystem.stop(clientActor);
            });

            return NotUsed.getInstance();
        });
    }


    public F.Either<Result, Flow<JsonNode, JsonNode, ?>> logException(Throwable throwable) {
        // https://docs.oracle.com/javase/tutorial/java/generics/capture.html
        logger.severe(String.format("Cannot create websocket: %s", throwable.toString()));
        Result result = Results.internalServerError("error");
        return F.Either.Left(result);
    }


    /**
     * gets the number of milliseconds from current execution time till midnight
     * @return milliseconds to midnight
     */
    private long timeTilMidnight() {
        //http://stackoverflow.com/a/32683993
        ZoneId zoneId = ZoneId.of( "America/Montreal" );
        ZonedDateTime now = ZonedDateTime.now( zoneId );
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        ZonedDateTime tomorrowStart = tomorrow.atStartOfDay( zoneId );
        return java.time.Duration.between(now, tomorrowStart).toMillis();
    }
}

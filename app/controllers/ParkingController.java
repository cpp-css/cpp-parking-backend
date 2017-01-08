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
import annotations.RedisSubscriberRunnable;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.reactivestreams.Publisher;
import play.libs.F;
import play.mvc.*;
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
    private final Materializer materializer;


    @Inject
    public ParkingController(Logger logger,
                             ActorSystem actorSystem,
                             @Named("clientManagerActor") ActorRef clientManager,
                             RedisUpdater redisUpdater,
                             @RedisSubscriberRunnable Runnable redisSubscriber,
                             Materializer materializer) {
        this.logger = logger;
        this.clientManager = clientManager;
        this.actorSystem = actorSystem;
        this.redisUpdater = redisUpdater;
        this.materializer = materializer;
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
        ).thenApply(response -> ok((JsonNode) response));
    }

    //websocket-actor is very confusing and not well documented
    //all of the code below this line was taken from: https://github.com/playframework/play-websocket-java
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
//                clientManager.tell(new ConnectionClosed(), clientActor);
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
}

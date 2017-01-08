package actors;

import actors.messages.ConnectionClosed;
import actors.messages.ConnectionCreated;
import actors.messages.JsonMessage;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * Represents a websocket connection to a client
 * https://www.playframework.com/documentation/2.5.x/JavaWebSockets
 * "Any messages received from the client will be sent to the actor,
 * and any messages sent to the actor supplied by Play will be sent to the client."
 */
public class ClientActor extends UntypedActor {
    //out represents the Websocket's outputstream to the client
    private final ActorRef out;

    private final ActorRef clientManager;

    public static Props props(ActorRef out, ActorRef clientManager) {
        return Props.create(ClientActor.class, out, clientManager);
    }

    public ClientActor(ActorRef out, ActorRef clientManager) {
        this.out = out;
        this.clientManager = clientManager;
        clientManager.tell(new ConnectionCreated(), getSelf());
    }

    @Override
    public void postStop() throws Exception {
        clientManager.tell(new ConnectionClosed(), getSelf());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof JsonMessage) {
            String json = ((JsonMessage) message).getJson();
            out.tell(json, getSelf());
        } else {
            unhandled(message);
        }
    }
}

package actors;

import actors.messages.ConnectionClosed;
import actors.messages.ConnectionCreated;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.assistedinject.Assisted;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * each clientactor has a client websocket connection
 * <p>
 * behavior should be as follows:
 * upon creation, send a message to clientManager indicating a new connection occurred
 * (this occurs in the preStart() hook)
 * <p>
 * whenever clientactor gets sent a jsonNode message, forward it out to the client websocket
 * <p>
 * whenever the websocket connection closes, this actor dies,
 * and should inform the clientmanager (occurs in the postStop() hook)
 */
public class ClientActor extends UntypedActor {
    //out represents the Websocket's outputstream to the client
    private final ActorRef out;

    private final ActorRef clientManager;

    @Inject
    public ClientActor(@Assisted ActorRef out,
                       @Named("clientManagerActor") ActorRef clientManager) {
        this.out = out;
        this.clientManager = clientManager;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        clientManager.tell(new ConnectionCreated(), getSelf());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        clientManager.tell(new ConnectionClosed(), getSelf());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof JsonNode) {
            out.tell(message, getSelf());
        } else {
            unhandled(message);
        }
    }

    public interface Factory {
        Actor create(ActorRef out);
    }
}

package actors;

import actors.messages.ConnectionClosed;
import actors.messages.ConnectionCreated;
import actors.messages.CurrentStateRequest;
import actors.messages.JsonNodeMessage;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.assistedinject.Assisted;

import javax.inject.Inject;
import javax.inject.Named;


public class ClientActor extends UntypedActor {
    //out represents the Websocket's outputstream to the client
    private final ActorRef out;

    private final ActorRef clientManager;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        clientManager.tell(new ConnectionCreated(), getSelf());
        clientManager.tell(new CurrentStateRequest(), getSelf());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        clientManager.tell(new ConnectionClosed(), clientManager);
    }

    @Inject
    public ClientActor(@Assisted ActorRef out,
                       @Named("clientManagerActor") ActorRef clientManager) {
        this.out = out;
        this.clientManager = clientManager;
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        //jsonmessage is from updated state from ClientManager
        if (message instanceof JsonNodeMessage) {
            JsonNode json = ((JsonNodeMessage) message).getJson();
            out.tell(json, getSelf());
        } else {
            unhandled(message);
        }
    }

    public interface Factory {
        Actor create(ActorRef out);
    }
}

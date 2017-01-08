package actors.messages;

import akka.actor.ActorRef;

/**
 * Created by brianzhao on 1/8/17.
 */
public class ClientActorCreate {
    private ActorRef out;
    private String id;

    public ClientActorCreate(String id, ActorRef out) {
        this.out = out;
        this.id = id;
    }

    public ActorRef getOut() {
        return out;
    }

    public String getId() {
        return id;
    }
}

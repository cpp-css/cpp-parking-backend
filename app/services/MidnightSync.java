package services;

import actors.messages.RefreshState;
import akka.actor.ActorRef;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Hacky sync just in case there is ever any deviation between
 * server state and redis state; theoretically impossible,
 * can maybe happen if there is network drop of redis notification
 */
public class MidnightSync implements Runnable {
    private ActorRef clientManager;

    @Inject
    public MidnightSync(@Named("clientManagerActor") ActorRef clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public void run() {
        clientManager.tell(new RefreshState(),ActorRef.noSender());
    }
}

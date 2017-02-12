package services;

import akka.actor.ActorRef;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import models.KeepAliveMessage;
import utils.ExceptionUtils;

import java.util.logging.Logger;

/**
 * Created by brianzhao on 2/12/17.
 */
public class KeepAlive implements Runnable {
    private final Logger logger;
    private final ActorRef clientManager;

    @Inject
    public KeepAlive(Logger logger,
                     @Named("clientManagerActor") ActorRef clientManager) {
        this.logger = logger;
        this.clientManager = clientManager;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(5000);
                clientManager.tell(new KeepAliveMessage(),ActorRef.noSender());
            } catch (InterruptedException e) {
                logger.severe(ExceptionUtils.getStackTrace(e));
            }
        }

    }
}

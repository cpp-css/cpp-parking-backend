import actors.ClientActor;
import actors.ClientManager;
import annotations.AllParkingState;
import annotations.MidnightSyncRunnable;
import annotations.RedisSubscriberRunnable;
import annotations.WebsocketKeepAliveRunnable;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import models.ParkingLot;
import play.libs.akka.AkkaGuiceSupport;
import providers.InitialParkingStateProvider;
import services.KeepAlive;
import services.MidnightSync;
import services.RedisSubscriber;

import java.util.Map;

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.
 *
 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 *
 * todo if we want to switch to slf4j
 * http://forkbomb-blog.de/2012/slf4j-logger-injection-with-guice
 */
public class Module extends AbstractModule implements AkkaGuiceSupport {

    @Override
    public void configure() {
        bindActor(ClientManager.class, "clientManagerActor");
        bind(new TypeLiteral<Map<String, ParkingLot>>(){})
                .annotatedWith(AllParkingState.class)
                .toProvider(InitialParkingStateProvider.class);
        bind(Runnable.class)
                .annotatedWith(RedisSubscriberRunnable.class)
                .to(RedisSubscriber.class);
        bind(Runnable.class)
                .annotatedWith(MidnightSyncRunnable.class)
                .to(MidnightSync.class);
        bind(Runnable.class)
                .annotatedWith(WebsocketKeepAliveRunnable.class)
                .to(KeepAlive.class);
        bindActorFactory(ClientActor.class, ClientActor.Factory.class);
    }

}

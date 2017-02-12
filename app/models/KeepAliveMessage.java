package models;

/**
 * Created by brianzhao on 2/12/17.
 */
public class KeepAliveMessage implements WebsocketMessage {
    private String header = KeepAliveMessage.class.getSimpleName();

    @Override
    public String getHeader() {
        return KeepAliveMessage.class.getSimpleName();
    }
}

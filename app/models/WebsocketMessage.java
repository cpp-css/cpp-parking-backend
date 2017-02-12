package models;

/**
 * All classes that implement this interface are intended to be messages
 * sent to the clientActor, and therefore back to the client websocket
 *
 * Should define a field "header" initialized w/classname
 */
public interface WebsocketMessage {
    public String getHeader();
}

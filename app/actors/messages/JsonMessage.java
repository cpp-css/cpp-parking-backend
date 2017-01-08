package actors.messages;

/**
 * Created by brianzhao on 1/7/17.
 */
public class JsonMessage {
    private final String json;

    public JsonMessage(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }
}

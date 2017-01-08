package actors.messages;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Created by brianzhao on 1/8/17.
 */
public class JsonNodeMessage {
    private final JsonNode json;

    public JsonNodeMessage(JsonNode json) {
        this.json = json;
    }

    public JsonNode getJson() {
        return json;
    }
}

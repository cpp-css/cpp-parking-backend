package models;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by brianzhao on 2/12/17.
 */
public class FullStateUpdate extends HashMap<String,Object> implements WebsocketMessage {

    public FullStateUpdate(Map<String, ParkingLot> input) {
        super(input);
        this.put("header", FullStateUpdate.class.getSimpleName());
    }

    @Override
    public String getHeader() {
        return (String) this.get("header");
    }
}

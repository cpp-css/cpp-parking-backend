package models;

/**
 * The diff object to send to the client
 */
public class DiffUpdate implements WebsocketMessage {
    private String lot;
    private int occupancy;
    private final String header = DiffUpdate.class.getSimpleName();

    public DiffUpdate(String lot, int occupancy) {
        this.lot = lot;
        this.occupancy = occupancy;
    }

    @Override
    public String getHeader() {
        return header;
    }

    public String getLot() {
        return lot;
    }

    public int getOccupancy() {
        return occupancy;
    }
}

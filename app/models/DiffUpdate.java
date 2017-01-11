package models;

/**
 * The diff object to send to the client
 */
public class DiffUpdate {
    private String lot;
    private int occupancy;

    public DiffUpdate(String lot, int occupancy) {
        this.lot = lot;
        this.occupancy = occupancy;
    }


    public String getLot() {
        return lot;
    }

    public int getOccupancy() {
        return occupancy;
    }
}

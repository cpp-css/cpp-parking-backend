package models;

/**
 * Incoming json posted from Raspberry Pi's
 */
public class IncomingLotUpdate {
    private String lot;
    private int diff;

    public IncomingLotUpdate() {
        //empty constructor required for Jackson
    }

    public IncomingLotUpdate(String lot, int change) {
        this.lot = lot;
        this.diff = change;
    }

    public String getLot() {
        return lot;
    }

    public int getDiff() {
        return diff;
    }

}

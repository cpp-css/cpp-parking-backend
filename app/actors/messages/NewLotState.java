package actors.messages;

/**
 * message saying that parking lot with name "lot" has been updated
 * sent by redisSubscriber to ClientManager
 */
public class NewLotState {
    private String lot;

    public NewLotState(String lot) {
        this.lot = lot;
    }

    public String getLot() {
        return lot;
    }
}

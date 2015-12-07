package cmuoh.ticketService.exception;

/**
 * Created by chibuike on 12/7/15.
 */
public class VenueLevelNotFoundException extends Exception {
    private final int venueLevel;

    public VenueLevelNotFoundException(int venueLevel) {
        super(String.format("Venue level %d was not found", venueLevel));
        this.venueLevel = venueLevel;
    }

    public int getVenueLevel() {
        return venueLevel;
    }
}

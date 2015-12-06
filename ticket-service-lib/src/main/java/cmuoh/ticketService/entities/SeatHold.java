package cmuoh.ticketService.entities;

import cmuoh.ticketService.dataAccess.ReservationHold;

/**
 * Created by chibuike on 12/3/15.
 */
public final class SeatHold {
    private final ReservationHold hold;

    protected SeatHold(ReservationHold hold) {
        this.hold = hold;
    }


    @Override
    public String toString() {
        return String.format("SeatHold[customerEmail=%s]", hold.getData());
    }
}

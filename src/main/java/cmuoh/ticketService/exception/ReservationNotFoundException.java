package cmuoh.ticketService.exception;

/**
 * Created by chibuike on 12/7/15.
 */
public class ReservationNotFoundException extends Exception {
    public ReservationNotFoundException(Integer seatHoldId, String email) {
        super(String.format("Reservation #%d for %s was not found", seatHoldId, email));
    }
}

package cmuoh.ticketService.exception;

/**
 * Created by chibuike on 12/7/15.
 */
public class InvalidSeatHoldRequestException extends Exception {
    public InvalidSeatHoldRequestException(String reason) {
        super(reason);
    }
}

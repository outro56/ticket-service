package cmuoh.ticketService.dataAccess;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Details about a reservation that is being held
 */
public class ReservationHold implements Comparable<ReservationHold> {

    static OffsetDateTime getOffsetDateTimeNow() {
        return OffsetDateTime.now(Clock.systemUTC());
    }

    final int reservationId;
    OffsetDateTime timeStamp;
    List<Integer> slots;
    Object data;

    private boolean confirmed;

    /**
     * Initialize the reservation hold object
     */
    ReservationHold(int reservationId, List<Integer> slots) {
        this.timeStamp = getOffsetDateTimeNow();
        this.reservationId = reservationId;
        this.slots = slots;
    }

    @Override
    public String toString() {
        return String.format("ReservationHold[reservationId=%d, timeStamp=%s, confirmed=%s, heldReservations={%s}]",
                getReservationId(), getTimeStamp(), isConfirmed(),
                slots.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "))
        );
    }

    public int compareTo(ReservationHold o) {
        return getTimeStamp().compareTo(o.getTimeStamp());
    }

    /**
     *
     * @return when the reservation was made
     */
    public OffsetDateTime getTimeStamp() {
        return timeStamp;
    }

    /**
     *
     * @return the reservation ID
     */
    public int getReservationId() {
        return reservationId;
    }

    /**
     * Is the reservation confirmed or not
     *
     * @return
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    protected void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    /**
     * The held slots for this reservation
     *
     * @return
     */
    public Collection<Integer> getSlots() {
        return Collections.unmodifiableCollection(slots);
    }

    /**
     * Data associated with the resolve hold object
     * @return
     */
    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

package cmuoh.ticketService.dataAccess;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Details about a reservation that is being held
 *
 * @implNote This object is thread safe
 */
public final class ReservationHold implements Comparable<ReservationHold> {
    final int reservationId;
    final int resourceId;
    final Collection<Integer> slots;
    final AtomicBoolean confirmed = new AtomicBoolean();
    final AtomicReference<Object> data = new AtomicReference<>();
    final OffsetDateTime timeStamp;

    /**
     * Initialize the reservation hold object
     */
    ReservationHold(int reservationId, int resourceId, List<Integer> slots, Object data, OffsetDateTime timeStamp) {
        this.timeStamp = timeStamp;
        this.reservationId = reservationId;
        this.resourceId = resourceId;
        this.data.set(data);
        this.slots = Collections.unmodifiableCollection(slots);
    }

    @Override
    public String toString() {
        return String.format("%s[reservationId=%d, timeStamp=%s, confirmed=%s, slots={%s}]",
                this.getClass().getName(),
                getReservationId(),
                getTimeStamp(),
                isConfirmed(),
                slots.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "))
        );
    }

    public int compareTo(ReservationHold rhs) {
        return getTimeStamp().compareTo(rhs.getTimeStamp());
    }

    /**
     *
     * @return identifier for the resource where this hold was placed
     */
    public int getResourceId() {
        return resourceId;
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
     * The held slots for this reservation
     *
     * @return
     */
    public Collection<Integer> getSlots() {
        return slots;
    }

    /**
     * Is the reservation confirmed or not
     *
     * @return
     */
    public boolean isConfirmed() {
        return confirmed.get();
    }

    /**
     * Data associated with the reservation hold object
     *
     * @return
     */
    public <T> T getData() {
        return (T) data.get();
    }

    public <T> void setData(T data) {
        this.data.set(data);
    }
}

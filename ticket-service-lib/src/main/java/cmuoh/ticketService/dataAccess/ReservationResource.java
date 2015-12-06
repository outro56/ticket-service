package cmuoh.ticketService.dataAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages reservation claims for a resource. A resource has limited number capacity slots; each slot can be in either
 * of two states available or held. Held slots expired (lazily) after a given TTL unless they are specifically marked
 * as reserved.
 */
final class ReservationResource {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationResource.class);

    public static int DEFAULT_TTL_SECONDS = 30;

    String tag = "<no-tag>";

    final AtomicInteger reservationIdCounter;
    final Map<Integer, ReservationHold> heldReservations;

    int numConfirmedHolds = 0;
    int reservationTTL;
    int maxCapacity;

    final List<Integer> availableSlots;
    final Queue<ReservationHold> expirationQueue;

    /**
     * Initialize the resource object
     *
     * @param reservationIdCounter
     * @param capacity
     * @param reservationTTL
     */
    public ReservationResource(AtomicInteger reservationIdCounter, int capacity, int reservationTTL)
            throws Exception {
        if (reservationIdCounter == null) {
            throw new NullPointerException("reservationIdCounter");
        }

        if (capacity < 0) {
            throw new IllegalArgumentException("capacity");
        } else if (capacity == 0) {
            LOG.warn("{} resource created with zero capacity", getTag());
        }

        if (reservationTTL <= 0) {
            throw new IllegalArgumentException("reservationTTL");
        }

        this.reservationIdCounter = reservationIdCounter;
        this.maxCapacity = capacity;
        this.reservationTTL = reservationTTL;

        boolean initCapacityValid = capacity > 0;
        availableSlots = initCapacityValid ? new ArrayList<>(capacity) : new ArrayList<>();
        heldReservations = initCapacityValid ? new HashMap<>(capacity) : new HashMap<>();
        expirationQueue = initCapacityValid ? new PriorityQueue<>(capacity) : new PriorityQueue<>();

        if (initCapacityValid) {
            addCapacity(capacity);
        }
    }

    public ReservationResource(AtomicInteger reservationIdCounter, int capacity, int reservationTTL,
                               String tag) throws Exception {
        this(reservationIdCounter, capacity, reservationTTL);
        setTag(tag);
    }

    /**
     * Initializes or adds additional capacity to hold more reservations
     *
     * @param slotCount number of slots to add
     */
    private void addCapacity(int slotCount) {
        for (int i = 0, slotNum = availableSlots.size(); i < slotCount; ++i) {
            availableSlots.add(slotNum++);
        }
        LOG.debug("{} increased capacity by {}", getTag(), slotCount);
    }

    @Override
    public String toString() {
        return String.format("ReservationResource[tag=%s, holds=%s, confirmed=%s, available=%s]",
                getTag(), countHolds(), countConfirmed(), countAvailable());
    }

    /**
     * Tag for the reservation resource, useful for debugging purpose
     *
     * @return
     */
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * The maximum number reservation slots that can either be available or held
     *
     * @return reservation capacity for the resource
     */
    public int maxCapacity() {
        return maxCapacity;
    }

    /**
     * Reservation slots that are being held and won't expire
     *
     * @return number of confirmed reservation slots
     */
    public int countConfirmed() {
        return numConfirmedHolds;
    }

    /**
     *
     * @return number of available slots
     */
    public int countAvailable() {
        return availableSlots.size();
    }

    /**
     *
     * @return number of slots that are being held including confirmed
     */
    public int countHolds() {
        return heldReservations.size();
    }

    /**
     * Find and return a reservation by ID
     *
     * @param reservationId
     * @return existing reservation else NULL if not found
     */
    public ReservationHold getReservation(int reservationId) {
        ensureAvailable(0);
        return heldReservations.get(reservationId);
    }

    /**
     * Confirms an existing reservation that is being held
     *
     * @param reservationId
     * @return TRUE if the reservation was successfully confirmed else FALSE
     */
    public boolean confirmReservation(int reservationId) {
        ReservationHold hold = getReservation(reservationId);
        if (hold == null) {
            LOG.debug("{} reservation #{} was not found or has expired", getTag(), reservationId);
            return false;
        }

        if (!hold.isConfirmed()) {
            hold.setConfirmed(true);
            ++numConfirmedHolds;
            LOG.info("{} confirmed {} holds for reservation #{}",
                    new Object[] { getTag(), hold.slots.size(), reservationId });
        } else {
            LOG.info("{} reservation #{} is already confirmed", getTag(), reservationId);
        }

        return true;
    }

    /**
     * Holds a number of reservation slots
     *
     * @param slotCount the number of slots to hold
     * @return reservation hold details iff successfully held else NULL
     */
    public ReservationHold requestHold(int slotCount) {
        if (slotCount < 1) {
            LOG.warn("{} invalid number of hold ({}) requested", new Object[] { getTag(), slotCount });
            return null;
        }

        if (!ensureAvailable(slotCount)) {
            LOG.warn("{} available reservation slots ({}) cannot hold {}.",
                    new Object[] { getTag(), countAvailable(), slotCount });
            return null;
        }

        int pos = availableSlots.size() - slotCount;
        List<Integer> freeList = availableSlots.subList(pos, availableSlots.size());

        ReservationHold hold = new ReservationHold(reservationIdCounter.incrementAndGet(), new ArrayList<>(freeList));
        heldReservations.put(hold.getReservationId(), hold);
        expirationQueue.add(hold);

        freeList.clear();

        LOG.info("{} added {} hold for reservation #{}",
                new Object[] { getTag(), slotCount, hold.getReservationId() });

        return hold;
    }

    /**
     * Checks that there are enough available slots for the requested number of holds. When there
     * are not enough available slots, first attempts to reclaim expired holds to free up slots.
     *
     * @param slotCount
     * @return TRUE if there is enough availability else FALSE
     */
    private boolean ensureAvailable(int slotCount) {
        if (slotCount != 0 && availableSlots.size() >= slotCount) {
            return true;
        }

        collectExpiredHolds();

        return availableSlots.size() >= slotCount;
    }

    /**
     * Marks the slot for expired holds as available when they are not confirmed
     *
     * @return the number of slots marked as available
     */
    private int collectExpiredHolds() {
        final OffsetDateTime expired = ReservationHold.getOffsetDateTimeNow().minusSeconds(reservationTTL);

        int freed = 0;
        while (!expirationQueue.isEmpty()) {
            ReservationHold hold = expirationQueue.peek();

            if (hold.getTimeStamp().compareTo(expired) > 0) {
                break;
            }

            if (!hold.isConfirmed()) {
                if (heldReservations.remove(hold.getReservationId()) != null) {
                    availableSlots.addAll(hold.getSlots());
                }
                freed += hold.getSlots().size();
            }

            LOG.info("{} reservation #{} (created: {}) with {} slots has expired",
                    new Object[] { getTag(), hold.getReservationId(), hold.getTimeStamp(), hold.getSlots().size() });

            expirationQueue.remove();
        }

        if (freed > 0) {
            LOG.debug("{} increase available slots by {}", getTag(), freed);
        }

        return freed;
    }
}

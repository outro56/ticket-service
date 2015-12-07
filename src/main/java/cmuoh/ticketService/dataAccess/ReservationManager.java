package cmuoh.ticketService.dataAccess;

import cmuoh.ticketService.util.HoldTimeStampProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages reservation claims on a resource.
 *
 * Managed resources have limited number of capacity slots; each slot can be in either of two states available or held.
 * Held slots expired (lazily) after a given TTL unless they are specifically marked as reserved.
 *
 * @implNote The public API of this object is thread safe
 */
public final class ReservationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationManager.class);

    final int resourceId;
    final int maxCapacity;

    final ArrayBlockingQueue<Integer> availableSlots;
    final PriorityBlockingQueue<ReservationHold> expirationQueue;
    final ConcurrentHashMap<Integer, ReservationHold> holdReservations;
    final AtomicInteger countConfirmedHolds = new AtomicInteger();

    protected AtomicInteger reservationIdCounter = new AtomicInteger(215);
    protected HoldTimeStampProvider holdTimeStampProvider;
    protected String tag;

    /**
     * Initialize the resource object
     *
     * @param resourceId
     * @param capacity
     * @param holdTimeStampProvider
     * @param tag
     */
    public ReservationManager(int resourceId, int capacity, HoldTimeStampProvider holdTimeStampProvider, String tag)
            throws Exception {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity");
        }

        if (holdTimeStampProvider == null) {
            throw new NullPointerException("holdTimeStampProvider");
        }

        this.holdTimeStampProvider = holdTimeStampProvider;
        this.resourceId = resourceId;
        this.maxCapacity = capacity;
        this.tag = tag;

        // ensure that reallocation don't happen
        availableSlots = new ArrayBlockingQueue<Integer>(capacity);
        holdReservations = new ConcurrentHashMap<>(capacity);
        expirationQueue = new PriorityBlockingQueue<>(capacity, Comparator.reverseOrder());

        for (int i = 0, slotNum = availableSlots.size(); i < capacity; ++i) {
            availableSlots.add(slotNum++);
        }

        LOG.debug("{} initialized with capacity[{}]", tag, capacity);
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, tag=%s, holds=%s, confirmed=%s, available=%s]",
                this.getClass().getName(),
                resourceId,
                tag,
                holdReservations.size(),
                countConfirmedHolds.get(),
                availableSlots.size());
    }

    /**
     *
     * @return an identifier for the resource
     */
    public int getResourceId() {
        return resourceId;
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
     * The reservation ID counter
     *
     * @return
     */
    public AtomicInteger getReservationIdCounter() {
        return reservationIdCounter;
    }

    public void setReservationIdCounter(AtomicInteger reservationIdCounter) throws Exception {
        if (reservationIdCounter == null) {
            throw new NullPointerException("reservationIdCounter");
        }

        this.reservationIdCounter = reservationIdCounter;
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
     *
     * @return number of reserved holds that won't expire
     */
    public int countConfirmedHolds() {
        return countConfirmedHolds.get();
    }

    /**
     *
     * @return reservation holds on this resource
     */
    public List<ReservationHold> getAllReservations() {
        collectExpiredHolds();
        return new ArrayList<>(holdReservations.values());
    }

    /**
     *
     * @return number of available slots that are not being held
     */
    public int countAvailableSlots() {
        collectExpiredHolds();
        return availableSlots.size();
    }

    /**
     * Find and return a reservation by ID
     *
     * @param holdId
     * @return existing reservation hold else NULL if not found
     */
    public ReservationHold getReservation(int holdId) {
        collectExpiredHolds(); // make sure it is expired yet
        return holdReservations.get(holdId);
    }

    /**
     * Confirms an existing hold so that it won't expire
     *
     * @param holdId
     * @return TRUE if the reservation hold was successfully confirmed else FALSE
     */
    public boolean confirmHold(int holdId) {
        ReservationHold hold = getReservation(holdId);
        if (hold == null) {
            LOG.debug("{} reservation #{} was not found or has expired", tag, holdId);
            return false;
        }

        if (!hold.isConfirmed()) {
            hold.confirmed.set(true);

            // ** RACE CONDITION CHECK **
            // Verify that the reservation hold wasn't collected in between
            if (!holdReservations.containsKey(holdId)) {
                return false;
            }

            countConfirmedHolds.incrementAndGet();
            LOG.info("{} confirmed {} slots for reservation hold #{}",
                    tag, hold.slots.size(), holdId);
        }

        return true;
    }

    /**
     * Holds a number of reservation slots
     *
     * @param slotCount the number of slots to hold
     * @param data data to store for the reservation
     * @return reservation hold details iff successfully held else NULL
     */
    public ReservationHold requestHold(int slotCount, Object data) {
        if (slotCount < 1) {
            LOG.warn("{} invalid number of hold slot ({}) requested", tag, slotCount);
            return null;
        }

        final List<Integer> claimedSlots = new ArrayList<>(slotCount);
        ReservationHold hold = null;
        try {
            // try to drain desired number of slots from the queue
            availableSlots.drainTo(claimedSlots, slotCount);
            if (claimedSlots.size() < slotCount) {
                collectExpiredHolds(claimedSlots, slotCount);
            }

            if (claimedSlots.size() < slotCount) {
                LOG.warn("{} unable to satisfy reservation request for {} slot", tag, slotCount);
                return null;
            }

            hold = buildReservationHold(data, claimedSlots);
            holdReservations.put(hold.getReservationId(), hold);
            expirationQueue.offer(hold);

            if (LOG.isInfoEnabled()) {
                LOG.info("{} hold #{} claimed slot [{}]", tag,
                        hold.getReservationId(),
                        hold.getSlots().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(", ")));
            }

            return hold;

        } finally {
            // TODO: adding to holdReservation/expirationQueue could also fail
            if (hold == null) {
                availableSlots.addAll(claimedSlots);
            }
        }
    }

    private ReservationHold buildReservationHold(Object data, List<Integer> claimedSlots) {
        return new ReservationHold(reservationIdCounter.incrementAndGet(), resourceId, claimedSlots, data,
                holdTimeStampProvider.getTimeStamp());
    }

    /**
     * Processes and removes expired hold reservations and returns their held slots to the available list
     */
    protected void collectExpiredHolds() {
        collectExpiredHolds(availableSlots, 0);
    }

    /**
     * Processes and removes expired hold reservations. The held slots are returned in a caller specified drain queue
     *
     * @param drainSlotsTo
     * @param desiredSlotCount Stop processing holds after given them of slots are collected
     * @return
     */
    protected void collectExpiredHolds(Collection<Integer> drainSlotsTo, int desiredSlotCount) {
        while (holdIsExpired(expirationQueue.peek())) {
            // remove the hold from the queue
            ReservationHold hold = expirationQueue.poll();

            // ** RACE CONDITION CHECK **
            // the hold was removed from the top of the queue might not be the same one that we checked at the start
            // of the while loop so need to verify that we didn't remove a hold that has not yet expired
            if (!holdIsExpired(hold)) {
                if (hold != null) {
                    expirationQueue.offer(hold);
                }
                break;
            }

            // NOTE: confirmed holds are not drained
            if (!hold.isConfirmed()) {
                if (holdReservations.remove(hold.getReservationId()) != null) {
                    for (Integer slot : hold.getSlots()) {
                        if (desiredSlotCount == 0 || drainSlotsTo.size() < desiredSlotCount) {
                            drainSlotsTo.add(slot);
                        } else {
                            availableSlots.add(slot);
                        }
                    }
                    if (desiredSlotCount > 0 && drainSlotsTo.size() >= desiredSlotCount) {
                        break;
                    }
                }

                LOG.info("{} hold #{} (created: {}) with {} slots has expired",
                        tag, hold.getReservationId(), hold.getTimeStamp(), hold.getSlots().size());
            }
        }
    }

    private boolean holdIsExpired(ReservationHold hold) {
        if (hold == null) {
            return false;
        }
        return holdTimeStampProvider.isExpired(hold.getTimeStamp());
    }
}

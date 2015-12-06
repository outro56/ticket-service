package cmuoh.ticketService.dataAccess;

import org.junit.Before;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit test for ReservationResource.
 */
public class ReservationResourceTests {

    private final AtomicInteger reservationIdCounter = new AtomicInteger(4572);

    @Before
    public void setup() {
    }

    @Test
    public void resourceSizeDoesNotGrow() throws Exception {
        // resources with different slot counts
        ReservationResource[] resources = new ReservationResource[] {
                createResource(0, "test-resource-0"),
                createResource(1, "test-resource-1"),
                createResource(2, "test-resource-2")
        };

        // NOTE: request pattern includes invalid slot counts
        Integer[] requestPattern = new Integer[] { 0, 1, -1, 0, 1, 2, 0, 2 };

        ArrayList<Integer> invalidSlotCount = new ArrayList<>();
        Collections.addAll(invalidSlotCount, -1, 0);

        for (int slotCount : requestPattern) {
            for (ReservationResource resource : resources) {
                boolean shouldFail = slotCount > resource.maxCapacity() ||
                        slotCount > resource.countAvailable() ||
                        invalidSlotCount.contains(slotCount);
                ReservationHold hold = addHold(resource, slotCount);
                if (shouldFail) {
                    assertNull(hold);
                } else {
                    assertNotNull(hold);
                }
            }
        }
    }

    @Test
    public void allHoldRequestsSatisfied() throws Exception {
        Integer[] requestPattern = new Integer[] { 1, 2, 1, 7, 3, 5, 4, 6 };
        final int capacity = Arrays.stream(requestPattern).reduce(0, (x, y) -> x + y);

        ReservationResource resource = createResource(capacity, "test-resource");

        for (int slotCount : requestPattern) {
            ReservationHold hold = addHold(resource, slotCount);
            assertNotNull(hold);
        }
    }

    @Test
    public void someHoldRequestsSatisfied() throws Exception {
        final int capacity = 5;
        final Integer[] requestPattern = new Integer[] { 1, 2, 5, 1, 3, 1, 3 };

        // hold requests should fail at the following indices
        final ArrayList<Integer> failedRequestIdx = new ArrayList<>();
        Collections.addAll(failedRequestIdx, 2, 4, 6);

        ReservationResource resource = createResource(capacity, "test-resource");

        for (int i = 0; i < requestPattern.length; ++i) {
            int slotCount = requestPattern[i];
            ReservationHold hold = addHold(resource, slotCount);
            if (failedRequestIdx.contains(i)) {
                assertNull(hold);
            } else {
                assertNotNull(hold);
            }
        }
    }

    @Test
    public void expiredSlotsAreCollectedWhenNeeded() throws Exception {
        final int capacity = 5;
        final OffsetDateTime expireAt = ReservationHold.getOffsetDateTimeNow()
                .minusSeconds(ReservationResource.DEFAULT_TTL_SECONDS * 2);
        final Integer[] requestPattern = new Integer[] { 1, 3, 4, 1, 3 };

        // expired hold requests should only get collected at the following indices
        final ArrayList<Integer> collectHoldsAfterIdx = new ArrayList<>();
        Collections.addAll(collectHoldsAfterIdx, 2, 4);

        ReservationResource resource = createResource(capacity, "test-resource");

        ArrayList<ReservationHold> holds = new ArrayList<>();

        for (int i = 0; i < requestPattern.length; ++i) {
            int slotCount = requestPattern[i];

            ReservationHold hold;
            if (collectHoldsAfterIdx.contains(i)) {
                // enable collecting of expired holds
                resource.reservationTTL = ReservationResource.DEFAULT_TTL_SECONDS;

                hold = addHold(resource, slotCount);

                verifyExpiredHolds(resource, holds, false);
                holds.clear();

            } else {
                // disable collecting of expired holds
                resource.reservationTTL = Integer.MAX_VALUE;

                hold = addHold(resource, slotCount);

                // verify existing holds have not been collected
                for (ReservationHold existingHold : holds) {
                    assertNotNull(resource.getReservation(existingHold.getReservationId()));
                }
            }

            assertNotNull(hold);

            hold.timeStamp = expireAt;
            holds.add(hold);
        }
    }

    private ReservationResource createResource(int capacity, String tag) throws Exception {
        ReservationResource resource = new ReservationResource(reservationIdCounter, capacity, Integer.MAX_VALUE, tag);

        assertEquals(resource.countConfirmed(), 0);
        assertEquals(resource.countHolds(), 0);
        assertEquals(resource.expirationQueue.size(), 0);

        assertEquals(resource.countAvailable(), capacity);

        // verify the slot IDs were actually created
        for (int slotId = 0; slotId < capacity; slotId++) {
            assertTrue(resource.availableSlots.contains(slotId));
        }

        return resource;
    }

    private ReservationHold addHold(ReservationResource resource, int numHolds) {
        final int prevCountAvailable = resource.countAvailable();
        final int prevCountHolds = resource.countHolds();
        final int prevExpirationQueueCount = resource.expirationQueue.size();
        final int prevReservationId = reservationIdCounter.get();

        ReservationHold hold = resource.requestHold(numHolds);
        if (hold != null) {

            // verify new holds are being stored correctly
            assertNotNull(resource.getReservation(hold.getReservationId()));
            assertTrue(resource.expirationQueue.contains(hold));

            // verify that new IDs are being generated
            assertEquals(reservationIdCounter.get(), hold.getReservationId());
            assertNotEquals(prevReservationId, reservationIdCounter);

            // verify held slots are not available
            assertEquals(numHolds, hold.getSlots().size());

            for (Integer slot : hold.getSlots()) {
                assertFalse(resource.availableSlots.contains(slot));
            }

        } else {
            assertEquals(prevCountAvailable, resource.countAvailable());
            assertEquals(prevCountHolds, resource.countHolds());
            assertEquals(prevExpirationQueueCount, resource.expirationQueue.size());
        }

        return hold;
    }

    private static void verifyExpiredHolds(ReservationResource resource, ArrayList<ReservationHold> expiredHolds,
                                           boolean slotIsAvailable) {
        for (ReservationHold hold : expiredHolds) {
            assertNull(resource.getReservation(hold.getReservationId()));
            assertFalse(resource.expirationQueue.contains(hold));

            if (slotIsAvailable) {
                for (Integer slot : hold.getSlots()) {
                    assertFalse(resource.availableSlots.contains(slot));
                }
            }
        }
    }
}

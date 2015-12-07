package cmuoh.ticketService.dataAccess;

import cmuoh.ticketService.util.HoldTimeStampProvider;
import org.junit.Before;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit test for ReservationManager.
 */
public class ReservationManagerTest {

    private static int resourceCounter = 0;

    HoldTimeStampProvider nonExpiringHoldTimeStampProvider;
    HoldTimeStampProvider expiredHoldTimeStampProvider;

    @Before
    public void setup() throws Exception {
        nonExpiringHoldTimeStampProvider = new HoldTimeStampProvider() {
            @Override
            public boolean isExpired(OffsetDateTime time) {
                return false;
            }
        };

        expiredHoldTimeStampProvider = new HoldTimeStampProvider() {
            @Override
            public boolean isExpired(OffsetDateTime time) {
                return true;
            }
        };
    }

    @Test
    public void resourceSizeDoesNotGrow() throws Exception {
        // resources with different slot counts
        ReservationManager[] resources = new ReservationManager[] {
                createResource(1, nonExpiringHoldTimeStampProvider),
                createResource(2, nonExpiringHoldTimeStampProvider),
                createResource(3, nonExpiringHoldTimeStampProvider)
        };

        // NOTE: request pattern includes invalid slot counts
        Integer[] requestPattern = new Integer[] { 0, 1, -1, 0, 1, 2, 0, 2 };

        ArrayList<Integer> invalidSlotCount = new ArrayList<>();
        Collections.addAll(invalidSlotCount, -1, 0);

        for (int slotCount : requestPattern) {
            for (ReservationManager resource : resources) {
                boolean shouldFail = slotCount > resource.maxCapacity() ||
                        slotCount > resource.availableSlots.size() ||
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
    public void allHoldRequestsAreSatisfied() throws Exception {
        Integer[] requestPattern = new Integer[] { 1, 2, 1, 7, 3, 5, 4, 6 };
        final int capacity = Arrays.stream(requestPattern).reduce(0, (x, y) -> x + y);

        ReservationManager resource = createResource(capacity, nonExpiringHoldTimeStampProvider);

        for (int slotCount : requestPattern) {
            ReservationHold hold = addHold(resource, slotCount);
            assertNotNull(hold);
        }
    }

    @Test
    public void someHoldRequestsAreSatisfied() throws Exception {
        final int capacity = 5;
        final Integer[] requestPattern = new Integer[] { 1, 2, 5, 1, 3, 1, 3 };

        // hold requests should fail at the following indices
        final ArrayList<Integer> failedRequestIdx = new ArrayList<>();
        Collections.addAll(failedRequestIdx, 2, 4, 6);

        ReservationManager resource = createResource(capacity, nonExpiringHoldTimeStampProvider);

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
    public void slotsForExpiredHoldsAreAvailable() throws Exception {
        final int capacity = 5;
        final int[] requestPattern = new int[] { 1, 1, 1, 2 };

        ReservationManager resource = createResource(capacity, expiredHoldTimeStampProvider);

        ArrayList<ReservationHold> existingHolds = new ArrayList<>();

        for (int slotCount : requestPattern) {
            ReservationHold hold = resource.requestHold(slotCount, null);
            assertNotNull(hold);
            existingHolds.add(hold);
        }

        resource.collectExpiredHolds();

        assertEquals(capacity, resource.availableSlots.size());
        verifyExpiredHolds(resource, existingHolds, true);
    }

    @Test
    public void expiredHoldsAreCollectedWhenNeeded() throws Exception {
        final int capacity = 5;
        final Integer[] requestPattern = new Integer[] { 1, 3, 4, 1, 3 };

        // expired hold requests should only get collected at the following indices
        final ArrayList<Integer> collectHoldAtIdx = new ArrayList<>();
        Collections.addAll(collectHoldAtIdx, 2, 4);

        ReservationManager resource = createResource(capacity, expiredHoldTimeStampProvider);

        ArrayList<ReservationHold> holds = new ArrayList<>();

        for (int i = 0; i < requestPattern.length; ++i) {
            int slotCount = requestPattern[i];

            ReservationHold hold;
            if (collectHoldAtIdx.contains(i)) {
                // enable collecting of expired holds
                resource.holdTimeStampProvider = expiredHoldTimeStampProvider;
                resource.collectExpiredHolds();

                // disable collecting of expired holds
                resource.holdTimeStampProvider = nonExpiringHoldTimeStampProvider;

                // add the new hold
                hold = addHold(resource, slotCount);

                verifyExpiredHolds(resource, holds, false);
                holds.clear();

            } else {
                // disable collecting of expired holds
                resource.holdTimeStampProvider = nonExpiringHoldTimeStampProvider;

                hold = addHold(resource, slotCount);

                // verify existing holds have not been collected
                for (ReservationHold existingHold : holds) {
                    assertNotNull(resource.getReservation(existingHold.getReservationId()));
                }
            }

            assertNotNull(hold);
            holds.add(hold);
        }
    }

    @Test
    public void expiredHoldsAreCollectedByTime() throws Exception {
        final int capacity = 5;
        final int[] requestPattern = new int[] { 1, 1, 1, 2 };
        final int expireHoldsBeforeIdx = 3;

        ReservationManager resource = createResource(capacity, expiredHoldTimeStampProvider);

        ArrayList<ReservationHold> holds = new ArrayList<>();

        for (int i = 0; i < requestPattern.length; ++i) {
            int slotCount = requestPattern[i];

            resource.holdTimeStampProvider =  (i < expireHoldsBeforeIdx) ?
                    expiredHoldTimeStampProvider :
                    nonExpiringHoldTimeStampProvider;

            ReservationHold hold = resource.requestHold(slotCount, null);
            assertNotNull(hold);

            holds.add(hold);
        }

        resource.holdTimeStampProvider = expiredHoldTimeStampProvider;

        for (int i = 0; i < holds.size(); ++i) {
            ReservationHold hold = holds.get(i);
            if (i > expireHoldsBeforeIdx) {
                assertNotNull(resource.getReservation(hold.getReservationId()));
            } else {
                assertNull(resource.getReservation(hold.getReservationId()));
            }
        }
    }

    @Test
    public void confirmedHoldSlotsAreNotCollected() throws Exception {
        final int capacity = 5;
        final int[] requestPattern = new int[] { 1, 1, 1, 2 };

        // hold requests at these indices should not expire
        final ArrayList<Integer> confirmHoldAtIdx = new ArrayList<>();
        Collections.addAll(confirmHoldAtIdx, 2, 3);

        final int expectedNumOfConfirmedSlots = confirmHoldAtIdx.stream()
                .map(i -> requestPattern[i])
                .reduce(0, (x, y) -> x + y);

        ReservationManager resource = createResource(capacity, nonExpiringHoldTimeStampProvider);

        ArrayList<ReservationHold> confirmedHolds = new ArrayList<>();

        for (int i = 0; i < requestPattern.length; ++i) {
            int slotCount = requestPattern[i];

            ReservationHold hold = resource.requestHold(slotCount, null);
            assertNotNull(hold);

            if (confirmHoldAtIdx.contains(i)) {
                assertTrue(resource.confirmHold(hold.getReservationId()));
                confirmedHolds.add(hold);
            }
        }

        resource.holdTimeStampProvider = expiredHoldTimeStampProvider;
        resource.collectExpiredHolds();

        assertEquals(capacity - expectedNumOfConfirmedSlots, resource.availableSlots.size());

        for (ReservationHold hold : confirmedHolds) {
            assertNotNull(resource.getReservation(hold.getReservationId()));
        }
    }

    /**
     * Creates a resource where the holds are never collected
     *
     * @param capacity
     * @return
     * @throws Exception
     */
    private ReservationManager createResource(int capacity, HoldTimeStampProvider holdTimeStampProvider
    ) throws Exception {
        String tag = String.format("resource-%d", resourceCounter);
        ReservationManager resource = new ReservationManager(++resourceCounter, capacity, holdTimeStampProvider, tag);

        assertEquals(resource.countConfirmedHolds(), 0);
        assertEquals(resource.getAllReservations().size(), 0);
        assertEquals(resource.expirationQueue.size(), 0);
        assertEquals(resource.availableSlots.size(), capacity);

        // verify the slot IDs were actually created
        for (int slotId = 0; slotId < capacity; slotId++) {
            assertTrue(resource.availableSlots.contains(slotId));
        }

        return resource;
    }

    private ReservationHold addHold(ReservationManager resource, int numHolds) {
        final int prevCountAvailable = resource.availableSlots.size();
        final int prevExpirationQueueCount = resource.expirationQueue.size();
        final int prevCountHolds = resource.getAllReservations().size();
        final int prevReservationId = resource.getReservationIdCounter().get();

        ReservationHold hold = resource.requestHold(numHolds, null);
        if (hold != null) {

            // verify new holds are being stored correctly
            assertNotNull(resource.getReservation(hold.getReservationId()));
            assertTrue(resource.expirationQueue.contains(hold));

            // verify that new IDs are being generated
            assertEquals(resource.getReservationIdCounter().get(), hold.getReservationId());
            assertNotEquals(prevReservationId, resource.getReservationIdCounter().get());

            // verify held slots are not available
            assertEquals(numHolds, hold.getSlots().size());

            for (Integer slot : hold.getSlots()) {
                assertFalse(resource.availableSlots.contains(slot));
            }

        } else {
            assertEquals(prevCountHolds, resource.getAllReservations().size());
            assertEquals(prevCountAvailable, resource.availableSlots.size());
            assertEquals(prevExpirationQueueCount, resource.expirationQueue.size());
            assertEquals(prevReservationId, resource.getReservationIdCounter().get());
        }

        return hold;
    }

    private static void verifyExpiredHolds(ReservationManager resource, ArrayList<ReservationHold> expiredHolds,
                                           boolean slotIsAvailable) {
        for (ReservationHold hold : expiredHolds) {
            assertNull(resource.getReservation(hold.getReservationId()));
            assertFalse(resource.expirationQueue.contains(hold));

            if (slotIsAvailable) {
                for (Integer slot : hold.getSlots()) {
                    assertTrue(resource.availableSlots.contains(slot));
                }
            }
        }
    }
}

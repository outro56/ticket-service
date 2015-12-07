package cmuoh.ticketService.util;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.TimeZone;

/**
 * Helper class to return time stamp information
 */
public class HoldTimeStampProvider {
    public static int DEFAULT_TTL_SECONDS = 30;

    private int holdTtl;

    public HoldTimeStampProvider() throws Exception {
        this(DEFAULT_TTL_SECONDS);
    }

    public HoldTimeStampProvider(int holdTtl) throws Exception {

        if (holdTtl <= 0) {
            throw new IllegalArgumentException("reservationTTL");
        }

        this.holdTtl = holdTtl;
    }

    public int getHoldTtl() {
        return holdTtl;
    }

    public TimeZone getTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    public OffsetDateTime getTimeStamp() {
        return OffsetDateTime.now(Clock.systemUTC());
    }

    public boolean isExpired(OffsetDateTime time) {
        final OffsetDateTime expiredAt = getTimeStamp().minusSeconds(holdTtl);

        return time.compareTo(expiredAt) <= 0;
    }
}

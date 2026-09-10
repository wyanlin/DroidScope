package com.droidscope.local;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class EventTicketStoreTest {
    public static void main(String[] args) {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-05T00:00:00Z"));
        EventTicketStore store = new EventTicketStore(clock);
        String ticket = store.issue();

        if (!ticket.matches("[A-Za-z0-9_-]+")) throw new AssertionError("ticket must be URL safe");
        if (!store.consume(ticket)) throw new AssertionError("new ticket must be consumable");
        if (store.consume(ticket)) throw new AssertionError("ticket must be single use");

        String expiredTicket = store.issue();
        clock.advance(java.time.Duration.ofSeconds(31));
        if (store.consume(expiredTicket)) throw new AssertionError("expired ticket must not be consumable");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) { this.instant = instant; }
        void advance(java.time.Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

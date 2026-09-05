package com.droidscope.local;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class EventTicketStore {
    private static final Duration LIFETIME = Duration.ofSeconds(30);
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> tickets = new ConcurrentHashMap<>();

    public EventTicketStore(Clock clock) {
        this.clock = clock;
    }

    public String issue() {
        String ticket = SessionToken.create();
        tickets.put(ticket, clock.instant().plus(LIFETIME));
        return ticket;
    }

    public boolean consume(String ticket) {
        Instant expiry = tickets.remove(ticket);
        return expiry != null && clock.instant().isBefore(expiry);
    }
}

package com.droidscope.local;

import com.droidscope.adb.AdbDevice;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DeviceEventBroker {
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public Subscription subscribe() {
        Subscription subscription = new Subscription();
        subscriptions.add(subscription);
        return subscription;
    }

    public void publish(List<AdbDevice> devices) {
        String event = DeviceJson.encode(devices);
        for (Subscription subscription : subscriptions) {
            subscription.offer(event);
        }
    }

    public final class Subscription implements AutoCloseable {
        private final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(1);
        private boolean closed;

        public String await(Duration timeout) throws InterruptedException {
            return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            closed = true;
            subscriptions.remove(this);
            events.clear();
        }

        private void offer(String event) {
            if (closed) return;
            events.poll();
            events.offer(event);
        }
    }
}

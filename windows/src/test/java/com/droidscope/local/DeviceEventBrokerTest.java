package com.droidscope.local;

import com.droidscope.adb.AdbDevice;

import java.time.Duration;
import java.util.List;

public final class DeviceEventBrokerTest {
    public static void main(String[] args) throws Exception {
        DeviceEventBroker broker = new DeviceEventBroker();
        DeviceEventBroker.Subscription first = broker.subscribe();
        DeviceEventBroker.Subscription second = broker.subscribe();

        broker.publish(List.of(new AdbDevice("ONE", AdbDevice.State.DEVICE)));
        assertEquals("{\"devices\":[{\"serial\":\"ONE\",\"state\":\"device\",\"ready\":true}]}", first.await(Duration.ofMillis(100)));
        assertEquals("{\"devices\":[{\"serial\":\"ONE\",\"state\":\"device\",\"ready\":true}]}", second.await(Duration.ofMillis(100)));

        first.close();
        broker.publish(List.of(new AdbDevice("TWO", AdbDevice.State.OFFLINE)));
        assertEquals(null, first.await(Duration.ZERO));
        assertEquals("{\"devices\":[{\"serial\":\"TWO\",\"state\":\"offline\",\"ready\":false}]}", second.await(Duration.ofMillis(100)));

        broker.publish(List.of(new AdbDevice("OLD", AdbDevice.State.OFFLINE)));
        broker.publish(List.of(new AdbDevice("LATEST", AdbDevice.State.DEVICE)));
        assertEquals("{\"devices\":[{\"serial\":\"LATEST\",\"state\":\"device\",\"ready\":true}]}", second.await(Duration.ofMillis(100)));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}

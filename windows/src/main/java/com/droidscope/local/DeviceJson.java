package com.droidscope.local;

import com.droidscope.adb.AdbDevice;

import java.util.List;

public final class DeviceJson {
    private DeviceJson() {}

    public static String encode(List<AdbDevice> devices) {
        StringBuilder json = new StringBuilder("{\"devices\":[");
        for (int index = 0; index < devices.size(); index++) {
            if (index > 0) json.append(',');
            AdbDevice device = devices.get(index);
            json.append("{\"serial\":\"").append(escape(device.serial()))
                    .append("\",\"state\":\"").append(device.state().name().toLowerCase())
                    .append("\",\"ready\":").append(device.isReady()).append('}');
        }
        return json.append("]}").toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }
}

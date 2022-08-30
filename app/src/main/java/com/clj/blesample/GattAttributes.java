package com.clj.blesample;

import java.util.HashMap;

public class GattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"; // 客户端特性配置描述符 用于设置通知/指示
    public static String BLE_UART_SERVICE = "d973f2e0-b19e-11e2-9e96-080020f29a66";
    public static String BLE_UART_TX = "d973f2e1-b19e-11e2-9e96-9e08000c9a66";
    public static String BLE_UART_RX = "d973f2e2-b19e-11e2-9e96-0800200c9a66";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put(BLE_UART_SERVICE, "BLE UART Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        attributes.put(BLE_UART_TX, "BLE UART TX");
        attributes.put(BLE_UART_RX,"BLE UART RX");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}

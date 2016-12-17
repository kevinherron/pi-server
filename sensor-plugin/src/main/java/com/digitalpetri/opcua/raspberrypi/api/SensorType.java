package com.digitalpetri.opcua.raspberrypi.api;

public interface SensorType {

    /**
     * Create a new {@link Sensor}.
     *
     * @param context the {@link SensorContext} for which the {@link Sensor} should be created.
     * @return a new {@link Sensor} for the provided {@link SensorContext}.
     */
    Sensor createSensor(SensorContext context) throws Exception;

}

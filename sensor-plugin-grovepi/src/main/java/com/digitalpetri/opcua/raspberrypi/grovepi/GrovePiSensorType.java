package com.digitalpetri.opcua.raspberrypi.grovepi;

import com.digitalpetri.opcua.raspberrypi.api.Sensor;
import com.digitalpetri.opcua.raspberrypi.api.SensorContext;
import com.digitalpetri.opcua.raspberrypi.api.SensorType;
import com.digitalpetri.opcua.raspberrypi.grovepi.sensors.LightSensor;
import com.digitalpetri.opcua.raspberrypi.grovepi.sensors.RotaryAngleSensor;
import com.digitalpetri.opcua.raspberrypi.grovepi.sensors.TempAndHumiditySensor;
import com.typesafe.config.Config;

public class GrovePiSensorType implements SensorType {

    public Sensor createSensor(SensorContext sensorContext) throws Exception {
        Config config = sensorContext.getConfig();

        String groveType = config.getString("sensor.grove.grove-type");

        switch (groveType) {
            case TempAndHumiditySensor.GROVE_TYPE:
                return new TempAndHumiditySensor(GrovePiContext.get(), sensorContext);

            case LightSensor.GROVE_TYPE:
                return new LightSensor(GrovePiContext.get(), sensorContext);

            case RotaryAngleSensor.GROVE_TYPE:
                return new RotaryAngleSensor(GrovePiContext.get(), sensorContext);
        }

        throw new Exception("unknown sensor.grove.grove-type value: " + groveType);
    }

}

package com.digitalpetri.opcua.raspberrypi.grovepi.sensors;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.digitalpetri.grovepi.GroveDigitalPin;
import com.digitalpetri.grovepi.sensors.TemperatureAndHumiditySensor;
import com.digitalpetri.opcua.raspberrypi.api.SensorContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiSensor;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempAndHumiditySensor extends GrovePiSensor {

    public static final String GROVE_TYPE = "temperature-humidity";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final long updateRate;

    private final UaVariableNode temperatureNode;
    private final UaVariableNode humidityNode;

    private final TemperatureAndHumiditySensor sensor;

    public TempAndHumiditySensor(GrovePiContext grovePiContext, SensorContext sensorContext) {
        super(grovePiContext, sensorContext);

        UaNodeManager addressSpace = sensorContext.getServer().getNodeManager();

        updateRate = sensorContext.getConfig().getDuration(
            "sensor.grove.update-rate", TimeUnit.MILLISECONDS);

        int pinNumber = sensorContext.getConfig().getInt("sensor.grove.pin-number");

        sensor = new TemperatureAndHumiditySensor(
            grovePiContext.getGrovePi(),
            GroveDigitalPin.values()[pinNumber],
            0
        );

        NodeId temperatureNodeId = sensorContext.nodeId("Temperature");
        NodeId humidityNodeId = sensorContext.nodeId("Humidity");

        temperatureNode = new UaVariableNode.UaVariableNodeBuilder(sensorContext.getServer())
            .setNodeId(temperatureNodeId)
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Temperature"))
            .setDisplayName(LocalizedText.english("Temperature"))
            .setDataType(Identifiers.Float)
            .build();

        addressSpace.addNode(temperatureNode);
        getSensorNode().addComponent(temperatureNode);

        humidityNode = new UaVariableNode.UaVariableNodeBuilder(sensorContext.getServer())
            .setNodeId(humidityNodeId)
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Humidity"))
            .setDisplayName(LocalizedText.english("Humidity"))
            .setDataType(Identifiers.Float)
            .build();

        addressSpace.addNode(humidityNode);
        getSensorNode().addComponent(humidityNode);

        readSensor();
    }

    private void readSensor() {
        sensor.getTemperatureAndHumidity().whenComplete((v, ex) -> {
            if (v != null) {
                DataValue temp = new DataValue(new Variant(v.getTemperature()));
                DataValue humidity = new DataValue(new Variant(v.getHumidity()));

                temperatureNode.setValue(temp);
                humidityNode.setValue(humidity);
            } else {
                logger.error("Error reading temperature and humidity.", ex);
            }

            ScheduledExecutorService executor = getGrovePiContext().getExecutor();

            executor.schedule(this::readSensor, updateRate, TimeUnit.MILLISECONDS);
        });
    }

}

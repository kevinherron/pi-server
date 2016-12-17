package com.digitalpetri.opcua.raspberrypi.grovepi.sensors;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.digitalpetri.grovepi.GroveAnalogPin;
import com.digitalpetri.opcua.raspberrypi.api.SensorContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiSensor;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RotaryAngleSensor extends GrovePiSensor {

    public static final String GROVE_TYPE = "rotary-angle";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final UaVariableNode sensorValueNode;
    private final UaVariableNode voltageNode;
    private final UaVariableNode degreesNode;

    private final ServerNodeMap nodeMap;
    private final long updateRate;
    private final com.digitalpetri.grovepi.sensors.RotaryAngleSensor sensor;

    public RotaryAngleSensor(GrovePiContext grovePiContext, SensorContext sensorContext) {
        super(grovePiContext, sensorContext);

        nodeMap = sensorContext.getServer().getNodeMap();

        updateRate = sensorContext.getConfig().getDuration(
            "sensor.grove.update-rate", TimeUnit.MILLISECONDS);

        int pinNumber = sensorContext.getConfig().getInt("sensor.grove.pin-number");

        sensor = new com.digitalpetri.grovepi.sensors.RotaryAngleSensor(
            grovePiContext.getGrovePi(),
            GroveAnalogPin.values()[pinNumber]
        );

        sensorValueNode = new UaVariableNode.UaVariableNodeBuilder(nodeMap)
            .setNodeId(sensorContext.nodeId("Sensor Value"))
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Sensor Value"))
            .setDisplayName(LocalizedText.english("Sensor Value"))
            .setDataType(Identifiers.Double)
            .build();

        nodeMap.addNode(sensorValueNode);
        getSensorNode().addComponent(sensorValueNode);

        voltageNode = new UaVariableNode.UaVariableNodeBuilder(nodeMap)
            .setNodeId(sensorContext.nodeId("Voltage"))
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Voltage"))
            .setDisplayName(LocalizedText.english("Voltage"))
            .setDataType(Identifiers.Double)
            .build();

        nodeMap.addNode(voltageNode);
        getSensorNode().addComponent(voltageNode);

        degreesNode = new UaVariableNode.UaVariableNodeBuilder(nodeMap)
            .setNodeId(sensorContext.nodeId("Degrees"))
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Degrees"))
            .setDisplayName(LocalizedText.english("Degrees"))
            .setDataType(Identifiers.Double)
            .build();

        nodeMap.addNode(degreesNode);
        getSensorNode().addComponent(degreesNode);

        readSensor();
    }

    private void readSensor() {
        sensor.getRotaryAngle().whenComplete((v, ex) -> {
            if (v != null) {
                DataValue sensorValue = new DataValue(new Variant(v.getSensorValue()));
                DataValue voltage = new DataValue(new Variant(v.getVoltage()));
                DataValue degrees = new DataValue(new Variant(v.getDegrees()));

                sensorValueNode.setValue(sensorValue);
                voltageNode.setValue(voltage);
                degreesNode.setValue(degrees);
            } else {
                logger.error("Error reading rotary angle.", ex);
            }

            ScheduledExecutorService executor = getGrovePiContext().getExecutor();

            executor.schedule(this::readSensor, updateRate, TimeUnit.MILLISECONDS);
        });
    }


}

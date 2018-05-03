package com.digitalpetri.opcua.raspberrypi.grovepi.sensors;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.digitalpetri.grovepi.GroveAnalogPin;
import com.digitalpetri.grovepi.sensors.GroveLightSensor;
import com.digitalpetri.opcua.raspberrypi.api.SensorContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiContext;
import com.digitalpetri.opcua.raspberrypi.grovepi.GrovePiSensor;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpace;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LightSensor extends GrovePiSensor {

    public static final String GROVE_TYPE = "light";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final UaVariableNode colorTemperatureNode;

    private final long updateRate;
    private final GroveLightSensor sensor;

    public LightSensor(GrovePiContext grovePiContext, SensorContext sensorContext) {
        super(grovePiContext, sensorContext);

        AddressSpace addressSpace = sensorContext.getServer().getAddressSpace();

        updateRate = sensorContext.getConfig().getDuration(
            "sensor.grove.update-rate", TimeUnit.MILLISECONDS);

        int pinNumber = sensorContext.getConfig().getInt("sensor.grove.pin-number");

        sensor = new GroveLightSensor(
            grovePiContext.getGrovePi(),
            GroveAnalogPin.values()[pinNumber]
        );

        colorTemperatureNode = new UaVariableNode.UaVariableNodeBuilder(sensorContext.getServer())
            .setNodeId(sensorContext.nodeId("Color Temperature"))
            .setBrowseName(new QualifiedName(sensorContext.getNamespaceIndex(), "Color Temperature"))
            .setDisplayName(LocalizedText.english("Color Temperature"))
            .setDataType(Identifiers.Double)
            .build();

        addressSpace.addNode(colorTemperatureNode);
        getSensorNode().addComponent(colorTemperatureNode);

        readSensor();
    }

    private void readSensor() {
        sensor.getValue().whenComplete((v, ex) -> {
            if (v != null) {
                DataValue temp = new DataValue(new Variant(v));

                colorTemperatureNode.setValue(temp);
            } else {
                logger.error("Error reading color temperature.", ex);
            }

            ScheduledExecutorService executor = getGrovePiContext().getExecutor();

            executor.schedule(this::readSensor, updateRate, TimeUnit.MILLISECONDS);
        });
    }

}

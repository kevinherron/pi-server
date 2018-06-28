/*
 * Copyright 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.opcua.raspberrypi.nodes;

import java.util.Set;

import com.digitalpetri.opcua.raspberrypi.GpioConfig.OutputConfig;
import com.digitalpetri.opcua.raspberrypi.PiNamespace;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogOutput;
import com.pi4j.io.gpio.RaspiPin;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class AnalogOutputNode extends UaVariableNode {

    private final GpioController controller = GpioFactory.getInstance();

    private final GpioPinAnalogOutput output;

    public AnalogOutputNode(UaNodeContext context,
                            NodeId nodeId,
                            QualifiedName browseName,
                            LocalizedText displayName,
                            OutputConfig outputConfig) {

        super(context, nodeId, browseName, displayName);

        output = controller.provisionAnalogOutputPin(
            RaspiPin.getPinByAddress(outputConfig.getPin()),
            outputConfig.getName(),
            outputConfig.getValue()
        );

        setDataType(Identifiers.Double);
        setValue(new DataValue(new Variant(outputConfig.getValue())));

        Set<AccessLevel> accessLevels = AccessLevel.READ_WRITE;
        UByte accessLevel = ubyte(AccessLevel.getMask(accessLevels));
        setAccessLevel(accessLevel);
        setUserAccessLevel(accessLevel);
    }

    @Override
    public synchronized void setValue(DataValue value) {
        double d = (double) value.getValue().getValue();

        output.setValue(d);
    }

    @Override
    public DataValue getValue() {
        double value = output.getValue();

        return new DataValue(new Variant(value));
    }

    public static AnalogOutputNode fromOutput(PiNamespace namespace, OutputConfig outputConfig) {
        UShort namespaceIndex = namespace.getNamespaceIndex();

        return new AnalogOutputNode(
            namespace.getNodeContext(),
            new NodeId(namespaceIndex, "Pin" + outputConfig.getPin()),
            new QualifiedName(namespaceIndex, outputConfig.getName()),
            LocalizedText.english(outputConfig.getName()),
            outputConfig
        );
    }

}

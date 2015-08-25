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

import java.util.EnumSet;

import com.digitalpetri.opcua.raspberrypi.GpioConfig;
import com.digitalpetri.opcua.raspberrypi.GpioConfig.OutputConfig;
import com.digitalpetri.opcua.raspberrypi.PiNamespace;
import com.digitalpetri.opcua.sdk.core.AccessLevel;
import com.digitalpetri.opcua.sdk.server.api.UaNamespace;
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.QualifiedName;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UByte;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;

import static com.digitalpetri.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class DigitalOutputNode extends UaVariableNode {

    private final GpioController controller = GpioFactory.getInstance();

    private final GpioPinDigitalOutput output;

    public DigitalOutputNode(UaNamespace nodeManager,
                             NodeId nodeId,
                             QualifiedName browseName,
                             LocalizedText displayName,
                             OutputConfig outputConfig) {

        super(nodeManager, nodeId, browseName, displayName);

        boolean high = outputConfig.getValue() > 0;

        output = controller.provisionDigitalOutputPin(
                GpioConfig.int2pin(outputConfig.getPin()),
                outputConfig.getName(),
                high ? PinState.HIGH : PinState.LOW
        );

        setDataType(Identifiers.Boolean);
        setValue(new DataValue(new Variant(high)));

        EnumSet<AccessLevel> accessLevels = AccessLevel.READ_WRITE;
        UByte accessLevel = ubyte(AccessLevel.getMask(accessLevels));
        setAccessLevel(accessLevel);
        setUserAccessLevel(accessLevel);
    }

    @Override
    public synchronized void setValue(DataValue value) {
        boolean high = (boolean) value.getValue().getValue();

        output.setState(high);
    }

    @Override
    public DataValue getValue() {
        boolean high = output.getState().isHigh();

        return new DataValue(new Variant(high));
    }

    public static DigitalOutputNode fromOutput(PiNamespace namespace, OutputConfig outputConfig) {
        UShort namespaceIndex = namespace.getNamespaceIndex();

        return new DigitalOutputNode(namespace,
                new NodeId(namespaceIndex, "Pin" + outputConfig.getPin()),
                new QualifiedName(namespaceIndex, outputConfig.getName()),
                LocalizedText.english(outputConfig.getName()),
                outputConfig
        );
    }

}

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

import com.digitalpetri.opcua.raspberrypi.GpioConfig;
import com.digitalpetri.opcua.raspberrypi.GpioConfig.InputConfig;
import com.digitalpetri.opcua.raspberrypi.PiNamespace;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class AnalogInputNode extends UaVariableNode {

    private final GpioController controller = GpioFactory.getInstance();

    private final GpioPinAnalogInput input;

    public AnalogInputNode(ServerNodeMap nodeMap,
                           NodeId nodeId,
                           QualifiedName browseName,
                           LocalizedText displayName,
                           InputConfig inputConfig) {

        super(nodeMap, nodeId, browseName, displayName);

        input = controller.provisionAnalogInputPin(
            GpioConfig.int2pin(inputConfig.getPin()),
            inputConfig.getName()
        );

        input.addListener((GpioPinListenerAnalog) event -> {
            double value = event.getValue();

            setValue(new DataValue(new Variant(value)));
        });

        double value = input.getValue();

        setDataType(Identifiers.Double);
        setValue(new DataValue(new Variant(value)));
    }

    public static AnalogInputNode fromInput(PiNamespace namespace, InputConfig inputConfig) {
        UShort namespaceIndex = namespace.getNamespaceIndex();

        return new AnalogInputNode(
            namespace.getNodeMap(),
            new NodeId(namespaceIndex, "Pin" + inputConfig.getPin()),
            new QualifiedName(namespaceIndex, inputConfig.getName()),
            LocalizedText.english(inputConfig.getName()),
            inputConfig
        );
    }

}

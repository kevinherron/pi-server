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

import com.digitalpetri.opcua.raspberrypi.GpioConfig.InputConfig;
import com.digitalpetri.opcua.raspberrypi.PiNamespace;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class DigitalInputNode extends UaVariableNode {

    private final GpioController controller = GpioFactory.getInstance();

    private final GpioPinDigitalInput input;

    public DigitalInputNode(UaNodeContext context,
                            NodeId nodeId,
                            QualifiedName browseName,
                            LocalizedText displayName,
                            InputConfig inputConfig) {

        super(context, nodeId, browseName, displayName);

        boolean pullDown = inputConfig.getResistance().equalsIgnoreCase("pull-down");

        input = controller.provisionDigitalInputPin(
            RaspiPin.getPinByAddress(inputConfig.getPin()),
            inputConfig.getName(),
            pullDown ? PinPullResistance.PULL_DOWN : PinPullResistance.PULL_UP
        );

        input.addListener((GpioPinListenerDigital) event -> {
            PinState state = event.getState();

            setValue(new DataValue(new Variant(state.isHigh())));
        });

        PinState state = input.getState();

        setDataType(Identifiers.Boolean);
        setValue(new DataValue(new Variant(state.isHigh())));
    }

    public static DigitalInputNode fromInput(PiNamespace namespace, InputConfig inputConfig) {
        UShort namespaceIndex = namespace.getNamespaceIndex();

        return new DigitalInputNode(
            namespace.getNodeContext(),
            new NodeId(namespaceIndex, "Pin" + inputConfig.getPin()),
            new QualifiedName(namespaceIndex, inputConfig.getName()),
            LocalizedText.english(inputConfig.getName()),
            inputConfig
        );
    }

}

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

package com.digitalpetri.opcua.raspberrypi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.digitalpetri.opcua.raspberrypi.GpioConfig.InputConfig;
import com.digitalpetri.opcua.raspberrypi.GpioConfig.OutputConfig;
import com.digitalpetri.opcua.raspberrypi.nodes.AnalogInputNode;
import com.digitalpetri.opcua.raspberrypi.nodes.AnalogOutputNode;
import com.digitalpetri.opcua.raspberrypi.nodes.DigitalInputNode;
import com.digitalpetri.opcua.raspberrypi.nodes.DigitalOutputNode;
import com.digitalpetri.opcua.sdk.core.Reference;
import com.digitalpetri.opcua.sdk.server.NamespaceManager;
import com.digitalpetri.opcua.sdk.server.api.DataItem;
import com.digitalpetri.opcua.sdk.server.api.MonitoredItem;
import com.digitalpetri.opcua.sdk.server.api.UaNamespace;
import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.digitalpetri.opcua.sdk.server.model.UaObjectNode;
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.sdk.server.util.SubscriptionModel;
import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.StatusCodes;
import com.digitalpetri.opcua.stack.core.UaException;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.QualifiedName;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.digitalpetri.opcua.stack.core.types.enumerated.TimestampsToReturn;
import com.digitalpetri.opcua.stack.core.types.structured.ReadValueId;
import com.digitalpetri.opcua.stack.core.types.structured.WriteValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.digitalpetri.opcua.sdk.server.util.StreamUtil.opt2stream;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

public class PiNamespace implements UaNamespace {

    public static final String NAMESPACE_URI = "urn:digitalpetri:pi-server:pi-namespace";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();


    private final UaObjectNode gpioFolder;
    private final SubscriptionModel subscriptionModel;

    private final PiServer server;
    private final UShort namespaceIndex;

    public PiNamespace(PiServer server, UShort namespaceIndex) {
        this.server = server;
        this.namespaceIndex = namespaceIndex;

        gpioFolder = UaObjectNode.builder(this)
                .setNodeId(new NodeId(namespaceIndex, "GPIO"))
                .setBrowseName(new QualifiedName(namespaceIndex, "GPIO"))
                .setDisplayName(LocalizedText.english("GPIO"))
                .setTypeDefinition(Identifiers.FolderType)
                .build();

        nodes.put(gpioFolder.getNodeId(), gpioFolder);

        server.getServer().getUaNamespace().getObjectsFolder().addReference(new Reference(
                Identifiers.ObjectsFolder,
                Identifiers.Organizes,
                gpioFolder.getNodeId().expanded(),
                gpioFolder.getNodeClass(),
                true
        ));

        addGpioNodes();

        subscriptionModel = new SubscriptionModel(server.getServer(), this);
    }

    private void addGpioNodes() {
        /*
         * Output Nodes
         */
        List<OutputConfig> outputs = server.getGpioConfig().getOutputs();

        outputs.stream().flatMap(output -> {
            String outputType = output.getType();

            UaVariableNode node;

            if ("digital".equalsIgnoreCase(outputType)) {
                node = DigitalOutputNode.fromOutput(this, output);
            } else if ("analog".equalsIgnoreCase(outputType)) {
                node = AnalogOutputNode.fromOutput(this, output);
            } else {
                logger.warn("Unknown output type: {}. Expected \"digital\" or \"analog\".", outputType);
                node = null;
            }

            return opt2stream(Optional.ofNullable(node));
        }).forEach(n -> {
            nodes.put(n.getNodeId(), n);

            gpioFolder.addReference(new Reference(
                    gpioFolder.getNodeId(),
                    Identifiers.Organizes,
                    n.getNodeId().expanded(),
                    n.getNodeClass(),
                    true
            ));
        });

        /*
         * Input Nodes
         */
        List<InputConfig> inputs = server.getGpioConfig().getInputs();

        inputs.stream().flatMap(input -> {
            String inputType = input.getType();

            UaVariableNode node;

            if ("digital".equalsIgnoreCase(inputType)) {
                node = DigitalInputNode.fromInput(this, input);
            } else if ("analog".equalsIgnoreCase(inputType)) {
                node = AnalogInputNode.fromInput(this, input);
            } else {
                logger.warn("Unknown input type: {}. Expected \"digital\" or \"analog\".", inputType);
                node = null;
            }

            return opt2stream(Optional.ofNullable(node));
        }).forEach(n -> {
            nodes.put(n.getNodeId(), n);

            gpioFolder.addReference(new Reference(
                    gpioFolder.getNodeId(),
                    Identifiers.Organizes,
                    n.getNodeId().expanded(),
                    n.getNodeClass(),
                    true
            ));
        });
    }

    @Override
    public UShort getNamespaceIndex() {
        return namespaceIndex;
    }

    @Override
    public String getNamespaceUri() {
        return NAMESPACE_URI;
    }

    @Override
    public CompletableFuture<List<Reference>> getReferences(NodeId nodeId) {
        List<Reference> references = getNode(nodeId)
                .map(UaNode::getReferences)
                .orElse(ImmutableList.<Reference>of());

        return CompletableFuture.completedFuture(references);
    }

    @Override
    public void read(ReadContext context,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     List<ReadValueId> readValueIds) {

        List<DataValue> results = newArrayListWithCapacity(readValueIds.size());

        for (ReadValueId readValueId : readValueIds) {
            NodeId nodeId = readValueId.getNodeId();
            UInteger attributeId = readValueId.getAttributeId();
            String indexRange = readValueId.getIndexRange();

            DataValue value = getNode(nodeId)
                    .map(n -> n.readAttribute(attributeId.intValue(), timestamps, indexRange))
                    .orElse(new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown)));

            results.add(value);
        }

        context.complete(results);
    }

    @Override
    public void write(WriteContext context, List<WriteValue> writeValues) {
        NamespaceManager namespaceManager = server.getServer().getNamespaceManager();

        List<StatusCode> results = newArrayListWithCapacity(writeValues.size());

        for (WriteValue writeValue : writeValues) {
            NodeId nodeId = writeValue.getNodeId();
            UInteger attributeId = writeValue.getAttributeId();
            DataValue value = writeValue.getValue();
            String indexRange = writeValue.getIndexRange();

            StatusCode result = getNode(nodeId).map(n -> {
                try {
                    n.writeAttribute(namespaceManager, attributeId, value, indexRange);

                    return StatusCode.GOOD;
                } catch (UaException e) {
                    return e.getStatusCode();
                }
            }).orElse(new StatusCode(StatusCodes.Bad_NodeIdUnknown));

            results.add(result);
        }

        context.complete(results);
    }

    @Override
    public void addNode(UaNode node) {
        nodes.put(node.getNodeId(), node);
    }

    @Override
    public Optional<UaNode> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<UaNode> getNode(ExpandedNodeId nodeId) {
        return nodeId.local().flatMap(this::getNode);
    }

    @Override
    public Optional<UaNode> removeNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.remove(nodeId));
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}

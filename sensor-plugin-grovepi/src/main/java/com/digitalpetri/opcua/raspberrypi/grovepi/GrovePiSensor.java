package com.digitalpetri.opcua.raspberrypi.grovepi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.digitalpetri.opcua.raspberrypi.api.Sensor;
import com.digitalpetri.opcua.raspberrypi.api.SensorContext;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpace;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;

public abstract class GrovePiSensor implements Sensor {

    private final AddressSpace addressSpace;
    private final SubscriptionModel subscriptionModel;
    private final UaObjectNode sensorNode;

    private final GrovePiContext grovePiContext;
    private final SensorContext sensorContext;

    public GrovePiSensor(GrovePiContext grovePiContext, SensorContext sensorContext) {
        this.grovePiContext = grovePiContext;
        this.sensorContext = sensorContext;

        addressSpace = sensorContext.getServer().getAddressSpace();

        subscriptionModel = new SubscriptionModel(sensorContext.getServer(), this);

        sensorNode = new UaObjectNode.UaObjectNodeBuilder(sensorContext.getServer())
            .setNodeId(sensorContext.getRootNodeId())
            .setBrowseName(sensorContext.qualifiedName(sensorContext.getName()))
            .setDisplayName(LocalizedText.english(sensorContext.getName()))
            .setTypeDefinition(Identifiers.FolderType)
            .build();

        addressSpace.put(sensorNode.getNodeId(), sensorNode);
    }

    @Override
    public UShort getNamespaceIndex() {
        return sensorContext.getNamespaceIndex();
    }

    @Override
    public String getNamespaceUri() {
        return sensorContext.getNamespaceUri();
    }

    @Override
    public CompletableFuture<List<Reference>> browse(AccessContext context, NodeId nodeId) {
        ServerNode node = addressSpace.get(nodeId);

        if (node != null) {
            return completedFuture(node.getReferences());
        } else {
            CompletableFuture<List<Reference>> f = new CompletableFuture<>();
            f.completeExceptionally(new UaException(StatusCodes.Bad_NodeIdUnknown));
            return f;
        }
    }

    @Override
    public void read(ReadContext context,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     List<ReadValueId> readValueIds) {

        List<DataValue> results = newArrayListWithCapacity(readValueIds.size());

        for (ReadValueId id : readValueIds) {
            DataValue value;

            ServerNode node = addressSpace.get(id.getNodeId());

            if (node != null) {
                value = node.readAttribute(
                    new AttributeContext(context),
                    id.getAttributeId(),
                    timestamps,
                    id.getIndexRange(),
                    QualifiedName.NULL_VALUE
                );
            } else {
                value = new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
            }

            results.add(value);
        }

        context.complete(results);
    }

    @Override
    public void write(WriteContext context, List<WriteValue> writeValues) {
        List<StatusCode> results = writeValues.stream().map(value -> {
            if (addressSpace.containsKey(value.getNodeId())) {
                return new StatusCode(StatusCodes.Bad_NotWritable);
            } else {
                return new StatusCode(StatusCodes.Bad_NodeIdUnknown);
            }
        }).collect(toList());

        context.complete(results);
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

    protected GrovePiContext getGrovePiContext() {
        return grovePiContext;
    }

    protected SensorContext getSensorContext() {
        return sensorContext;
    }

    protected UaObjectNode getSensorNode() {
        return sensorNode;
    }

}

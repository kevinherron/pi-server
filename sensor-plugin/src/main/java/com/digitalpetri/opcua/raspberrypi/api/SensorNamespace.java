package com.digitalpetri.opcua.raspberrypi.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpace;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.Pending;
import org.eclipse.milo.opcua.sdk.server.util.PendingRead;
import org.eclipse.milo.opcua.sdk.server.util.PendingWrite;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.eclipse.milo.opcua.stack.core.util.FutureUtils.failedUaFuture;
import static org.eclipse.milo.opcua.stack.core.util.FutureUtils.sequence;

public class SensorNamespace implements Namespace {

    public static final String NAMESPACE_URI = "urn:digitalpetri:opcua:piserver:sensors";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Sensor> sensors = Maps.newConcurrentMap();

    private final AddressSpace addressSpace;
    private final SubscriptionModel subscriptionModel;
    private final NodeId sensorsNodeId;

    private final OpcUaServer server;
    private final UShort namespaceIndex;

    public SensorNamespace(OpcUaServer server, UShort namespaceIndex) {
        this.server = server;
        this.namespaceIndex = namespaceIndex;

        addressSpace = server.getAddressSpace();

        subscriptionModel = new SubscriptionModel(server, this);

        sensorsNodeId = new NodeId(namespaceIndex, "Sensors");

        UaNode folderNode = UaObjectNode.builder(server)
            .setNodeId(sensorsNodeId)
            .setBrowseName(new QualifiedName(namespaceIndex, "Sensors"))
            .setDisplayName(LocalizedText.english("Sensors"))
            .setTypeDefinition(Identifiers.FolderType)
            .build();

        addressSpace.put(folderNode.getNodeId(), folderNode);

        addressSpace.addReference(
            new Reference(
                Identifiers.ObjectsFolder,
                Identifiers.Organizes,
                folderNode.getNodeId().expanded(),
                folderNode.getNodeClass(),
                true
            )
        );
    }

    public void addPlugin(Sensor sensor, SensorContext context) {
        // Build the browse path nodes...
        List<String> browsePath = context.getConfig().getStringList("sensor.browse-path");
        List<UaNode> browsePathNodes = createNodes(browsePath, Lists.newArrayList(), Lists.newArrayList());

        browsePathNodes.stream().forEach(node -> addressSpace.putIfAbsent(node.getNodeId(), node));

        // Create references for all the nodes we built...
        ServerNode sensorsFolder = addressSpace.get(sensorsNodeId);
        List<ServerNode> ns = Lists.newArrayList(sensorsFolder);
        ns.addAll(browsePathNodes);
        List<Reference> references = createReferences(context, ns, Lists.newArrayList());

        references.stream().forEach(reference -> {
            ServerNode node = addressSpace.get(reference.getSourceNodeId());
            node.addReference(reference);
        });

        sensors.put(context.getName(), sensor);
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
    public CompletableFuture<List<Reference>> browse(AccessContext context, NodeId nodeId) {
        ServerNode node = addressSpace.get(nodeId);

        if (node != null) {
            return CompletableFuture.completedFuture(node.getReferences());
        } else {
            return sensor(nodeId)
                .map(c -> c.browse(context, nodeId))
                .orElse(failedUaFuture(StatusCodes.Bad_NodeIdUnknown));
        }
    }

    @Override
    public void read(ReadContext readContext,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     List<ReadValueId> readValueIds) {

        List<PendingRead> pendingReads = readValueIds.stream()
            .map(PendingRead::new)
            .collect(toList());

        Map<Optional<Sensor>, List<PendingRead>> byPlugin = pendingReads.stream()
            .collect(groupingBy(p -> sensor(p.getInput().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<PendingRead> pending = byPlugin.get(plugin);

            List<ReadValueId> ids = pending.stream()
                .map(PendingRead::getInput)
                .collect(toList());

            CompletableFuture<List<DataValue>> callback = Pending.callback(pending);

            if (plugin.isPresent()) {
                ReadContext context = new ReadContext(
                    readContext.getServer(),
                    readContext.getSession().orElse(null),
                    callback,
                    readContext.getDiagnostics()
                );

                server.getExecutorService().execute(
                    () -> plugin.get().read(context, maxAge, timestamps, ids));
            } else {
                callback.complete(read(new AttributeContext(readContext), ids));
            }
        });

        /*
         * When all PendingReads have been completed complete the future we received with the values.
         */

        List<CompletableFuture<DataValue>> futures = pendingReads.stream()
            .map(PendingRead::getFuture)
            .collect(toList());

        sequence(futures).thenAcceptAsync(
            readContext::complete,
            server.getExecutorService()
        );
    }

    @Override
    public void write(WriteContext writeContext, List<WriteValue> writeValues) {
        List<PendingWrite> pendingWrites = writeValues.stream()
            .map(PendingWrite::new)
            .collect(toList());

        Map<Optional<Sensor>, List<PendingWrite>> byPlugin = pendingWrites.stream()
            .collect(groupingBy(p -> sensor(p.getInput().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<PendingWrite> pending = byPlugin.get(plugin);

            List<WriteValue> values = pending.stream()
                .map(PendingWrite::getInput)
                .collect(toList());

            CompletableFuture<List<StatusCode>> callback = Pending.callback(pending);

            if (plugin.isPresent()) {
                WriteContext context = new WriteContext(
                    writeContext.getServer(),
                    writeContext.getSession().orElse(null),
                    callback,
                    writeContext.getDiagnostics()
                );

                server.getExecutorService().execute(
                    () -> plugin.get().write(context, values));
            } else {
                callback.complete(write(values));
            }
        });

        List<CompletableFuture<StatusCode>> futures = pendingWrites.stream()
            .map(PendingWrite::getFuture)
            .collect(toList());

        sequence(futures).thenAcceptAsync(
            writeContext::complete,
            server.getExecutorService()
        );
    }

    @Override
    public void onDataItemsCreated(List<DataItem> monitoredItems) {
        Map<Optional<Sensor>, List<DataItem>> byPlugin = monitoredItems.stream()
            .collect(groupingBy(item -> sensor(item.getReadValueId().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<DataItem> items = byPlugin.get(plugin);

            if (plugin.isPresent()) {
                plugin.get().onDataItemsCreated(items);
            } else {
                subscriptionModel.onDataItemsCreated(items);
            }
        });
    }

    @Override
    public void onDataItemsModified(List<DataItem> monitoredItems) {
        Map<Optional<Sensor>, List<DataItem>> byPlugin = monitoredItems.stream()
            .collect(groupingBy(item -> sensor(item.getReadValueId().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<DataItem> items = byPlugin.get(plugin);

            if (plugin.isPresent()) {
                plugin.get().onDataItemsModified(items);
            } else {
                subscriptionModel.onDataItemsModified(items);
            }
        });
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> monitoredItems) {
        Map<Optional<Sensor>, List<DataItem>> byPlugin = monitoredItems.stream()
            .collect(groupingBy(item -> sensor(item.getReadValueId().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<DataItem> items = byPlugin.get(plugin);

            if (plugin.isPresent()) {
                plugin.get().onDataItemsDeleted(items);
            } else {
                subscriptionModel.onDataItemsDeleted(items);
            }
        });
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        Map<Optional<Sensor>, List<MonitoredItem>> byPlugin = monitoredItems.stream()
            .collect(groupingBy(item -> sensor(item.getReadValueId().getNodeId())));

        byPlugin.keySet().forEach(plugin -> {
            List<MonitoredItem> items = byPlugin.get(plugin);

            if (plugin.isPresent()) {
                plugin.get().onMonitoringModeChanged(items);
            } else {
                subscriptionModel.onMonitoringModeChanged(items);
            }
        });
    }

    private List<DataValue> read(AttributeContext context, List<ReadValueId> readValueIds) {
        return readValueIds.stream().map(id -> {
            NodeId nodeId = id.getNodeId();
            ServerNode node = addressSpace.get(nodeId);

            if (node != null) {
                return node.readAttribute(context, id.getAttributeId());
            } else {
                return new DataValue(StatusCodes.Bad_NodeIdUnknown);
            }
        }).collect(toList());
    }

    private List<StatusCode> write(List<WriteValue> values) {
        return Collections.nCopies(values.size(), new StatusCode(StatusCodes.Bad_NotWritable));
    }

    private Optional<Sensor> sensor(NodeId nodeId) {
        String id = nodeId.getIdentifier().toString();
        Matcher matcher = SensorContext.SENSOR_PREFIX_PATTERN.matcher(id);
        boolean matches = matcher.matches();

        return matches ?
            Optional.ofNullable(sensors.get(matcher.group(1))) :
            Optional.empty();
    }

    private List<UaNode> createNodes(List<String> browsePath, List<String> currentPath, List<UaNode> nodes) {
        if (browsePath.isEmpty()) {
            return nodes;
        } else {
            String element = browsePath.get(0);
            String nodeId = String.join("/", currentPath) + "/" + element;

            UaNode node = UaObjectNode.builder(server)
                .setNodeId(new NodeId(getNamespaceIndex(), nodeId))
                .setBrowseName(new QualifiedName(getNamespaceIndex(), element))
                .setDisplayName(LocalizedText.english(element))
                .setTypeDefinition(Identifiers.FolderType)
                .build();

            browsePath.remove(0);
            currentPath.add(element);
            nodes.add(node);

            return createNodes(browsePath, currentPath, nodes);
        }
    }

    private List<Reference> createReferences(SensorContext context,
                                             List<ServerNode> nodes,
                                             List<Reference> references) {
        if (nodes.isEmpty()) {
            return references;
        } else if (nodes.size() == 1) {
            // The termination/special case: create a reference to the connection's root folder.
            Reference reference = new Reference(
                nodes.get(0).getNodeId(),
                Identifiers.Organizes,
                context.getRootNodeId().expanded(),
                NodeClass.Object,
                true
            );

            references.add(reference);

            return references;
        } else {
            ServerNode n1 = nodes.get(0);
            ServerNode n2 = nodes.get(1);

            Reference reference = new Reference(
                n1.getNodeId(),
                Identifiers.Organizes,
                n2.getNodeId().expanded(),
                n2.getNodeClass(),
                true
            );

            references.add(reference);
            nodes.remove(0);

            return createReferences(context, nodes, references);
        }
    }

}

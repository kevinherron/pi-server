package com.digitalpetri.opcua.raspberrypi.api;

import java.io.File;
import java.util.Arrays;

import com.digitalpetri.opcua.raspberrypi.plugins.PluginContext;
import com.digitalpetri.opcua.raspberrypi.plugins.PluginHook;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorPluginHook implements PluginHook {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile PluginContext pluginContext;

    @Override
    public void startup(PluginContext context) {
        logger.info("startup()");

        this.pluginContext = context;

        OpcUaServer server = context.getServer();

        SensorNamespace namespace = server.getNamespaceManager().registerAndAdd(
            SensorNamespace.NAMESPACE_URI,
            (namespaceIdx) -> new SensorNamespace(server, namespaceIdx));

        loadSensors(context.getConfigDirectory(), namespace);
    }

    @Override
    public void shutdown(PluginContext context) {
        logger.info("shutdown()");
    }

    private void loadSensors(File configDirectory, SensorNamespace namespace) {
        File sensorDirectory = new File(configDirectory, "sensors");

        if (!sensorDirectory.exists() && !sensorDirectory.mkdirs()) {
            logger.error("Could not create connections directory.");
            return;
        }

        loadSensorDirectory(sensorDirectory, namespace);
    }

    private void loadSensorDirectory(File directory, SensorNamespace namespace) {
        /*
         * Load any .conf files in this directory.
		 */
        File[] configFiles = directory.listFiles(pathname -> pathname.getPath().endsWith(".conf"));
        Arrays.stream(configFiles).forEach(file -> {
            try {
                Tuple2<Sensor, SensorContext> tuple = load(file, namespace);

                namespace.addPlugin(tuple.v1(), tuple.v2());
            } catch (Exception e) {
                logger.error("Error loading connection.", e);
            }
        });

		/*
         * Recursively descend into subdirectories loading .conf files as we go.
		 */
        File[] dirs = directory.listFiles(File::isDirectory);
        Arrays.stream(dirs).forEach(d -> loadSensorDirectory(d, namespace));
    }

    private Tuple2<Sensor, SensorContext> load(File file, SensorNamespace namespace) throws Exception {
        Config config = ConfigFactory.parseFile(file);

        SensorType sensorType = SensorType.class.cast(
            Class.forName(config.getString("sensor.sensor-type")).newInstance());

        String name = config.getString("sensor.sensor-name");

        NodeId rootNodeId = new NodeId(
            namespace.getNamespaceIndex(),
            String.format("[%s]", name)
        );

        SensorContextImpl sensorContext = new SensorContextImpl(
            pluginContext.getServer(),
            namespace.getNamespaceIndex(),
            name,
            config,
            rootNodeId
        );

        Sensor sensor = sensorType.createSensor(sensorContext);

        return new Tuple2<>(sensor, sensorContext);
    }

    private static class SensorContextImpl implements SensorContext {

        private final OpcUaServer server;
        private final UShort namespaceIndex;
        private final String name;
        private final Config config;
        private final NodeId rootNodeId;

        public SensorContextImpl(OpcUaServer server,
                                 UShort namespaceIndex,
                                 String name,
                                 Config config,
                                 NodeId rootNodeId) {

            this.server = server;
            this.namespaceIndex = namespaceIndex;
            this.name = name;
            this.config = config;
            this.rootNodeId = rootNodeId;
        }

        @Override
        public OpcUaServer getServer() {
            return server;
        }

        @Override
        public UShort getNamespaceIndex() {
            return namespaceIndex;
        }

        @Override
        public String getNamespaceUri() {
            return SensorNamespace.NAMESPACE_URI;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Config getConfig() {
            return config;
        }

        @Override
        public NodeId getRootNodeId() {
            return rootNodeId;
        }

        @Override
        public NodeId nodeId(Object value) {
            return new NodeId(getNamespaceIndex(), String.format("[%s]%s", getName(), value));
        }

        @Override
        public QualifiedName qualifiedName(String name) {
            return new QualifiedName(getNamespaceIndex(), name);
        }

    }

}

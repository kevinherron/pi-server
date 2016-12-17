package com.digitalpetri.opcua.raspberrypi.api;

import java.util.regex.Pattern;

import com.typesafe.config.Config;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public interface SensorContext {

    /**
     * @return the {@link OpcUaServer} this sensor is a part of.
     */
    OpcUaServer getServer();

    /**
     * @return the index of the namespace connections belong to.
     */
    UShort getNamespaceIndex();

    /**
     * @return the URI of the namespace connections belong to.
     */
    String getNamespaceUri();

    /**
     * @return the name assigned to this plugin.
     */
    String getName();

    /**
     * @return the {@link Config} for this plugin.
     */
    Config getConfig();

    /**
     * @return the {@link NodeId} to use as the root folder of this plugin.
     */
    NodeId getRootNodeId();

    /**
     * A convenience method for creating {@link NodeId}s that belong to this sensor.
     * <p>
     * The {@link NodeId} is built by prefixing the supplied value with
     * "[{@link #getName()}]".
     *
     * @param value The value of the {@link NodeId}, before the prefix is applied.
     * @return A {@link NodeId} suitable for use as representing a node belonging to
     * this sensor.
     * @see {@link SensorContext#SENSOR_PREFIX_PATTERN}.
     */
    NodeId nodeId(Object value);

    /**
     * A convenience method for creating {@link QualifiedName}s that belong to the sensor namespace.
     *
     * @param name the String to create the {@link QualifiedName} with.
     * @return A {@link QualifiedName} belonging to the connection namespace.
     */
    QualifiedName qualifiedName(String name);

    /**
     * A {@link Pattern} that will match on Strings starting with a plugin name surrounded by square brackets.
     * <p>
     * Intended for use matching and extracting plugin names from {@link NodeId} values.
     * <p>
     * Group 1 contains the plugin name, group 2 contains the rest of the NodeId without the square brackets prefix.
     */
    Pattern SENSOR_PREFIX_PATTERN = Pattern.compile("^\\[(.+?)\\](.*)");

}

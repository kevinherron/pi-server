package com.digitalpetri.opcua.raspberrypi.plugins;

import java.io.File;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;

public interface PluginContext {

    File getDataDirectory();

    File getConfigDirectory();

    OpcUaServer getServer();

}

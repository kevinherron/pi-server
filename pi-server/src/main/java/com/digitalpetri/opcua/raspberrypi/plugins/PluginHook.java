package com.digitalpetri.opcua.raspberrypi.plugins;

public interface PluginHook {

    void startup(PluginContext context);

    void shutdown(PluginContext context);

}

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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inductiveautomation.opcua.sdk.server.OpcUaServer;
import com.inductiveautomation.opcua.stack.core.util.NonceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PiServer {

    public static void main(String[] args) throws Exception {
        new PiServer();
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OpcUaServer server;
    private final GpioConfig gpioConfig;

    public PiServer() throws Exception {
        configureLogback();

        gpioConfig = readGpioConfig();

        // SecureRandom on RPi sporadically blocks for insane amounts of time so don't use it for nonce generation.
        NonceUtil.disableSecureRandom();

        server = new OpcUaServer(new PiServerConfig());

        String namespaceUri = server.getApplicationDescription().getApplicationUri();

        server.getNamespaceManager().registerAndAdd(
                namespaceUri,
                (namespaceIndex) -> new PiNamespace(this, namespaceUri, namespaceIndex));

        server.startup();

        shutdownFuture().get();
    }

    public OpcUaServer getServer() {
        return server;
    }

    public GpioConfig getGpioConfig() {
        return gpioConfig;
    }

    private CompletableFuture<Void> shutdownFuture() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            future.complete(null);
        }));

        return future;
    }

    private void configureLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();

            System.out.println(System.getProperty("user.dir"));

            File logbackXml = new File("../config/logback.xml");

            if (!logbackXml.exists()) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("logback.xml");
                Files.copy(is, logbackXml.toPath());
            }

            configurator.doConfigure(logbackXml);
        } catch (Exception e) {
            System.err.println("Error configuring logback." + e);
        }

        StatusPrinter.printInCaseOfErrorsOrWarnings(context);
    }

    private GpioConfig readGpioConfig() {
        File configJson = new File("../config/gpio-config.json");

        try {
            ObjectMapper mapper = new ObjectMapper();

            return mapper.readValue(configJson, GpioConfig.class);
        } catch (Exception e) {
            logger.error("Error reading GPIO config. Make sure {} exists and contains a valid GPIO configuration.",
                    configJson.getAbsolutePath(), e);

            return new GpioConfig();
        }
    }

}

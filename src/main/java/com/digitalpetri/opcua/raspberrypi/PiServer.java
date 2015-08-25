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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.digitalpetri.opcua.raspberrypi.util.ManifestUtil;
import com.digitalpetri.opcua.sdk.server.OpcUaServer;
import com.digitalpetri.opcua.sdk.server.api.config.OpcUaServerConfig;
import com.digitalpetri.opcua.stack.core.application.DirectoryCertificateManager;
import com.digitalpetri.opcua.stack.core.security.SecurityPolicy;
import com.digitalpetri.opcua.stack.core.types.builtin.DateTime;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.structured.BuildInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;

public class PiServer {

    public static void main(String[] args) throws Exception {
        new PiServer();
    }

    private static final String PRODUCT_URI = "https://github.com/kevinherron/pi-server";
    private static final String BUILD_DATE_PROPERTY = "X-PiServer-Build-Date";
    private static final String BUILD_NUMBER_PROPERTY = "X-PiServer-Build-Number";
    private static final String SOFTWARE_VERSION_PROPERTY = "X-PiServer-Version";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OpcUaServer server;
    private final GpioConfig gpioConfig;

    public PiServer() throws Exception {
        configureLogback();

        gpioConfig = readGpioConfig();

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                .setApplicationName(getApplicationName())
                .setApplicationUri(getApplicationUri())
                .setBindAddresses(newArrayList("0.0.0.0"))
                .setBindPort(12685)
                .setBuildInfo(getBuildInfo())
                .setCertificateManager(new DirectoryCertificateManager(new File("../security")))
                .setHostname(getDefaultHostname())
                .setProductUri(getProductUri())
                .setSecurityPolicies(EnumSet.allOf(SecurityPolicy.class))
                .setServerName(getServerName())
                .build();

        server = new OpcUaServer(serverConfig);

        server.getNamespaceManager().registerAndAdd(
                PiNamespace.NAMESPACE_URI,
                (namespaceIndex) -> new PiNamespace(this, namespaceIndex));

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

    private LocalizedText getApplicationName() {
        return LocalizedText.english("Raspberry Pi OPC-UA Server");
    }

    private String getApplicationUri() {
        return String.format("urn:%s:pi-server:%s", getDefaultHostname(), UUID.randomUUID());
    }

    private String getProductUri() {
        return PRODUCT_URI;
    }

    private String getServerName() {
        return "pi-server";
    }

    private EnumSet<SecurityPolicy> getSecurityPolicies() {
        return EnumSet.of(SecurityPolicy.None);
    }

    private BuildInfo getBuildInfo() {
        String productUri = PRODUCT_URI;
        String manufacturerName = "digitalpetri";
        String productName = "Raspberry Pi OPC-UA Server";
        String softwareVersion = ManifestUtil.read(SOFTWARE_VERSION_PROPERTY).orElse("dev");
        String buildNumber = ManifestUtil.read(BUILD_NUMBER_PROPERTY).orElse("dev");
        DateTime buildDate = ManifestUtil.read(BUILD_DATE_PROPERTY).map((ts) -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(Long.valueOf(ts));
            return new DateTime(c.getTime());
        }).orElse(new DateTime());

        return new BuildInfo(
                productUri,
                manufacturerName,
                productName,
                softwareVersion,
                buildNumber,
                buildDate
        );
    }

    private static String getDefaultHostname() {
        try {
            return System.getProperty("hostname",
                    InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

}

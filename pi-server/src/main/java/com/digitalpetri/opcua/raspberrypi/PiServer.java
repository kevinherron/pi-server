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
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.digitalpetri.opcua.raspberrypi.plugins.PluginContext;
import com.digitalpetri.opcua.raspberrypi.plugins.PluginHook;
import com.digitalpetri.opcua.raspberrypi.util.KeyStoreLoader;
import com.digitalpetri.opcua.raspberrypi.util.ManifestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.application.CertificateManager;
import org.eclipse.milo.opcua.stack.core.application.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;

public class PiServer {

    public static void main(String[] args) throws Exception {
        System.out.println("user.dir=" + System.getProperty("user.dir"));

        new PiServer();
    }

    private static final String PRODUCT_URI = "https://github.com/kevinherron/pi-server";
    private static final String BUILD_DATE_PROPERTY = "X-PiServer-Build-Date";
    private static final String BUILD_NUMBER_PROPERTY = "X-PiServer-Build-Number";
    private static final String SOFTWARE_VERSION_PROPERTY = "X-PiServer-Version";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<PluginHook> pluginHooks = Sets.newConcurrentHashSet();

    private final OpcUaServer server;
    private final GpioConfig gpioConfig;

    public PiServer() throws Exception {
        configureLogback();

        gpioConfig = readGpioConfig();

        File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
        if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            throw new Exception("unable to create security temp dir: " + securityTempDir);
        }
        LoggerFactory.getLogger(getClass()).info("security temp dir: {}", securityTempDir.getAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

        DefaultCertificateManager certificateManager = new DefaultCertificateManager(
            loader.getServerKeyPair(),
            loader.getServerCertificateChain()
        );

        File pkiDir = securityTempDir.toPath().resolve("pki").toFile();
        DirectoryCertificateValidator certificateValidator = new DirectoryCertificateValidator(pkiDir);
        LoggerFactory.getLogger(getClass()).info("pki dir: {}", pkiDir.getAbsolutePath());

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = certificateManager.getCertificates().stream()
            .findFirst()
            .map(certificate ->
                CertificateUtil.getSubjectAltNameField(certificate, CertificateUtil.SUBJECT_ALT_NAME_URI)
                    .map(Object::toString)
                    .orElseThrow(() -> new RuntimeException("certificate is missing the application URI")))
            .orElse("urn:eclipse:milo:examples:server:" + UUID.randomUUID());

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        List<String> endpointAddresses = newArrayList();
        endpointAddresses.add(HostnameUtil.getHostname());
        endpointAddresses.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationName(getApplicationName())
            .setApplicationUri(applicationUri)
            .setBindAddresses(newArrayList("0.0.0.0"))
            .setBindPort(12685)
            .setBindAddresses(bindAddresses)
            .setEndpointAddresses(endpointAddresses)
            .setBuildInfo(getBuildInfo())
            .setCertificateManager(certificateManager)
            .setCertificateValidator(certificateValidator)
            .setProductUri(getProductUri())
            .setSecurityPolicies(getSecurityPolicies())
            .setServerName(getServerName())
            .setUserTokenPolicies(newArrayList(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS))
            .setStrictEndpointUrlsEnabled(false)
            .build();

        server = new OpcUaServer(serverConfig);

        server.getNamespaceManager().registerAndAdd(
            PiNamespace.NAMESPACE_URI,
            (namespaceIndex) -> new PiNamespace(this, namespaceIndex));

        server.startup();

        loadPlugins();

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

            File logbackXml = new File("../pi-server-data/config/logback.xml");

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
        File configJson = new File("../pi-server-data/config/gpio-config.json");

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
        return "";
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

    private void loadPlugins() {
        logger.info("Loading plugins...");

        ServiceLoader<PluginHook> serviceLoader = ServiceLoader.load(PluginHook.class);

        Iterator<PluginHook> hookIterator = serviceLoader.iterator();

        PluginContext context = new PluginContext() {
            @Override
            public File getDataDirectory() {
                return new File("../pi-server-data/").getAbsoluteFile();
            }

            @Override
            public File getConfigDirectory() {
                return new File("../pi-server-data/config/");
            }

            @Override
            public OpcUaServer getServer() {
                return server;
            }
        };

        hookIterator.forEachRemaining(pluginHook -> {
            try {
                pluginHook.startup(context);
                pluginHooks.add(pluginHook);
            } catch (Throwable t) {
                logger.error("Error loading PluginHook.", t);
            }
        });

        logger.info("Loaded {} plugins.", pluginHooks.size());
    }

}

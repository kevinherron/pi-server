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

import java.util.Calendar;
import java.util.EnumSet;
import java.util.UUID;

import com.digitalpetri.opcua.raspberrypi.util.ManifestUtil;
import com.inductiveautomation.opcua.sdk.server.api.OpcUaServerConfig;
import com.inductiveautomation.opcua.stack.core.security.SecurityPolicy;
import com.inductiveautomation.opcua.stack.core.types.builtin.DateTime;
import com.inductiveautomation.opcua.stack.core.types.builtin.LocalizedText;
import com.inductiveautomation.opcua.stack.core.types.structured.BuildInfo;

public class PiServerConfig implements OpcUaServerConfig {

    private static final String PRODUCT_URI = "https://github.com/kevinherron/pi-server";
    private static final String BUILD_DATE_PROPERTY = "X-PiServer-Build-Date";
    private static final String BUILD_NUMBER_PROPERTY = "X-PiServer-Build-Number";
    private static final String SOFTWARE_VERSION_PROPERTY = "X-PiServer-Version";

    @Override
    public LocalizedText getApplicationName() {
        return LocalizedText.english("Raspberry Pi OPC-UA Server");
    }

    @Override
    public String getApplicationUri() {
        return String.format("urn:%s:pi-server:%s", getHostname(), UUID.randomUUID());
    }

    @Override
    public String getProductUri() {
        return PRODUCT_URI;
    }

    @Override
    public String getServerName() {
        return "";
    }

    @Override
    public EnumSet<SecurityPolicy> getSecurityPolicies() {
        return EnumSet.of(SecurityPolicy.None);
    }

    @Override
    public BuildInfo getBuildInfo() {
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

}

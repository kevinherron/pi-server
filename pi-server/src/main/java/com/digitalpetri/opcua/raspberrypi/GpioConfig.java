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

import java.util.List;

import com.google.common.collect.Lists;

public class GpioConfig {

    private List<InputConfig> inputs = Lists.newArrayList();
    private List<OutputConfig> outputs = Lists.newArrayList();

    public List<InputConfig> getInputs() {
        return inputs;
    }

    public void setInputs(List<InputConfig> inputs) {
        this.inputs = inputs;
    }

    public List<OutputConfig> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<OutputConfig> outputs) {
        this.outputs = outputs;
    }

    @Override
    public String toString() {
        return "GpioConfig{" +
            "inputs=" + inputs +
            ", outputs=" + outputs +
            '}';
    }

    public static class InputConfig {
        int pin;
        String name;
        String type;
        String resistance;

        public int getPin() {
            return pin;
        }

        public void setPin(int pin) {
            this.pin = pin;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getResistance() {
            return resistance;
        }

        public void setResistance(String resistance) {
            this.resistance = resistance;
        }

        @Override
        public String toString() {
            return "Input{" +
                "pin=" + pin +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", resistance='" + resistance + '\'' +
                '}';
        }
    }

    public static class OutputConfig {
        int pin;
        String name;
        String type;
        double value;

        public int getPin() {
            return pin;
        }

        public void setPin(int pin) {
            this.pin = pin;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "Output{" +
                "pin=" + pin +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", value=" + value +
                '}';
        }
    }


}

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
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiPin;

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

    public static Pin int2pin(int pin) {
        switch (pin) {
            case 0: return RaspiPin.GPIO_00;
            case 1: return RaspiPin.GPIO_01;
            case 2: return RaspiPin.GPIO_02;
            case 3: return RaspiPin.GPIO_03;
            case 4: return RaspiPin.GPIO_04;
            case 5: return RaspiPin.GPIO_05;
            case 6: return RaspiPin.GPIO_06;
            case 7: return RaspiPin.GPIO_07;
            case 8: return RaspiPin.GPIO_08;
            case 9: return RaspiPin.GPIO_09;
            case 10: return RaspiPin.GPIO_10;
            case 11: return RaspiPin.GPIO_11;
            case 12: return RaspiPin.GPIO_12;
            case 13: return RaspiPin.GPIO_13;
            case 14: return RaspiPin.GPIO_14;
            case 15: return RaspiPin.GPIO_15;
            case 16: return RaspiPin.GPIO_16;
            case 17: return RaspiPin.GPIO_17;
            case 18: return RaspiPin.GPIO_18;
            case 19: return RaspiPin.GPIO_19;
            case 20: return RaspiPin.GPIO_20;
            case 21: return RaspiPin.GPIO_21;
            case 22: return RaspiPin.GPIO_22;
            case 23: return RaspiPin.GPIO_23;
            case 24: return RaspiPin.GPIO_24;
            case 25: return RaspiPin.GPIO_25;
            case 26: return RaspiPin.GPIO_26;
            case 27: return RaspiPin.GPIO_27;
            case 28: return RaspiPin.GPIO_28;
            case 29: return RaspiPin.GPIO_29;

            default: return null;
        }
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

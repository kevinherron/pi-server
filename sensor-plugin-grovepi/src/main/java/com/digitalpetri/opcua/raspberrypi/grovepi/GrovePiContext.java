package com.digitalpetri.opcua.raspberrypi.grovepi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.digitalpetri.grovepi.GrovePi;

public class GrovePiContext {

    private static GrovePiContext INSTANCE = null;

    public static synchronized GrovePiContext get() throws Exception {
        if (INSTANCE == null) {
            GrovePi grovePi = new GrovePi();

            INSTANCE = new GrovePiContext(grovePi);
        }

        return INSTANCE;
    }

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor();

    private final GrovePi grovePi;

    private GrovePiContext(GrovePi grovePi) {
        this.grovePi = grovePi;
    }

    public GrovePi getGrovePi() {
        return grovePi;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

}

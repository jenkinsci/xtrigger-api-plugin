package org.jenkinsci.lib.xtrigger;

import hudson.util.StreamTaskListener;

import java.io.Serializable;

/**
 * @author Gregory Boissinot
 */
public class XTriggerLog implements Serializable {

    private StreamTaskListener listener;

    public XTriggerLog(StreamTaskListener listener) {
        this.listener = listener;
    }

    public StreamTaskListener getListener() {
        return listener;
    }

    public void info(String message) {
        if (listener != null) {
            listener.getLogger().println(message);
        }
    }

    public void error(String message) {
        if (listener != null) {
            listener.getLogger().println("[ERROR] - " + message);
        }
    }

    public void closeQuietly() {
        if (listener != null) {
            listener.closeQuietly();
        }
    }
}

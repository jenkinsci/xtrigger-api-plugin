package org.jenkinsci.plugins.xtriggerapi;

import hudson.util.StreamTaskListener;
import hudson.model.TaskListener;

import java.io.Serializable;

/**
 * @author Gregory Boissinot
 */
public class XTriggerLog implements Serializable {

    private TaskListener listener;

    public XTriggerLog(TaskListener listener) {
        this.listener = listener;
    }

    public TaskListener getListener() {
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
        if (listener != null && listener instanceof StreamTaskListener ) {
            ((StreamTaskListener)listener).closeQuietly();
        }
    }
}

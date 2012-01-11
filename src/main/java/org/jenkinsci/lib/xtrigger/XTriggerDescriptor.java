package org.jenkinsci.lib.xtrigger;

import hudson.model.Item;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Gregory Boissinot
 */
public abstract class XTriggerDescriptor extends TriggerDescriptor {

    private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

    public ExecutorService getExecutor() {
        return queue.getExecutors();
    }

    @Override
    public boolean isApplicable(Item item) {
        return true;
    }

}


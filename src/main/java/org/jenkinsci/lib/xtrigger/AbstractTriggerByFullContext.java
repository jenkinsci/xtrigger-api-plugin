package org.jenkinsci.lib.xtrigger;

import antlr.ANTLRException;
import hudson.model.BuildableItem;
import hudson.model.Node;


/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTriggerByFullContext<C extends XTriggerContext> extends AbstractTrigger {

    private transient C context;

    /**
     * Builds a trigger object
     * Calls an implementation trigger
     *
     * @param cronTabSpec the scheduler value
     * @throws ANTLRException
     */
    public AbstractTriggerByFullContext(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    /**
     * Can be overridden if needed
     */
    @Override
    protected void start(Node pollingNode, BuildableItem project, boolean newInstance, XTriggerLog log) throws XTriggerException {
        if (isContextOnStartupFetched()) {
            context = getContext(pollingNode, log);
        }
    }

    public abstract boolean isContextOnStartupFetched();

    @Override
    protected boolean checkIfModified(Node pollingNode, XTriggerLog log) throws XTriggerException {

        C newContext = getContext(pollingNode, log);

        if (offlineSlaveOnStartup) {
            log.info("Slave(s) were offline at startup or at previous poll.");
            log.info("Recording environment context and waiting for next schedule to check if there are modifications.");
            offlineSlaveOnStartup = false;
            setNewContext(newContext);
            return false;
        }

        if (context == null) {
            log.info("Recording context. Check changes in next poll.");
            setNewContext(newContext);
            return false;
        }

        boolean changed = checkIfModified(context, newContext, log);
        setNewContext(newContext);
        return changed;
    }


    private void setNewContext(C context) {
        this.context = context;
    }

    protected abstract C getContext(Node pollingNode, XTriggerLog log) throws XTriggerException;

    /**
     * Checks if there are modifications in the environment between last poll
     *
     * @return true if there are modifications
     */
    protected abstract boolean checkIfModified(C oldContext, C newContext, XTriggerLog log) throws XTriggerException;

}

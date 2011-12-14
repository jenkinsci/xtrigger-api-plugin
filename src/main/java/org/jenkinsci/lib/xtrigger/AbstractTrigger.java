package org.jenkinsci.lib.xtrigger;

import antlr.ANTLRException;
import hudson.Util;
import hudson.model.BuildableItem;
import hudson.triggers.Trigger;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.Date;


/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends Trigger<BuildableItem> implements Serializable {


    /**
     * Builds a trigger object
     * Calls an implementation trigger
     *
     * @param cronTabSpec the scheduler value
     * @throws ANTLRException
     */
    public AbstractTrigger(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    /**
     * Gets the triggering log file
     *
     * @return the trigger log
     */
    protected abstract File getLogFile();

    /**
     * Checks if the new folder content has been modified
     * The date time and the content file are used.
     *
     * @return true if the new folder content has been modified
     */
    protected abstract boolean checkIfModified(XTriggerLog log) throws XTriggerException;

    /**
     * Gets the trigger cause
     *
     * @return the trigger cause
     */
    public abstract String getCause();

    /**
     * Asynchronous task
     */
    protected class Runner implements Runnable, Serializable {

        private XTriggerLog log;

        private String triggerName;

        public Runner(XTriggerLog log, String triggerName) {
            this.log = log;
            this.triggerName = triggerName;
        }

        public void run() {

            try {
                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(log);
                log.info("\nPolling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    log.info("Changes found. Scheduling a build.");
                    job.scheduleBuild(new XTriggerCause(triggerName, getCause()));
                } else {
                    log.info("No changes.");
                }

            } catch (XTriggerException e) {
                log.error("Polling error " + e.getMessage());
            } catch (Throwable e) {
                log.error("SEVERE - Polling error " + e.getMessage());
            } finally {
                log.closeQuietly();
            }
        }
    }

}

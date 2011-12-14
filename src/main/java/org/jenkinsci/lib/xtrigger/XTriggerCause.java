package org.jenkinsci.lib.xtrigger;

import hudson.model.Cause;

/**
 * @author Gregory Boissinot
 */
public class XTriggerCause extends Cause {

    private String triggerName;

    private String causeFrom;

    protected XTriggerCause(String triggerName, String causeFrom) {
        this.triggerName = triggerName;
        this.causeFrom = causeFrom;
    }

    @Override
    public String getShortDescription() {
        if (causeFrom == null) {
            return "[" + triggerName + "]";
        } else {
            return String.format("[%s] %s", triggerName, causeFrom);
        }
    }
}

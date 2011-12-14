package org.jenkinsci.lib.xtrigger;

/**
 * @author Gregory Boissinot
 */
public class XTriggerException extends Exception {

    public XTriggerException() {
    }

    public XTriggerException(String s) {
        super(s);
    }

    public XTriggerException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public XTriggerException(Throwable throwable) {
        super(throwable);
    }
}

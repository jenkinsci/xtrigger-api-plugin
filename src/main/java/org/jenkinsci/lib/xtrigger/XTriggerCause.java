package org.jenkinsci.lib.xtrigger;

import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Gregory Boissinot
 */
public class XTriggerCause extends Cause {

    private static Logger LOGGER = Logger.getLogger(XTriggerCause.class.getName());

    private String triggerName;

    private String causeFrom;

    private boolean logEnabled;

    protected XTriggerCause(String triggerName, String causeFrom) {
        this(triggerName, causeFrom, false);
    }

    protected XTriggerCause(String triggerName, String causeFrom, boolean logEnabled) {
        this.triggerName = triggerName;
        this.causeFrom = causeFrom;
        this.logEnabled = logEnabled;
    }

    @Override
    public void onAddedTo(final AbstractBuild build) {
        final XTriggerCauseAction causeAction = build.getAction(XTriggerCauseAction.class);
        if (causeAction != null) {
            try {
                causeAction.setBuild(build);
                File triggerLogFile = causeAction.getLogFile();
                String logContent = causeAction.getLogMessage();
                try {
                    FileUtils.writeStringToFile(triggerLogFile, logContent);
                } catch (IOException ioe) {
                    throw new XTriggerException(ioe);
                }
            } catch (XTriggerException xe) {
                LOGGER.log(Level.SEVERE, "Problem to attach cause object to build object.", xe);
            }
        }
    }

    @Override
    public String getShortDescription() {
        if (causeFrom == null) {
            return "[" + triggerName + "]";
        } else if (!logEnabled) {
            return String.format("[%s] %s", triggerName, causeFrom);
        } else {
            return String.format("[%s] %s (%s)", triggerName, causeFrom, "<a href=\"triggerCauseAction\">log</a>");
        }
    }

    public void print(TaskListener listener) {
        if (causeFrom == null) {
            listener.getLogger().println("[" + triggerName + "]");
        } else {
            listener.getLogger().println(String.format("[%s] %s (%s)", triggerName, causeFrom,
                    HyperlinkNote.encodeTo("triggerCauseAction", "log")));
        }
    }


    @SuppressWarnings("unused")
    public String getTriggerName() {
        return triggerName;
    }

}

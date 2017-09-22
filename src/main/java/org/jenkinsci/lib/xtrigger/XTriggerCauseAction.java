package org.jenkinsci.lib.xtrigger;

import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.Run;
import org.apache.commons.jelly.XMLOutput;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Gregory Boissinot
 */
public class XTriggerCauseAction implements Action {

    private static final String URL_NAME = "triggerCauseAction";

    /**
     * Set when the cause object is added to the build object
     * at job startup
     */
    private Run<?, ?> run;

    /**
     * Set on creation
     */
    private transient String logMessage;


    public XTriggerCauseAction(String logMessage) {
        this.logMessage = logMessage;
    }

    public String getLogMessage() {
        return logMessage;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @SuppressWarnings("unused")
    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public final String getUrlName() {
        return URL_NAME;
    }

    public void setRun(Run<?, ?> run) {
        this.run = run;
    }

    public File getLogFile() {
        if (run == null) {
            return null;
        }
        return new File(run.getRootDir(), "triggerlog.xml");
    }

    @SuppressWarnings("unused")
    public String getLog() throws IOException {
        File logFile = getLogFile();
        if (logFile == null) {
            return null;
        }
        return Util.loadFile(logFile);
    }

    @SuppressWarnings("unused")
    public String getTitle() {
        if (run == null) {
            return "XTrigger Log";
        }
        XTriggerCause triggerCause = (XTriggerCause) run.getCause(XTriggerCause.class);
        if (triggerCause == null) {
            return "XTrigger Log";
        }
        return triggerCause.getTriggerName() + " Log";
    }

    @SuppressWarnings("unused")
    public void writeLogTo(XMLOutput out) throws IOException {
        new AnnotatedLargeText<XTriggerCauseAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
    }

}

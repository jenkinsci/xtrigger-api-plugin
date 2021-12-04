package org.jenkinsci.plugins.xtriggerapi;

import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.Run;

import org.apache.commons.jelly.XMLOutput;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    private Run<?, ?> build;

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
    public Run<?, ?> getBuild() {
        return build;
    }

    @Override
    public final String getUrlName() {
        return URL_NAME;
    }

    public void setBuild(Run<?, ?> build) {
        this.build = build;
    }

    public File getLogFile() {
        if (build == null) {
            return null;
        }
        return new File(build.getRootDir(), "triggerlog.xml");
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
        if (build == null) {
            return "XTrigger Log";
        }
        XTriggerCause triggerCause = (XTriggerCause) build.getCause(XTriggerCause.class);
        if (triggerCause == null) {
            return "XTrigger Log";
        }
        return triggerCause.getTriggerName() + " Log";
    }

    @SuppressWarnings("unused")
    @SuppressFBWarnings( "RV_RETURN_VALUE_IGNORED")
    public void writeLogTo(XMLOutput out) throws IOException {
        new AnnotatedLargeText<XTriggerCauseAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
    }

}

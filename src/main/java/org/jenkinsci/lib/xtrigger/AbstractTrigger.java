package org.jenkinsci.lib.xtrigger;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.util.StreamTaskListener;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.lib.envinject.service.EnvVarsResolver;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends Trigger<BuildableItem> implements Serializable {

    private static Logger LOGGER = Logger.getLogger(AbstractTrigger.class.getName());

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
     * Checks if a consistency workspace is required for the polling
     *
     * @return true if a workspace is required for the job, false otherwise
     */
    protected abstract boolean requiresWorkspaceForPolling();

    /**
     * Checks if the new folder content has been modified
     * The date time and the content file are used.
     *
     * @return true if the new folder content has been modified
     */
    protected abstract boolean checkIfModified(XTriggerLog log) throws XTriggerException;

    protected String resolveEnvVars(String value, AbstractProject project, Node node) throws XTriggerException {
        EnvVarsResolver varsResolver = new EnvVarsResolver();
        Map<String, String> envVars;
        try {
            envVars = varsResolver.getPollingEnvVars(project, node);
        } catch (EnvInjectException envInjectException) {
            throw new XTriggerException(envInjectException);
        }
        return Util.replaceMacro(value, envVars);
    }

    @Override
    public void run() {
        XTriggerDescriptor descriptor = getDescriptor();
        ExecutorService executorService = descriptor.getExecutor();
        StreamTaskListener listener;
        try {
            listener = new StreamTaskListener(getLogFile());
            XTriggerLog log = new XTriggerLog(listener);
            if (!Hudson.getInstance().isQuietingDown() && ((AbstractProject) job).isBuildable()) {
                Runner runner = new Runner(getName(), log);
                executorService.execute(runner);
            } else {
                log.info("Jenkins is quieting down or the job is not buildable.");
            }

        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
            t.printStackTrace();
        }
    }

    protected abstract String getName();

    public XTriggerDescriptor getDescriptor() {
        return (XTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Asynchronous task
     */
    protected class Runner implements Runnable, Serializable {

        private String triggerName;

        private XTriggerLog log;

        public Runner(String triggerName, XTriggerLog log) {
            this.triggerName = triggerName;
            this.log = log;
        }

        @Override
        public void run() {
            try {
                log.info("Polling for the job " + job.getName());
                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(log);
                log.info("\nPolling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start) + ".");
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

    /**
     * Gets the trigger cause
     *
     * @return the trigger cause
     */
    protected abstract String getCause();

    /**
     * Get the node where the polling need to be done
     * <p/>
     * The returned node must have a number of executor different of 0.
     *
     * @param log
     * @return the node; null if there is no available node
     */
    protected Node getPollingNode(XTriggerLog log) {
        List<Node> nodes = getPollingNodeListWithExecutors(log);
        if (nodes == null || nodes.size() == 0) {
            return null;
        }
        return nodes.get(0);
    }

    protected List<Node> getPollingNodeListWithExecutors(XTriggerLog log) {
        List<Node> result = new ArrayList<Node>();
        List<Node> nodes = getPollingNodeList(log);
        for (Node node : nodes) {
            if (eligibleNode(node)) {
                result.add(node);
            }
        }
        return result;
    }

    private List<Node> getPollingNodeList(XTriggerLog log) {
        if (requiresWorkspaceForPolling()) {
            AbstractProject project = (AbstractProject) job;
            Node lastBuildOnNode = project.getLastBuiltOn();
            if (lastBuildOnNode == null) {
                return candidatePollingNode(log);
            }
            return Arrays.asList(lastBuildOnNode);
        }
        return candidatePollingNode(log);
    }

    private boolean eligibleNode(Node node) {
        return node.getNumExecutors() != 0;
    }

    private List<Node> candidatePollingNode(XTriggerLog log) {
        AbstractProject p = (AbstractProject) job;
        Label label = p.getAssignedLabel();
        if (label == null) {
            log.info("Polling on master.");
            return Arrays.asList(getMasterNode());
        } else {
            log.info(String.format("Searching a node to run the polling for the label '%s'.", label));
            return getNodesLabel(p, label);
        }
    }

    private Node getMasterNode() {
        Computer computer = Hudson.getInstance().toComputer();
        if (computer != null) {
            return computer.getNode();
        } else {
            return null;
        }
    }

    private List<Node> getNodesLabel(AbstractProject project, Label label) {

        Node lastBuildOnNode = project.getLastBuiltOn();
        boolean isAPreviousBuildNode = lastBuildOnNode != null;

        List<Node> result = new ArrayList<Node>();
        Set<Node> nodes = label.getNodes();
        for (Node node : nodes) {
            if (node != null) {
                if (!isAPreviousBuildNode) {
                    FilePath nodePath = node.getRootPath();
                    if (nodePath != null) {
                        result.add(node);
                    }
                } else {
                    FilePath nodeRootPath = node.getRootPath();
                    if (nodeRootPath != null) {
                        if (nodeRootPath.equals(lastBuildOnNode.getRootPath())) {
                            result.add(0, node);
                        }
                    }
                }
            }
        }
        return result;
    }

}

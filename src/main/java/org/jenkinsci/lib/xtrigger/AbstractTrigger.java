package org.jenkinsci.lib.xtrigger;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.util.NullStream;
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

    protected transient boolean offlineSlaveOnStartup = false;

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


    @Override
    public void start(BuildableItem project, boolean newInstance) {
        super.start(project, newInstance);

        XTriggerLog log = new XTriggerLog(new StreamTaskListener(new NullStream()));
        Node launcherNode = getPollingNode(log);
        if (launcherNode == null) {
            log.info("Can't find any complete active node. Checking again in next polling schedule.");
            offlineSlaveOnStartup = true;
            return;
        }
        if (launcherNode.getRootPath() == null) {
            log.info("The running slave might be offline at the moment. Waiting for next schedule.");
            offlineSlaveOnStartup = true;
            return;
        }

        start(launcherNode, project, newInstance, log);
    }

    protected abstract void start(Node pollingNode, BuildableItem project, boolean newInstance, XTriggerLog log);

    @SuppressWarnings("unused")
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
    private class Runner implements Runnable, Serializable {

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

                Node pollingNode = getPollingNode(log);
                if (pollingNode == null) {
                    log.info("Can't find any complete active node for the polling action. Maybe slaves are not yet active at this time or the number of executor of the master is 0. Checking again in next polling schedule.");
                    return;
                }

                if (pollingNode.getRootPath() == null) {
                    log.info("The running slave might be offline at the moment. Waiting for next schedule.");
                    return;
                }

                displayPollingNode(pollingNode, log);

                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(pollingNode, log);
                log.info("\nPolling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start) + ".");

                if (changed) {
                    log.info("Changes found. Scheduling a build.");
                    AbstractProject project = (AbstractProject) job;
                    project.scheduleBuild(0, new XTriggerCause(triggerName, getCause()), getScheduledActions(pollingNode, log));
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

    protected abstract Action[] getScheduledActions(Node pollingNode, XTriggerLog log);

    /**
     * Checks if the new folder content has been modified
     * The date time and the content file are used.
     *
     * @return true if the new folder content has been modified
     */
    protected abstract boolean checkIfModified(Node pollingNode, XTriggerLog log) throws XTriggerException;

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
    private Node getPollingNode(XTriggerLog log) {
        List<Node> nodes = getPollingNodeListWithExecutors(log);
        if (nodes == null || nodes.size() == 0) {
            return null;
        }
        //Get the first eligible node
        return nodes.get(0);
    }

    private void displayPollingNode(Node node, XTriggerLog log) {
        assert node != null;
        String nodeName = node.getNodeName();
        if (nodeName == null || nodeName.trim().length() == 0) {
            log.info("Polling on master.");
        } else {
            log.info("Polling remotely on " + nodeName);
        }
    }

    private List<Node> getPollingNodeListWithExecutors(XTriggerLog log) {
        List<Node> result = new ArrayList<Node>();
        List<Node> nodes = getPollingNodeList(log);
        for (Node node : nodes) {
            if (node != null && eligibleNode(node)) {
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
        if (node == null) {
            return false;
        }

        if (node.getRootPath() == null) {
            return false;
        }

        return node.getNumExecutors() != 0;
    }

    private List<Node> candidatePollingNode(XTriggerLog log) {
        AbstractProject p = (AbstractProject) job;
        Label label = p.getAssignedLabel();
        if (label == null) {
            AbstractProject project = (AbstractProject) job;
            Node lastBuildOnNode = project.getLastBuiltOn();
            if (lastBuildOnNode == null) {
                return Arrays.asList(getMasterNode());
            }
            return Arrays.asList(lastBuildOnNode);
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

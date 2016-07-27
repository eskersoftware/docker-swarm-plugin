package suryagaddipati.jenkinsdockerslaves;

import com.github.dockerjava.api.DockerClient;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

@Extension
public class ContainerCleanupListener extends RunListener<Run<?,?>> {

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        if(run.getAction(DockerLabelAssignmentAction.class) !=null){
            DockerLabelAssignmentAction labelAssignmentAction = run.getAction(DockerLabelAssignmentAction.class);
            final String computerName = labelAssignmentAction.getLabel().getName();
            final PrintStream logger = listener.getLogger();
            DockerComputer computer = (DockerComputer) Jenkins.getInstance().getComputer(computerName);
            terminate(computer,logger);
        }
    }

    public void terminate(DockerComputer computer, PrintStream logger) {
        computer.setAcceptingTasks(false);
        cleanupNode(computer,logger);
        cleanupDockerVolumeAndContainer(computer,logger);
    }

    private void cleanupNode(DockerComputer computer, PrintStream logger) {
        try {
            if(computer.getNode() !=null){
                logger.println("Removing node "+ computer.getNode().getDisplayName());
                computer.getNode().terminate();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace(logger);
        }
    }

    private void cleanupDockerVolumeAndContainer(DockerComputer computer, PrintStream logger) {
        String containerId = computer.getContainerId();
        String volumeName = computer.getVolumeName();
        DockerSlaveConfiguration configuration = DockerSlaveConfiguration.get();
        try( DockerClient dockerClient = configuration.newDockerClient()){
            if (containerId != null){
                killContainer(containerId, dockerClient);
                removeContainer(logger, containerId, dockerClient);
            }
            removeVolume(logger, volumeName, dockerClient);
        } catch (Exception e) {
            e.printStackTrace(logger);
        }
    }

    private void removeVolume(PrintStream logger, String volumeName, DockerClient dockerClient) {
        if(volumeName != null){
            dockerClient.removeVolumeCmd(volumeName).exec();
            logger.println("Removed volume " + volumeName);
        }
    }

    private void removeContainer(PrintStream logger, String containerId, DockerClient dockerClient) {
        dockerClient.removeContainerCmd(containerId).exec();
        logger.println("Removed Container " + containerId);
    }

    private void killContainer(String containerId, DockerClient dockerClient) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
        }catch (Exception _){}
    }
}

package de.zalando.ep.zalenium.container;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerMount;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.PortBinding;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;

@SuppressWarnings("ConstantConditions")
public class DockerContainerClient implements ContainerClient {

    // Allows access from the docker-selenium containers to a Mac host. Fix until docker for mac supports it natively.
    // See https://github.com/moby/moby/issues/22753
    private static final String DOCKER_FOR_MAC_LOCALHOST_IP = "192.168.65.1";
    private static final String DOCKER_FOR_MAC_LOCALHOST_NAME = "mac.host.local";
    private static final String DEFAULT_DOCKER_NETWORK_MODE = "default";
    private static final String DEFAULT_DOCKER_NETWORK_NAME = "bridge";
    private static final String DOCKER_NETWORK_HOST_MODE_NAME = "host";
    private static final List<String> DEFAULT_DOCKER_EXTRA_HOSTS = new ArrayList<>();
    private static final String NODE_MOUNT_POINT = "/tmp/node";
    private static final String[] PROTECTED_NODE_MOUNT_POINTS = {
            "/var/run/docker.sock",
            "/home/seluser/videos",
            "/dev/shm"
    };
    private static final String[] HTTP_PROXY_ENV_VARS = {
            "zalenium_http_proxy",
            "zalenium_https_proxy",
            "zalenium_no_proxy"
    };
    private final Logger logger = Logger.getLogger(DockerContainerClient.class.getName());
    private final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private DockerClient dockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private String nodeId;
    private String zaleniumNetwork;
    private List<String> zaleniumExtraHosts;
    private List<ContainerMount> mntFolders = new ArrayList<>();
    private List<String> zaleniumHttpEnvVars = new ArrayList<>();
    private AtomicBoolean mntFoldersAndHttpEnvVarsChecked = new AtomicBoolean(false);

    @VisibleForTesting
    public void setContainerClient(final DockerClient client) {
        dockerClient = client;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    private String getContainerId(String containerName) {
        final String containerNameSearch = containerName.contains("/") ?
                containerName : String.format("/%s", containerName);

        List<Container> containerList = null;
        try {
            containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while getting containerId", e);
            ga.trackException(e);
        }

        return containerList.stream()
                .filter(container -> containerNameSearch.equalsIgnoreCase(container.names().get(0)))
                .findFirst().map(Container::id).orElse(null);
    }

    public InputStream copyFiles(String containerId, String folderName) {
        try {
            return dockerClient.archiveContainer(containerId, folderName);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Something happened while copying the folder " + folderName + ", " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
        return null;
    }

    public void stopContainer(String containerId) {
        try {
            dockerClient.stopContainer(containerId, 5);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        final ExecCreation execCreation;
        try {
            execCreation = dockerClient.execCreate(containerId, command,
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            final LogStream output = dockerClient.execStart(execCreation.id());
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, Arrays.toString(command)));
            if (waitForExecution) {
                try {
                    String commandOutput = output.readFully();
                    logger.log(Level.FINE, () -> String.format("%s %s", nodeId, commandOutput));
                } catch (Exception e) {
                    logger.log(Level.FINE, nodeId + " Error while executing the output.readFully()", e);
                    ga.trackException(e);
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }
    }

    public String getLatestDownloadedImage(String imageName) {
        List<Image> images;
        try {
            images = dockerClient.listImages(DockerClient.ListImagesParam.byName(imageName));
            if (images.isEmpty()) {
                logger.log(Level.SEVERE, nodeId + " A downloaded docker-selenium image was not found!");
                return imageName;
            }
            for (int i = images.size() - 1; i >= 0; i--) {
                if (images.get(i).repoTags() == null) {
                    images.remove(i);
                }
            }
            images.sort((o1, o2) -> o2.created().compareTo(o1.created()));
            return images.get(0).repoTags().get(0);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }
        return imageName;
    }

    public int getRunningContainers(String image) {
        try {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            int numberOfDockerSeleniumContainers = 0;
            for (Container container : containerList) {
                if (container.image().contains(image) && !"exited".equalsIgnoreCase(container.state())) {
                    numberOfDockerSeleniumContainers++;
                }
            }
            return numberOfDockerSeleniumContainers;
        } catch (InterruptedException | DockerException e) {
            logger.log(Level.WARNING, nodeId + " Error while getting number of running containers", e);
            ga.trackException(e);
        }
        return 0;
    }

    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                                   String nodePort) {
        String containerName = generateContainerName(zaleniumContainerName, nodePort);

        loadMountedFolders(zaleniumContainerName);

        List<String> binds = generateMountedFolderBinds();
        binds.add("/dev/shm:/dev/shm");

        String noVncPort = envVars.get("NOVNC_PORT");

        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        List<PortBinding> portBindingList = new ArrayList<>();
        portBindingList.add(PortBinding.of("", nodePort));
        portBindings.put(nodePort, portBindingList);
        portBindingList = new ArrayList<>();
        portBindingList.add(PortBinding.of("", noVncPort));
        portBindings.put(noVncPort, portBindingList);

        String networkMode = getZaleniumNetwork(zaleniumContainerName);

        List<String> extraHosts = new ArrayList<>();
        extraHosts.add(String.format("%s:%s", DOCKER_FOR_MAC_LOCALHOST_NAME, DOCKER_FOR_MAC_LOCALHOST_IP));

        // Allows "--net=host" work. Only supported for Linux.
        if (DOCKER_NETWORK_HOST_MODE_NAME.equalsIgnoreCase(networkMode)) {
            envVars.put("SELENIUM_HUB_HOST", "localhost");
            envVars.put("SELENIUM_NODE_HOST", "localhost");
            envVars.put("PICK_ALL_RANDOM_PORTS", "true");
            try {
                String hostName = dockerClient.info().name();
                extraHosts.add(String.format("%s:%s", hostName, "127.0.1.0"));
            } catch (DockerException | InterruptedException e) {
                logger.log(Level.FINE, nodeId + " Error while getting host name", e);
            }
        }

        // Reflect extra hosts of the hub container
        final List<String> hubExtraHosts = getContainerExtraHosts(zaleniumContainerName);
        extraHosts.addAll(hubExtraHosts);

        HostConfig hostConfig = HostConfig.builder()
                .appendBinds(binds)
                .portBindings(portBindings)
                .networkMode(networkMode)
                .extraHosts(extraHosts)
                .autoRemove(true)
                .privileged(true)
                .build();


        List<String> flattenedEnvVars = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
        flattenedEnvVars.addAll(zaleniumHttpEnvVars);


        final String[] exposedPorts = {nodePort, noVncPort};
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(image)
                .env(flattenedEnvVars)
                .exposedPorts(exposedPorts)
                .hostConfig(hostConfig)
                .build();

        try {
            final ContainerCreation container = dockerClient.createContainer(containerConfig, containerName);
            dockerClient.startContainer(container.id());
            return new ContainerCreationStatus(true, containerName, nodePort);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while starting a new container", e);
            ga.trackException(e);
            return new ContainerCreationStatus(false);
        }
    }

    private String generateContainerName(String zaleniumContainerName,
                                         String nodePort) {
        return String.format("%s_%s", zaleniumContainerName, nodePort);
    }

    private void loadMountedFolders(String zaleniumContainerName) {
        if (!this.mntFoldersAndHttpEnvVarsChecked.get()) {
            String containerId = getContainerId(zaleniumContainerName);
            if (containerId == null) {
                return;
            }

            ContainerInfo containerInfo = null;

            try {
                containerInfo = dockerClient.inspectContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                logger.log(Level.WARNING, nodeId + " Error while getting mounted folders and env vars.", e);
                ga.trackException(e);
            }

            loadMountedFolders(containerInfo);
        }
    }

    private synchronized void loadMountedFolders(ContainerInfo containerInfo) {
        if (!this.mntFoldersAndHttpEnvVarsChecked.getAndSet(true)) {

            for (ContainerMount containerMount : containerInfo.mounts()) {
                if (containerMount.destination().startsWith(NODE_MOUNT_POINT)) {
                    this.mntFolders.add(containerMount);
                }
            }

            for (String envVar : containerInfo.config().env()) {
                Arrays.asList(HTTP_PROXY_ENV_VARS).forEach(httpEnvVar -> {
                    String httpEnvVarToAdd = envVar.replace("zalenium_", "");
                    if (envVar.contains(httpEnvVar) && !zaleniumHttpEnvVars.contains(httpEnvVarToAdd)) {
                        zaleniumHttpEnvVars.add(httpEnvVarToAdd);
                    }
                });
            }
        }
    }

    private List<String> generateMountedFolderBinds() {
        List<String> result = new ArrayList<>();

        this.mntFolders.stream().filter(mount -> mount.destination().startsWith(NODE_MOUNT_POINT)).forEach(
                containerMount -> {
                    String destination = containerMount.destination().substring(NODE_MOUNT_POINT.length());

                    if (Arrays.stream(PROTECTED_NODE_MOUNT_POINTS).anyMatch(item -> item.equalsIgnoreCase(destination))) {
                        throw new IllegalArgumentException("The following points may not be mounted via node mounting: "
                                + String.join(",", PROTECTED_NODE_MOUNT_POINTS));
                    }
                    String mountedBind = String.format("%s:%s", containerMount.source(), destination);
                    result.add(mountedBind);
                }
        );

        return result;
    }

    private synchronized List<String> getContainerExtraHosts(String zaleniumContainerName) {
        if (zaleniumExtraHosts != null) {
            return zaleniumExtraHosts;
        }
        String containerId = getContainerId(zaleniumContainerName);
        ContainerInfo containerInfo;
        try {
            containerInfo = dockerClient.inspectContainer(containerId);
            zaleniumExtraHosts = containerInfo.hostConfig().extraHosts();
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while getting Zalenium extra hosts.", e);
            ga.trackException(e);
        }
        if (zaleniumExtraHosts == null) {
            zaleniumExtraHosts = DEFAULT_DOCKER_EXTRA_HOSTS;
        }
        return zaleniumExtraHosts;
    }

    @Override
    public void initialiseContainerEnvironment() {
        // TODO: Move cleanup code from bash to here

    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        ContainerClientRegistration registration = new ContainerClientRegistration();

        Integer noVncPort = remoteHost.getPort() + DockerSeleniumStarterRemoteProxy.NO_VNC_PORT_GAP;
        String containerName = generateContainerName(zaleniumContainerName, Integer.toString(remoteHost.getPort()));
        String containerId = this.getContainerId(containerName);
        registration.setNoVncPort(noVncPort);
        registration.setContainerId(containerId);
        return registration;
    }

    private synchronized String getZaleniumNetwork(String zaleniumContainerName) {
        if (zaleniumNetwork != null) {
            return zaleniumNetwork;
        }
        String zaleniumContainerId = getContainerId(zaleniumContainerName);
        try {
            ContainerInfo containerInfo = dockerClient.inspectContainer(zaleniumContainerId);
            ImmutableMap<String, AttachedNetwork> networks = containerInfo.networkSettings().networks();
            for (Map.Entry<String, AttachedNetwork> networkEntry : networks.entrySet()) {
                if (!DEFAULT_DOCKER_NETWORK_NAME.equalsIgnoreCase(networkEntry.getKey())) {
                    zaleniumNetwork = networkEntry.getKey();
                    return zaleniumNetwork;
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while getting Zalenium network.", e);
            ga.trackException(e);
        }
        zaleniumNetwork = DEFAULT_DOCKER_NETWORK_MODE;
        return zaleniumNetwork;
    }

    @Override
    public String getContainerIp(String containerName) {
        String containerId = this.getContainerId(containerName);
        if (containerId == null) {
            return null;
        }
        try {
            ContainerInfo containerInfo = dockerClient.inspectContainer(containerId);
            if (containerInfo.networkSettings().ipAddress().trim().isEmpty()) {
                ImmutableMap<String, AttachedNetwork> networks = containerInfo.networkSettings().networks();
                return networks.entrySet().stream().findFirst().get().getValue().ipAddress();
            }
            return containerInfo.networkSettings().ipAddress();
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while getting the container IP.", e);
            ga.trackException(e);
        }
        return null;
    }
}


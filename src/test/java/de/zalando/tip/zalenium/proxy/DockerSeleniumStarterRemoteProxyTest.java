package de.zalando.tip.zalenium.proxy;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.Environment;
import de.zalando.tip.zalenium.util.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class DockerSeleniumStarterRemoteProxyTest {

    private DockerSeleniumStarterRemoteProxy spyProxy;
    private Registry registry;

    @Before
    public void setUp() throws DockerException, InterruptedException {
        registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());

        // Creating the proxy
        DockerSeleniumStarterRemoteProxy proxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // Mock the docker client
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerCreation containerCreation = mock(ContainerCreation.class);
        when(containerCreation.id()).thenReturn("ANY_CONTAINER_ID");
        when(dockerClient.createContainer(any(ContainerConfig.class), anyString())).thenReturn(containerCreation);

        DockerSeleniumStarterRemoteProxy.setDockerClient(dockerClient);

        // Spying on the proxy to see if methods are invoked or not
        spyProxy = spy(proxy);
    }

    @After
    public void afterMethod() {
        registry.removeIfPresent(spyProxy);
    }

    @AfterClass
    public static void tearDown() {
        DockerSeleniumStarterRemoteProxy.restoreDockerClient();
        DockerSeleniumStarterRemoteProxy.restoreEnvironment();
        DockerSeleniumStarterRemoteProxy.restoreCommonProxyUtilities();
    }

    @Test
    public void noContainerIsStartedWhenCapabilitiesAreNotSupported() throws DockerException, InterruptedException {

        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.MAC);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(anyString());
    }

    @Test
    public void noContainerIsStartedWhenPlatformIsNotSupported() {
        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.WINDOWS);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(anyString());
    }

    @Test
    public void containerIsStartedWhenChromeCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, times(1)).startDockerSeleniumContainer(BrowserType.CHROME);
    }

    @Test
    public void containerIsStartedWhenFirefoxCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX);
    }

    /*
        The following tests check that if for any reason the capabilities from DockerSelenium cannot be
        fetched, it should fallback to the default ones.
     */

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlIsNotValid() {
        // Clearing the current loaded capabilities in the class
        DockerSeleniumStarterRemoteProxy.clearCapabilities();

        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "this_is_not_a_url";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlReturnsNoJson() {
        // Clearing the current loaded capabilities in the class
        DockerSeleniumStarterRemoteProxy.clearCapabilities();

        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "https://www.google.com";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlReturnsAJsonWithWrongFields() {
        // Clearing the current loaded capabilities in the class
        DockerSeleniumStarterRemoteProxy.clearCapabilities();

        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "http://ip.jsontest.com/";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

    /*
        Tests checking the environment variables setup to have a given number of containers on startup
     */

    @Test
    public void fallbackToDefaultAmountOfValuesWhenVariablesAreNotSet() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class);
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn(null);
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn(null);
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn(null);
        DockerSeleniumStarterRemoteProxy.setEnvironment(environment);

        registry.add(spyProxy);

        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
    }

    @Test
    public void fallbackToDefaultAmountValuesWhenVariablesAreNotIntegers() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class);
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        DockerSeleniumStarterRemoteProxy.setEnvironment(environment);

        registry.add(spyProxy);

        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
    }

    @Test
    public void variablesGrabTheConfiguredEnvironmentVariables() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class);
        int amountOfChromeContainers = 4;
        int amountOfFirefoxContainers = 3;
        int amountOfMaxContainers = 8;
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn(String.valueOf(amountOfChromeContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn(String.valueOf(amountOfFirefoxContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn(String.valueOf(amountOfMaxContainers));
        DockerSeleniumStarterRemoteProxy.setEnvironment(environment);

        registry.add(spyProxy);

        Assert.assertEquals(amountOfChromeContainers, DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(amountOfFirefoxContainers, DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(amountOfMaxContainers, DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
    }

    @Test
    public void amountOfCreatedContainersIsTheConfiguredOne() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class);
        int amountOfChromeContainers = 3;
        int amountOfFirefoxContainers = 4;
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn(String.valueOf(amountOfChromeContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn(String.valueOf(amountOfFirefoxContainers));

        DockerSeleniumStarterRemoteProxy.setEnvironment(environment);

        registry.add(spyProxy);

        verify(spyProxy, times(amountOfChromeContainers)).startDockerSeleniumContainer(BrowserType.CHROME);
        verify(spyProxy, times(amountOfFirefoxContainers)).startDockerSeleniumContainer(BrowserType.FIREFOX);
        Assert.assertEquals(amountOfChromeContainers, DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(amountOfFirefoxContainers, DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
    }

    @Test
    public void noNegativeValuesAreAllowedForStartup() {
        DockerSeleniumStarterRemoteProxy.setChromeContainersOnStartup(-1);
        DockerSeleniumStarterRemoteProxy.setFirefoxContainersOnStartup(-1);
        DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(-1);
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
    }

    @Test
    public void useDockerSeleniumFallbackCapabilitiesWhenTheOnesFromGitHubAreNotAvailable() {
        // Mocking the utility class that fetches the json from a given url
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString())).thenReturn(null);
        DockerSeleniumStarterRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);

        // Clearing the current loaded capabilities in the class
        DockerSeleniumStarterRemoteProxy.clearCapabilities();

        // Check that the registration request gets updated with after it is processed by the proxy
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());

        // Assuring the capabilities are empty
        Assert.assertTrue(request.getCapabilities().isEmpty());

        request = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(request,
                DockerSeleniumStarterRemoteProxy.DOCKER_SELENIUM_CAPABILITIES_URL);

        // Now the capabilities should be filled even if the url was not fetched
        Assert.assertFalse(request.getCapabilities().isEmpty());

        Assert.assertEquals("Should return 2 capabilities", request.getCapabilities().size(), 2);
        Assert.assertThat(request.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(request.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(request.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

}

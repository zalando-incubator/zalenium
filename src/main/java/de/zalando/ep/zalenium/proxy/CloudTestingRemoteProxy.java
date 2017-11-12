package de.zalando.ep.zalenium.proxy;

/*
    Many concepts and ideas are inspired from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityMatcher;
import de.zalando.ep.zalenium.servlet.renderer.CloudProxyHtmlRenderer;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.io.FileUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("WeakerAccess")
public class CloudTestingRemoteProxy extends DefaultRemoteProxy {

    @VisibleForTesting
    public static final long DEFAULT_MAX_TEST_IDLE_TIME_SECS = 90L;
    private static final Logger logger = Logger.getLogger(CloudTestingRemoteProxy.class.getName());
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static final Environment defaultEnvironment = new Environment();
    private static GoogleAnalyticsApi ga = defaultGA;
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;
    private static Environment env = defaultEnvironment;
    private final HtmlRenderer renderer = new CloudProxyHtmlRenderer(this);
    private CloudProxyNodePoller cloudProxyNodePoller = null;
    private CapabilityMatcher capabilityHelper;
    private long maxTestIdleTime = DEFAULT_MAX_TEST_IDLE_TIME_SECS;

    @SuppressWarnings("WeakerAccess")
    public CloudTestingRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(request, registry);
    }

    protected static GoogleAnalyticsApi getGa() {
        return ga;
    }

    @VisibleForTesting
    static void setGa(GoogleAnalyticsApi ga) {
        CloudTestingRemoteProxy.ga = ga;
    }

    @VisibleForTesting
    static void restoreGa() {
        ga = defaultGA;
    }

    protected static CommonProxyUtilities getCommonProxyUtilities() {
        return commonProxyUtilities;
    }

    @VisibleForTesting
    public static void setCommonProxyUtilities(final CommonProxyUtilities utilities) {
        commonProxyUtilities = utilities;
    }

    public static Environment getEnv() {
        return env;
    }

    @VisibleForTesting
    static void restoreCommonProxyUtilities() {
        commonProxyUtilities = defaultCommonProxyUtilities;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    public static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest,
                                                                           int concurrency, String proxyName) {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, concurrency);
        desiredCapabilities.setBrowserName(proxyName);
        desiredCapabilities.setPlatform(Platform.ANY);
        registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
        registrationRequest.getConfiguration().maxSession = concurrency;
        return registrationRequest;
    }

    public long getMaxTestIdleTime() {
        return maxTestIdleTime;
    }

    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting
    public void setMaxTestIdleTime(long maxTestIdleTime) {
        this.maxTestIdleTime = maxTestIdleTime;
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        /*
            Validate first if the capability is matched
        */
        if (!hasCapability(requestedCapability)) {
            return null;
        }
        logger.log(Level.INFO, () ->"Test will be forwarded to " + getProxyName() + ", " + requestedCapability);
        return super.getNewSession(requestedCapability);
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.START_SESSION)) {
                String body = seleniumRequest.getBody();
                JsonObject jsonObject = new JsonParser().parse(body).getAsJsonObject();
                JsonObject desiredCapabilities = jsonObject.getAsJsonObject("desiredCapabilities");
                desiredCapabilities.addProperty(getUserNameProperty(), getUserNameValue());
                desiredCapabilities.addProperty(getAccessKeyProperty(), getAccessKeyValue());
                if (!desiredCapabilities.has(CapabilityType.VERSION) && proxySupportsLatestAsCapability()) {
                    desiredCapabilities.addProperty(CapabilityType.VERSION, "latest");
                }
                try {
                    seleniumRequest.setBody(jsonObject.toString());
                } catch (UnsupportedEncodingException e) {
                    logger.log(Level.SEVERE, () ->"Error while setting the body request in " + getProxyName()
                            + ", " + jsonObject.toString());
                }
            }
        }
        super.beforeCommand(session, request, response);
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "DELETE".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.STOP_SESSION)) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                getGa().testEvent(getProxyClassName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                addTestToDashboard(session.getExternalKey().getKey(), true);
            }
        }
        super.afterCommand(session, request, response);
    }

    @Override
    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    public String getProxyClassName() {
        return null;
    }

    public String getUserNameProperty() {
        return null;
    }

    public String getUserNameValue() {
        return null;
    }

    public String getAccessKeyProperty() {
        return null;
    }

    public String getAccessKeyValue() {
        return null;
    }

    public String getCloudTestingServiceUrl() {
        return null;
    }

    public TestInformation getTestInformation(String seleniumSessionId) {
        return null;
    }

    public String getProxyName() {
        return null;
    }

    public String getVideoFileExtension() {
        return null;
    }

    public boolean proxySupportsLatestAsCapability() {
        return false;
    }

    public boolean useAuthenticationToDownloadFile() {
        return false;
    }

    public boolean convertVideoFileToMP4() {
        return false;
    }

    public void addTestToDashboard(String seleniumSessionId, boolean testCompleted) {
        new Thread(() -> {
            try {
                TestInformation testInformation = getTestInformation(seleniumSessionId);
                TestInformation.TestStatus status = testCompleted ?
                        TestInformation.TestStatus.COMPLETED : TestInformation.TestStatus.TIMEOUT;
                testInformation.setTestStatus(status);
                String fileNameWithFullPath = testInformation.getVideoFolderPath() + "/" + testInformation.getFileName();
                commonProxyUtilities.downloadFile(testInformation.getVideoUrl(), fileNameWithFullPath,
                        getUserNameValue(), getAccessKeyValue(), useAuthenticationToDownloadFile());
                if (convertVideoFileToMP4()) {
                    commonProxyUtilities.convertFlvFileToMP4(testInformation);
                }
                for (String logUrl : testInformation.getLogUrls()) {
                    String fileName = logUrl.substring(logUrl.lastIndexOf('/') + 1);
                    fileNameWithFullPath = testInformation.getLogsFolderPath() + "/" + fileName;
                    commonProxyUtilities.downloadFile(logUrl, fileNameWithFullPath,
                            getUserNameValue(), getAccessKeyValue(), useAuthenticationToDownloadFile());
                }
                createFeatureNotImplementedFile(testInformation.getLogsFolderPath());
                Dashboard.updateDashboard(testInformation);
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }).start();
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new ZaleniumCapabilityMatcher(this);
        }
        return capabilityHelper;
    }

    /*
        Making the node seem as heavily used, in order to get it listed after the 'docker-selenium' nodes.
        99% used.
    */
    @Override
    public float getResourceUsageInPercent() {
        return 99;
    }

    @Override
    public URL getRemoteHost() {
        try {
            return new URL(getCloudTestingServiceUrl());
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return null;
    }

    private void createFeatureNotImplementedFile(String logsFolderPath) {
        String fileNameWithFullPath = logsFolderPath + "/not_implemented.log";
        File notImplemented = new File(fileNameWithFullPath);
        try {
            String textToWrite = String.format("Feature not implemented for %s, we are happy to receive PRs", getProxyName());
            FileUtils.writeStringToFile(notImplemented, textToWrite, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.INFO, e.toString(), e);
        }

    }

    @Override
    public void startPolling() {
        super.startPolling();
        cloudProxyNodePoller = new CloudProxyNodePoller(this);
        cloudProxyNodePoller.start();
    }

    @Override
    public void stopPolling() {
        super.stopPolling();
        cloudProxyNodePoller.interrupt();
    }

    @Override
    public void teardown() {
        super.teardown();
        stopPolling();
    }

    /*
        Method to check for test inactivity, and terminate idle sessions
     */
    @VisibleForTesting
    public void terminateIdleSessions() {
        for (TestSlot testSlot : getTestSlots()) {
            if (testSlot.getSession() != null &&
                    (testSlot.getSession().getInactivityTime() >= (getMaxTestIdleTime() * 1000L))) {
                long executionTime = (System.currentTimeMillis() - testSlot.getLastSessionStart()) / 1000;
                getGa().testEvent(getProxyClassName(), testSlot.getSession().getRequestedCapabilities().toString(),
                        executionTime);
                addTestToDashboard(testSlot.getSession().getExternalKey().getKey(), false);
                getRegistry().forceRelease(testSlot, SessionTerminationReason.ORPHAN);
                logger.log(Level.INFO, getProxyName() + " Releasing slot and terminating session due to inactivity.");
            }
        }
    }

    /*
        Class to poll continuously the slots status to check if there is an idle test. It could happen that the test
        did not finish properly so we need to release the slot as well.
    */
    static class CloudProxyNodePoller extends Thread {

        private static long sleepTimeBetweenChecks = 500;
        private CloudTestingRemoteProxy cloudProxy = null;

        CloudProxyNodePoller(CloudTestingRemoteProxy cloudProxy) {
            this.cloudProxy = cloudProxy;
        }

        protected long getSleepTimeBetweenChecks() {
            return sleepTimeBetweenChecks;
        }

        @Override
        public void run() {
            while (true) {
                /*
                    Checking continuously for idle sessions. It may happen that the session is terminated abnormally
                    remotely and the slot needs to be released locally as well.
                */
                cloudProxy.terminateIdleSessions();
                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    logger.log(Level.FINE, cloudProxy.getProxyName() + " Error while sleeping the thread.", e);
                    return;
                }
            }
        }
    }

}

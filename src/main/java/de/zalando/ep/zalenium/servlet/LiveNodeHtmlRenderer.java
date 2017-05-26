package de.zalando.ep.zalenium.servlet;

import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.Environment;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.beta.MiniCapability;
import org.openqa.grid.web.servlet.beta.SlotsLines;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LiveNodeHtmlRenderer implements HtmlRenderer {

    private static final Logger LOGGER = Logger.getLogger(LiveNodeHtmlRenderer.class.getName());

    private final Environment defaultEnvironment = new Environment();
    private Environment env = defaultEnvironment;
    private DockerSeleniumRemoteProxy proxy;
    private String serverName;
    private TemplateRenderer templateRenderer;

    @SuppressWarnings("WeakerAccess")
    public LiveNodeHtmlRenderer(DockerSeleniumRemoteProxy proxy, String serverName) {
        this.proxy = proxy;
        this.serverName = serverName;
        this.templateRenderer = new TemplateRenderer(getTemplateName());
    }

    private String getTemplateName() {
        return "html_templates/live_node_tab.html";
    }


    /**
     * Platform for docker-selenium will be always Linux.
     *
     * @param proxy remote proxy
     * @return Either the platform name, "Unknown", "mixed OS", or "not specified".
     */
    @SuppressWarnings("WeakerAccess")
    public static String getPlatform(DockerSeleniumRemoteProxy proxy) {
        return getPlatform(proxy.getTestSlots().get(0)).toString();
    }

    private static Platform getPlatform(TestSlot slot) {
        return (Platform) slot.getCapabilities().get(CapabilityType.PLATFORM);
    }

    @Override
    public String renderSummary() {
        StringBuilder testName = new StringBuilder();
        if (!proxy.getTestName().isEmpty()) {
            testName.append("<p>Test name: ").append(proxy.getTestName()).append("</p>");
        }
        StringBuilder testGroup = new StringBuilder();
        if (!proxy.getTestGroup().isEmpty()) {
            testGroup.append("<p>Test group: ").append(proxy.getTestGroup()).append("</p>");
        }

        SlotsLines wdLines = new SlotsLines();
        TestSlot testSlot = proxy.getTestSlots().get(0);
        wdLines.add(testSlot);
        MiniCapability miniCapability = wdLines.getLinesType().iterator().next();
        String icon = miniCapability.getIcon();
        String version = miniCapability.getVersion();
        TestSession session = testSlot.getSession();
        String slotClass = "";
        String slotTitle;
        if (session != null) {
            slotClass = "busy";
            slotTitle = session.get("lastCommand") == null ? "" : session.get("lastCommand").toString();
        } else {
            slotTitle = testSlot.getCapabilities().toString();
        }

        // Adding live preview
        int vncPort = proxy.getRemoteHost().getPort() + 10000;
        int mainVncPort = env.getIntEnvVariable("ZALENIUM_CONTAINER_LIVE_PREVIEW_PORT", 5555);
        String vncViewBaseUrl = "http://%s:%s/proxy/%s/?nginx=%s&view_only=%s";
        String vncReadOnlyUrl = String.format(vncViewBaseUrl, serverName, mainVncPort, vncPort, vncPort, "true");
        String vncInteractUrl = String.format(vncViewBaseUrl, serverName, mainVncPort, vncPort, vncPort, "false");

        Map<String, String> renderSummaryValues = new HashMap<>();
        renderSummaryValues.put("{{proxyName}}", proxy.getClass().getSimpleName());
        renderSummaryValues.put("{{proxyVersion}}", getHtmlNodeVersion());
        renderSummaryValues.put("{{proxyId}}", proxy.getId());
        renderSummaryValues.put("{{proxyIdEncoded}}", getUrlEncodedProxyId());
        renderSummaryValues.put("{{proxyPlatform}}", getPlatform(proxy));
        renderSummaryValues.put("{{testName}}", testName.toString());
        renderSummaryValues.put("{{testGroup}}", testGroup.toString());
        renderSummaryValues.put("{{browserVersion}}", version);
        renderSummaryValues.put("{{slotIcon}}", icon);
        renderSummaryValues.put("{{slotClass}}", slotClass);
        renderSummaryValues.put("{{slotTitle}}", slotTitle);
        renderSummaryValues.put("{{vncReadOnlyUrl}}", vncReadOnlyUrl);
        renderSummaryValues.put("{{vncInteractUrl}}", vncInteractUrl);
        renderSummaryValues.put("{{tabConfig}}", proxy.getConfig().toString("<p>%1$s: %2$s</p>"));
        return templateRenderer.renderTemplate(renderSummaryValues);
    }

    private String getUrlEncodedProxyId() {
        try {
            return URLEncoder.encode(proxy.getId(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private String getHtmlNodeVersion() {
        try {
            JsonObject object = proxy.getStatus();
            String version = object.get("value").getAsJsonObject()
                    .get("build").getAsJsonObject()
                    .get("version").getAsString();
            return " (version : " + version + ")";
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
            return " unknown version, " + e.getMessage();
        }
    }
}

package de.zalando.ep.zalenium.dashboard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import com.google.common.io.ByteStreams;

@SuppressWarnings("WeakerAccess")
public class DashboardCleanupServlet extends RegistryBasedServlet {

    private static final String DO_CLEANUP_ALL = "doCleanupAll";

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DashboardCleanupServlet.class.getName());

    @SuppressWarnings("unused")
    public DashboardCleanupServlet() {
        this(null);
    }

    public DashboardCleanupServlet(ZaleniumRegistry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = "";
        try {
            action = request.getParameter("action");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
        }

        String resultMsg;
        int responseStatus;
        if (DO_CLEANUP_ALL.equals(action)) {
            Dashboard.cleanupDashboard();
            resultMsg = "SUCCESS";
            responseStatus = 200;
        } else {
            resultMsg = "ERROR action not implemented. Given action=" + action;
            responseStatus = 400;
        }
        sendMessage(response, resultMsg, responseStatus);
    }

    private void sendMessage(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);

        try (InputStream in = new ByteArrayInputStream(message.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }
}

package dk.naestebus;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dk.naestebus.datasupplier.DataSupplier;
import dk.naestebus.model.Departure;
import dk.naestebus.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A {@code DeparturesServlet} serves {@link dk.naestebus.model.Departure}s for a {@link dk.naestebus.model.Stop}.
 * <p>
 * Retrieve departures using HTTP GET, given the parameters {@code stopId} and {@code max}. The response is in JSON format.
 * <p>
 * {@code stopId} is the id of the stop. Note that this value is volatile, so don't depend on it being stable over large time periods (i.e. days).
 * {@code max} is the maximum number of departures returned. Note that there is an unspecified internal maximum also.
 * <p>
 * Example usage:
 * <p>
 * <code>$ curl "http://localhost:8080/departures?stopId=751464200&max=2"</code>
 * <p>
 * <code>[{"name":"Bus 2A","direction":"Bjødstrupvej/Karetmagertoften (Aarhus)","hasDirection":true,"time":"21:05"},
 * {"name":"Bus 2A","direction":"Aarhus Uni. hospital  Skejby indg 8-11","hasDirection":true,"time":"21:12"}]</code>
 */
@Singleton
public class DeparturesServlet extends HttpServlet {

    private static final long serialVersionUID = 0;

    private static final String STOP_ID_PARAMETER_NAME = "stopId";
    private static final String MAX_PARAMETER_NAME = "max";

    private static final Logger log = LoggerFactory.getLogger(DeparturesServlet.class);

    private final transient DataSupplier dataSupplier;
    private final transient Serializer serializer;

    @Inject
    DeparturesServlet(@DataSupplier.Caching DataSupplier dataSupplier, Serializer serializer) {
        this.dataSupplier = dataSupplier;
        this.serializer = serializer;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String stopId = request.getParameter(STOP_ID_PARAMETER_NAME);
        String max = request.getParameter(MAX_PARAMETER_NAME);

        if (!checkArguments(stopId, max)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        ImmutableList<Departure> departures;
        try {
            departures = dataSupplier.getNextDepartures(stopId);
        } catch (IOException e) {
            log.warn("Couldn't get departures from data supplier.", e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        }

        // Only return max number of departures
        if (!departures.isEmpty()) {
            departures = departures.subList(0, Math.min(departures.size(), Integer.parseInt(max)));
        }
        response.setContentType(ServletConfig.MIME_RESPONSE_TYPE);
        response.setCharacterEncoding(ServletConfig.CHARACTER_ENCODING);
        PrintWriter writer = response.getWriter();
        writer.write(serializer.serializeDepartures(departures));
        writer.flush();
    }

    private boolean checkArguments(String stopId, String max) {
        if (stopId == null
            || !ServletUtil.BASIC_STRING_ONLY_PATTERN.matcher(stopId).matches()
            || max == null
            || !ServletUtil.INTEGER_ONLY_PATTERN.matcher(max).matches()
            || Integer.parseInt(max) <= 0) {
            return false;
        }
        return true;
    }
}

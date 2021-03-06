package dk.naestebus.datasupplier;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import dk.naestebus.model.Coordinate;
import dk.naestebus.model.Departure;
import dk.naestebus.model.Stop;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.ReadableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Supplies data to our web service through Rejseplanen. See the <a href="http://labs.rejseplanen.dk/labs/data__brug/rejseplanens_api/">Rejseplan API doc</a>.
 * <p>
 * This class is thread-safe.
 */
@Singleton
public final class RejseplanDataSupplier implements DataSupplier {

    private static final String BASE_URL = "http://xmlopen.rejseplanen.dk/bin/rest.exe/";
    private static final String NEARBY_STOPS_URL = "stopsNearby?coordX=%d&coordY=%d&maxRadius=%d&maxNumber=%d";
    private static final String DEPARTURES_URL = "departureBoard?id=%s&date=%s&time=%s&useTog=0&useMetro=0";

    private static final String TIMEZONE_ID = "Europe/Copenhagen";
    private static final String DATE_FORMAT = "dd.MM.yy";
    private static final String TIME_FORMAT = "HH:mm";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern(DATE_FORMAT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern(TIME_FORMAT);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern(DATE_FORMAT + TIME_FORMAT);

    private static final String XML_STOP = "StopLocation";
    private static final String XML_STOP_ID = "id";
    private static final String XML_STOP_NAME = "name";
    private static final String XML_STOP_LATITUDE = "y";
    private static final String XML_STOP_LONGITUDE = "x";

    private static final String XML_DEPARTURE = "Departure";
    private static final String XML_DEPARTURE_NAME = "name";
    private static final String XML_DEPARTURE_DATE = "date";
    private static final String XML_DEPARTURE_TIME = "time";
    private static final String XML_DEPARTURE_DIRECTION = "direction";

    private static final Logger log = LoggerFactory.getLogger(RejseplanDataSupplier.class);

    @Override
    public ImmutableList<Stop> getNearbyStops(Coordinate coordinate, int radius, int max) throws IOException {
        final ImmutableList.Builder<Stop> stops = ImmutableList.builder();

        connectAndParse(BASE_URL + String.format(NEARBY_STOPS_URL, coordinate.getLongitude(), coordinate.getLatitude(), radius, max), new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if (!XML_STOP.equals(qName)) {
                    return;
                }

                Stop stop = parseStop(attributes);
                stops.add(stop);
            }
        });

        return stops.build();
    }

    private Stop parseStop(Attributes attributes) {
        return Stop.newBuilder()
                .setId(attributes.getValue(XML_STOP_ID))
                .setName(attributes.getValue(XML_STOP_NAME))
                .setCoordinate(new Coordinate(Integer.parseInt(attributes.getValue(XML_STOP_LATITUDE)), Integer.parseInt(attributes.getValue(XML_STOP_LONGITUDE))))
                .build();
    }

    @Override
    public ImmutableList<Departure> getNextDepartures(String stopId) throws IOException {
        final ImmutableList.Builder<Departure> departures = ImmutableList.builder();

        final DateTime now = DateTime.now().toDateTime(DateTimeZone.forID(TIMEZONE_ID));
        connectAndParse(BASE_URL + String.format(DEPARTURES_URL, stopId, DATE_FORMATTER.print(now), TIME_FORMATTER.print(now)), new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if (!XML_DEPARTURE.equals(qName)) {
                    return;
                }

                Departure departure = parseDeparture(attributes, now);
                departures.add(departure);
            }
        });

        return departures.build();
    }

    private Departure parseDeparture(Attributes attributes, ReadableDateTime time) {
        Departure.Builder departure = Departure.newBuilder();
        departure.setName(attributes.getValue(XML_DEPARTURE_NAME));

        // As no time zone is given, assume the one from the parameter
        String xmlDate = attributes.getValue(XML_DEPARTURE_DATE);
        String xmlTime = attributes.getValue(XML_DEPARTURE_TIME);
        LocalDateTime localTime = DATE_TIME_FORMATTER.parseLocalDateTime(xmlDate + xmlTime);
        departure.setTime(localTime.toDateTime(time.getZone()));

        String xmlDirection = attributes.getValue(XML_DEPARTURE_DIRECTION);
        if (xmlDirection != null) {
            departure.setDirection(xmlDirection);
        }
        return departure.build();
    }

    private void connectAndParse(String urlAsString, DefaultHandler handler) throws IOException {
        log.debug("Connecting to {}...", urlAsString);
        URL url = new URL(urlAsString);
        URLConnection connection = url.openConnection();
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            parser.parse(in, handler);
        } catch (MalformedURLException e) {
            // This should never happen, and is a programming error. Therefore, throw it again as a RuntimeException.
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            // This likewise should never happen, and is a programming error. Therefore, throw it again as a RuntimeException.
            throw new RuntimeException(e);
        } catch (SAXException e) {
            // Just rethrow as connection error
            throw new IOException(e);
        }
    }
}

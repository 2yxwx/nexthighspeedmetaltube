package com.nexthighspeedmetaltube.datasupplier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.nexthighspeedmetaltube.model.Coordinate;
import com.nexthighspeedmetaltube.model.Departure;
import com.nexthighspeedmetaltube.model.Stop;

import java.io.IOException;
import java.util.Map;

/**
 * A {@code CachingDataSupplier} checks the cache for data before sending the request on to the component
 * {@link com.nexthighspeedmetaltube.datasupplier.DataSupplier} (decorator pattern).
 * <p>
 * Note that the cache mechanism is rather primitive as of yet. Nearby stops are not cached at all,
 * whereas departures are cached until the first departure in the list has happened. Everything is in-memory.
 * <p>
 * This class is thread-safe. A race condition might occur when two threads access the cache simultaneously,
 * but the only consequence is a double call to the underlying data supplier, which is okay.
 */
@Singleton
public final class CachingDataSupplier implements DataSupplier {

    private final DataSupplier dataSupplier;

    private final Map<String, ImmutableList<Departure>> cache = Maps.newConcurrentMap();

    public CachingDataSupplier(DataSupplier dataSupplier) {
        this.dataSupplier = dataSupplier;
    }

    @Override
    public ImmutableList<Stop> getNearbyStops(Coordinate coordinate, int radius, int max) throws IOException {
        return dataSupplier.getNearbyStops(coordinate, radius, max);
    }

    @Override
    public ImmutableList<Departure> getNextDepartures(String stopId) throws IOException {
        // Try cache
        ImmutableList<Departure> cached = cache.get(stopId);

        // If cache is too old (not in the future) or we have a cache miss, do caching and return
        if ((cached != null && !cached.get(0).getTime().isAfterNow()) || cached == null) {
            ImmutableList<Departure> departures = dataSupplier.getNextDepartures(stopId);
            if (!departures.isEmpty()) {
                cache.put(stopId, departures);
            }
            return departures;
        }

        return cached;
    }
}

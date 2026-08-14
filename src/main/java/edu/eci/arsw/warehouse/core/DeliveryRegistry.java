package edu.eci.arsw.warehouse.core;

import edu.eci.arsw.warehouse.model.DeliveryRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Intentionally unsafe shared registry.
 */
public class DeliveryRegistry {

    private int nextPosition = 1;
    private final List<DeliveryRecord> deliveries = new ArrayList<>();

    public synchronized void register(int robotId, int parcelId, long elapsedMillis) {
        int assignedPosition = nextPosition;
        nextPosition = nextPosition + 1;
        deliveries.add(new DeliveryRecord(assignedPosition, robotId, parcelId, elapsedMillis));
    }

    public List<DeliveryRecord> snapshot() {
        return List.copyOf(deliveries);
    }
}

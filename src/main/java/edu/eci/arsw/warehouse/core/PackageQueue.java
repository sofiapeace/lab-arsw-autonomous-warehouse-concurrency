package edu.eci.arsw.warehouse.core;

import edu.eci.arsw.warehouse.model.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Intentionally unsafe starter implementation.
 *
 * Students: do not simply synchronize every public method without analysis.
 * First identify the invariant and the minimum critical region.
 */
public class PackageQueue {

    private final List<Parcel> pending = new ArrayList<>();

    public PackageQueue(List<Parcel> parcels) {
        pending.addAll(parcels);
    }

    public synchronized Parcel takeNext() {
        if (pending.isEmpty()) {
            return null;
        }
        Parcel selected = pending.get(0);
        pending.remove(0);
        return selected;
    }

    public int pendingCount() {
        return pending.size();
    }
}

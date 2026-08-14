package edu.eci.arsw.warehouse.core;

/**
 * Intentionally unsafe counters. ++ and += are not atomic read-modify-write operations.
 */
public class WarehouseStatistics {

    private int processedParcels;
    private long totalProcessingMillis;

   public synchronized void recordProcessed(long elapsedMillis) {
        processedParcels++;
        totalProcessingMillis += elapsedMillis;
    }

    public int processedParcels() {
        return processedParcels;
    }

    public long totalProcessingMillis() {
        return totalProcessingMillis;
    }
}

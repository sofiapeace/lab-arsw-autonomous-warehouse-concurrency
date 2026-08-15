package edu.eci.arsw.warehouse.app;

import edu.eci.arsw.warehouse.model.DeliveryRecord;
import edu.eci.arsw.warehouse.model.WarehouseSnapshot;

import java.util.Comparator;

public final class WarehouseMain {

    private WarehouseMain() {
    }

    public static void main(String[] args) throws Exception {
        int robots = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int parcels = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        WarehouseSimulation simulation = new WarehouseSimulation(robots, parcels);

        System.out.printf("Starting warehouse with %d robots and %d parcels...%n", robots, parcels);
        simulation.start();

        // Block until every robot thread has actually terminated. join() waits on a real
        // event -- thread death -- while Thread.sleep(...) only guesses a duration and is
        // wrong as soon as the load, the core count or the scheduler changes.
        simulation.awaitCompletion();

        System.out.println("\n--- FINAL REPORT ---");
        printSnapshot(simulation.snapshot());
        System.out.println("--------------------\n");
    }

    static void printSnapshot(WarehouseSnapshot snapshot) {
        System.out.printf("Initial parcels : %d%n", snapshot.initialParcels());
        System.out.printf("Pending parcels : %d%n", snapshot.pendingParcels());
        System.out.printf("Processed count : %d%n", snapshot.processedParcels());
        System.out.printf("Registry size   : %d%n", snapshot.deliveries().size());

        snapshot.deliveries().stream()
                .min(Comparator.comparingInt(DeliveryRecord::position))
                .ifPresent(first -> System.out.printf(
                        "Current leader  : Robot-%02d / parcel %d / position %d%n",
                        first.robotId(), first.parcelId(), first.position()));
    }
}

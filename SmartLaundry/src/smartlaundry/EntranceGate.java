package smartlaundry;

import java.util.concurrent.CountDownLatch;

/**
 * The entrance: a separate thread that lets customers in one by one,
 * with a random gap of 0-3 seconds between arrivals (basic requirement 1).
 *
 * Arrivals are generated here, not by 50 threads each sleeping from time 0.
 * Sleeping from time 0 would make all 50 customers arrive within the first
 * 3 seconds, which does not match "customers arrive randomly every 0-3 seconds".
 *
 * The door is open for 60 seconds (Config.DOOR_OPEN_TIME). With an average
 * gap of 1.5 s, about 40 of the maximum 50 customers get in. Customers already
 * inside when the door closes still finish, so the whole run takes 65-75 s.
 */
public class EntranceGate extends Thread {

    private final LaundryFacility facility;
    private final Statistics statistics;
    private final CountDownLatch allCustomersDone;
    private final KioskOutage outage;

    public EntranceGate(LaundryFacility facility, Statistics statistics,
                        CountDownLatch allCustomersDone, KioskOutage outage) {
        super("Entrance");
        this.facility = facility;
        this.statistics = statistics;
        this.allCustomersDone = allCustomersDone;
        this.outage = outage;
    }

    @Override
    public void run() {
        int admitted = 0;
        try {
            for (int id = 1; id <= Config.CUSTOMERS; id++) {
                if (doorClosed()) {
                    break;                                    // 60 s are over: nobody else gets in
                }
                SimulationLogger.event(String.format("door opens - admitting Customer-%02d", id));
                new Customer(id, facility, statistics, allCustomersDone).start();
                admitted++;

                if (id < Config.CUSTOMERS) {
                    Thread.sleep(LaundryFacility.randomBetween(
                            Config.ARRIVAL_GAP_MIN, Config.ARRIVAL_GAP_MAX));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeDoor(admitted);
        }
    }

    private static boolean doorClosed() {
        return SimulationLogger.elapsedSeconds() * 1000 >= Config.DOOR_OPEN_TIME;
    }

    /** Runs once, when the entrance stops admitting customers. */
    private void closeDoor(int admitted) {
        int neverArrived = Config.CUSTOMERS - admitted;
        SimulationLogger.event("ENTRANCE CLOSED - " + admitted + " customers admitted, "
                + neverArrived + " did not arrive in time");

        // Main waits on a latch sized for the maximum of 50 customers.
        // Count down once for every customer who never came in, otherwise
        // Main would wait forever for threads that were never started.
        for (int i = 0; i < neverArrived; i++) {
            allCustomersDone.countDown();
        }

        // Bonus: tell the outage controller how many customers came today, so
        // the owner is still called if fewer than 30 arrived (prevents a deadlock).
        outage.entranceClosed(admitted);
    }
}

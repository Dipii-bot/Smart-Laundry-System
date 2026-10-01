package smartlaundry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The laundry itself: the three stages a customer goes through.
 *
 * Every stage follows the same safe pattern:
 *
 *     int id = pool.acquire();      // may block (waiting in the queue)
 *     try {
 *         ... use the machine ...
 *     } finally {
 *         pool.release(id);         // ALWAYS runs, even on error/interrupt
 *     }
 *
 * The try/finally guarantees a machine is never "lost". If a release were
 * skipped (e.g. after a washer failure) that permit would disappear forever,
 * the laundry would slowly run out of machines and customers would block
 * for ever.
 *
 * Randomness uses ThreadLocalRandom: each thread has its own generator, so
 * 50 customer threads never contend on one shared Random object.
 */
public class LaundryFacility {

    private final ResourcePool washers;
    private final ResourcePool dryers;
    private final ResourcePool kiosks;
    private final Statistics statistics;
    private final KioskOutage outage;

    public LaundryFacility(ResourcePool washers, ResourcePool dryers, ResourcePool kiosks,
                           Statistics statistics, KioskOutage outage) {
        this.washers = washers;
        this.dryers = dryers;
        this.kiosks = kiosks;
        this.statistics = statistics;
        this.outage = outage;
    }

    // =========================================================
    // STAGE 1: WASHING  (6 washers, 4-6 s, 5 % mid-cycle failure)
    // =========================================================
    public void wash(int customerId) throws InterruptedException {
        int attempt = 1;
        while (true) {
            SimulationLogger.customer(customerId, attempt == 1
                    ? "waiting for a washer"
                    : "re-joined washer queue (attempt " + attempt + ")");

            int washerId = washers.acquire();                 // blocks if all 6 are busy
            String washer = ResourceType.WASHER.machineName(washerId);
            boolean finished;

            try {
                int washTime = randomBetween(Config.WASH_MIN, Config.WASH_MAX);
                boolean willFail = ThreadLocalRandom.current().nextDouble() < Config.WASHER_FAIL_CHANCE;
                SimulationLogger.customer(customerId,
                        "started washing on " + washer + " (" + seconds(washTime) + ")");

                if (willFail) {
                    // Failure happens part-way through the cycle, not at the end.
                    int failAfter = randomBetween(1000, washTime - 1000);
                    Thread.sleep(failAfter);
                    statistics.washerFailed();
                    SimulationLogger.customer(customerId, "ERROR: " + washer
                            + " FAILED mid-cycle after " + seconds(failAfter) + " - taking clothes out");
                    finished = false;
                } else {
                    Thread.sleep(washTime);
                    SimulationLogger.customer(customerId, "finished washing on " + washer);
                    finished = true;
                }
            } finally {
                washers.release(washerId);                    // never skipped
            }
            SimulationLogger.customer(customerId, "released " + washer);

            if (finished) {
                return;
            }
            // Requirement: "customer must wait and retry"
            SimulationLogger.customer(customerId,
                    "waiting " + seconds(Config.WASH_RETRY_WAIT) + " before retrying the wash");
            Thread.sleep(Config.WASH_RETRY_WAIT);
            attempt++;
        }
    }

    // =========================================================
    // STAGE 2: DRYING  (4 dryers, 3-5 s)
    // =========================================================
    public void dry(int customerId) throws InterruptedException {
        SimulationLogger.customer(customerId, "waiting for a dryer");

        int dryerId = dryers.acquire();                       // blocks if all 4 are busy
        String dryer = ResourceType.DRYER.machineName(dryerId);
        try {
            int dryTime = randomBetween(Config.DRY_MIN, Config.DRY_MAX);
            SimulationLogger.customer(customerId,
                    "started drying on " + dryer + " (" + seconds(dryTime) + ")");
            Thread.sleep(dryTime);
            SimulationLogger.customer(customerId, "finished drying on " + dryer);
        } finally {
            dryers.release(dryerId);
        }
        SimulationLogger.customer(customerId, "released " + dryer);
    }

    // =========================================================
    // STAGE 3: PAYMENT  (2 kiosks, 1-2 s, 5 % failure -> retry after 2 s)
    // =========================================================
    public void pay(int customerId) throws InterruptedException {
        // Bonus: in the congested scenario this blocks until the owner repairs the kiosks.
        outage.waitUntilKiosksWork(customerId);

        while (true) {
            SimulationLogger.customer(customerId, "waiting for a payment kiosk");

            int kioskId = kiosks.acquire();                   // blocks if both kiosks are busy
            String kiosk = ResourceType.PAYMENT_KIOSK.machineName(kioskId);
            boolean paid;
            try {
                int payTime = randomBetween(Config.PAY_MIN, Config.PAY_MAX);
                SimulationLogger.customer(customerId,
                        "paying at " + kiosk + " (" + seconds(payTime) + ")");
                Thread.sleep(payTime);

                paid = ThreadLocalRandom.current().nextDouble() >= Config.KIOSK_FAIL_CHANCE;
                if (paid) {
                    SimulationLogger.customer(customerId, "payment SUCCESSFUL at " + kiosk);
                } else {
                    statistics.paymentFailed();
                    SimulationLogger.customer(customerId, "ERROR: " + kiosk + " payment FAILED");
                }
            } finally {
                kiosks.release(kioskId);
            }

            if (paid) {
                return;
            }
            SimulationLogger.customer(customerId,
                    "retrying payment in " + seconds(Config.PAY_RETRY_WAIT));
            Thread.sleep(Config.PAY_RETRY_WAIT);
        }
    }

    // ---- helpers ----

    /** Random int in [min, max] milliseconds, using this thread's own generator. */
    static int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static String seconds(int millis) {
        return String.format("%.1f s", millis / 1000.0);
    }
}

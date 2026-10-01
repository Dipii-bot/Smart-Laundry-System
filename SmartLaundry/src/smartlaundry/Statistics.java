package smartlaundry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects the statistics required by the case study:
 *   - total customers served
 *   - customers served in under 60 seconds (time in shop)
 *   - average total time per customer
 *   - max concurrent washers and dryers in use (read from the pools)
 * plus failure counters for the error-handling requirement.
 *
 * Two techniques are shown on purpose:
 *   - synchronized methods for the (served, totalTime) PAIR, because the two
 *     values must change together. Two separate atomics would let a reader see
 *     the new count with the old total and compute a wrong average.
 *   - AtomicInteger for independent single counters (failures), where one
 *     atomic incrementAndGet() is enough and no lock is needed.
 */
public class Statistics {

    // ---- Guarded by 'this' (synchronized methods) ----
    private int customersServed = 0;
    private long totalTimeMillis = 0;
    private int servedUnderTarget = 0;     // time in shop below Config.SERVICE_TARGET (60 s)

    // ---- Independent counters: lock-free atomics ----
    private final AtomicInteger customersArrived = new AtomicInteger(0);
    private final AtomicInteger washerFailures = new AtomicInteger(0);
    private final AtomicInteger paymentFailures = new AtomicInteger(0);

    private final ResourcePool washers;
    private final ResourcePool dryers;

    public Statistics(ResourcePool washers, ResourcePool dryers) {
        this.washers = washers;
        this.dryers = dryers;
    }

    public void customerArrived() { customersArrived.incrementAndGet(); }

    public int getCustomersArrived() { return customersArrived.get(); }

    /** Called once by each customer thread as it leaves. Both fields update as one atomic step. */
    public synchronized void customerServed(long timeInShopMillis) {
        customersServed++;
        totalTimeMillis += timeInShopMillis;
        if (timeInShopMillis < Config.SERVICE_TARGET) {
            servedUnderTarget++;
        }
    }

    public synchronized int getServedUnderTarget() { return servedUnderTarget; }

    public synchronized int getCustomersServed() { return customersServed; }

    public synchronized double getAverageTimeSeconds() {
        return customersServed == 0 ? 0.0 : (totalTimeMillis / 1000.0) / customersServed;
    }

    public void washerFailed()  { washerFailures.incrementAndGet(); }
    public void paymentFailed() { paymentFailures.incrementAndGet(); }

    public int getWasherFailures()  { return washerFailures.get(); }
    public int getPaymentFailures() { return paymentFailures.get(); }

    public int getMaxWashersInUse() { return washers.peakInUse(); }
    public int getMaxDryersInUse()  { return dryers.peakInUse(); }

    /** Printed by the main thread after every customer has finished. */
    public void printReport(double simulationSeconds) {
        SimulationLogger.separator();
        SimulationLogger.event("FINAL STATISTICS");
        SimulationLogger.separator();
        SimulationLogger.event("Customers arrived (60 s)   : " + getCustomersArrived() + " / " + Config.CUSTOMERS);
        SimulationLogger.event("Total customers served     : " + getCustomersServed() + " / " + getCustomersArrived());
        SimulationLogger.event("Served in under 60 s       : " + getServedUnderTarget() + " / " + getCustomersServed());
        SimulationLogger.event(String.format("Average time per customer  : %.2f s", getAverageTimeSeconds()));
        SimulationLogger.event("Max washers in use at once : " + getMaxWashersInUse() + " / " + Config.WASHERS);
        SimulationLogger.event("Max dryers in use at once  : " + getMaxDryersInUse() + " / " + Config.DRYERS);
        SimulationLogger.event("Washer failures (retried)  : " + getWasherFailures());
        SimulationLogger.event("Payment failures (retried) : " + getPaymentFailures());
        SimulationLogger.event(String.format("Total simulation time      : %.1f s", simulationSeconds));
        SimulationLogger.separator();
    }
}

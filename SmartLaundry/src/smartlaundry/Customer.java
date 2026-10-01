package smartlaundry;

import java.util.concurrent.CountDownLatch;

/**
 * One customer = one thread (basic requirement 1).
 *
 * The thread is started by the EntranceGate at the moment the customer walks
 * in, then runs the three stages in order: wash -> dry -> pay -> exit.
 * All shared state lives in LaundryFacility / ResourcePool / Statistics;
 * a Customer only keeps its own private fields, so nothing here needs locking.
 */
public class Customer extends Thread {

    private final int customerId;
    private final LaundryFacility facility;
    private final Statistics statistics;
    private final CountDownLatch allCustomersDone;

    public Customer(int customerId, LaundryFacility facility,
                    Statistics statistics, CountDownLatch allCustomersDone) {
        super(String.format("Customer-%02d", customerId));  // thread name shown in every log line
        this.customerId = customerId;
        this.facility = facility;
        this.statistics = statistics;
        this.allCustomersDone = allCustomersDone;
    }

    @Override
    public void run() {
        long arrivalTime = System.currentTimeMillis();       // local variable -> private to this thread
        statistics.customerArrived();
        SimulationLogger.customer(customerId, "ARRIVED at the laundry");
        try {
            facility.wash(customerId);
            facility.dry(customerId);
            facility.pay(customerId);

            long timeInShop = System.currentTimeMillis() - arrivalTime;
            statistics.customerServed(timeInShop);
            SimulationLogger.customer(customerId,
                    String.format("EXITED - total time %.1f s", timeInShop / 1000.0));

        } catch (InterruptedException e) {
            // Restore the interrupt flag so the thread's owner can see it was interrupted.
            Thread.currentThread().interrupt();
            SimulationLogger.customer(customerId, "interrupted - leaving early");
        } finally {
            allCustomersDone.countDown();                    // main thread waits for 50 of these
        }
    }
}

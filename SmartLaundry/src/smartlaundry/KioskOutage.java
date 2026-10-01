package smartlaundry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BONUS: Congested scenario.
 *
 * Both payment kiosks are broken for the day. Customers who finish drying
 * pile up at the payment area. When the 30th customer is stuck, the owner is
 * phoned; the owner (a separate thread) travels in, repairs the kiosks and
 * lets everyone continue.
 *
 * Java facilities used:
 *   - ReentrantLock + Condition : customers WAIT on the condition "kiosks are
 *     online" and the owner SIGNALS all of them at once (signalAll).
 *     This is the monitor pattern: await() releases the lock while sleeping,
 *     so the owner can get in and change the state.
 *   - CountDownLatch(1) : a one-time "phone call". The owner thread blocks on
 *     await() until the 30th customer calls countDown().
 *
 * In normal mode 'kiosksOnline' starts true and this class does nothing.
 */
public class KioskOutage {

    private final boolean congestedMode;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition kiosksRepaired = lock.newCondition();

    // ---- Guarded by 'lock' ----
    private boolean kiosksOnline;
    private int stuckCustomers = 0;
    private int callThreshold;          // 30, or fewer if fewer customers came in

    private final CountDownLatch ownerPhoneCall = new CountDownLatch(1);

    // Only written by the owner thread, read by the GUI -> volatile for visibility.
    private volatile String ownerStatus;

    public KioskOutage(boolean congestedMode) {
        this.congestedMode = congestedMode;
        this.kiosksOnline = !congestedMode;
        // Safety: if the threshold were larger than the number of customers the
        // owner would never be called and everyone would wait forever (deadlock).
        this.callThreshold = Math.min(Config.OWNER_CALL_THRESHOLD, Config.CUSTOMERS);
        this.ownerStatus = congestedMode ? "At home" : "Not needed";
    }

    /**
     * Called by a CUSTOMER thread before it queues for a kiosk.
     * Returns immediately if the kiosks work; otherwise blocks until repaired.
     */
    public void waitUntilKiosksWork(int customerId) throws InterruptedException {
        if (!congestedMode) {
            return;                       // final field, safe to read without the lock
        }
        lock.lock();
        try {
            if (kiosksOnline) {
                return;
            }
            stuckCustomers++;
            SimulationLogger.customer(customerId,
                    "BOTH KIOSKS OUT OF ORDER - stuck at payment (" + stuckCustomers + " waiting)");

            if (stuckCustomers == callThreshold) {
                // This customer makes the phone call; the owner reports his own actions.
                SimulationLogger.customer(customerId,
                        callThreshold + " customers stuck! Phoning the OWNER");
                ownerPhoneCall.countDown();
            }

            try {
                // Always await inside a while loop: protects against spurious
                // wake-ups and re-checks the real condition after waking.
                while (!kiosksOnline) {
                    kiosksRepaired.await();   // releases 'lock' while waiting
                }
            } finally {
                stuckCustomers--;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called by the ENTRANCE thread when the door closes after 60 s.
     * If fewer than 30 customers came in, the queue can never reach 30, so the
     * threshold is lowered to the number of customers who came. Without this
     * everyone would wait at payment for ever (a deadlock).
     */
    public void entranceClosed(int customersAdmitted) {
        if (!congestedMode) {
            return;
        }
        lock.lock();
        try {
            if (customersAdmitted < callThreshold) {
                callThreshold = customersAdmitted;
                SimulationLogger.event("only " + customersAdmitted
                        + " customers came today - owner will be called when all of them are stuck");
                if (stuckCustomers >= callThreshold) {
                    SimulationLogger.event("everyone is stuck at payment - phoning the OWNER");
                    ownerPhoneCall.countDown();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** Body of the OWNER thread. */
    public void ownerRoutine() throws InterruptedException {
        SimulationLogger.event("Owner is at home, phone on");
        ownerPhoneCall.await();                         // sleeps until the 30th customer calls

        ownerStatus = "Driving to shop";
        SimulationLogger.event("Owner received the call - driving to the laundry");
        Thread.sleep(Config.OWNER_TRAVEL_TIME);

        ownerStatus = "Repairing kiosks";
        SimulationLogger.event("Owner arrived. " + getStuckCustomers()
                + " customers in the payment queue. Rebooting both kiosks...");
        Thread.sleep(Config.KIOSK_REPAIR_TIME);

        lock.lock();
        try {
            kiosksOnline = true;
            kiosksRepaired.signalAll();                 // wake EVERY stuck customer
            SimulationLogger.event("Owner: both kiosks back ONLINE - releasing "
                    + stuckCustomers + " customers to pay");
        } finally {
            lock.unlock();
        }
        ownerStatus = "Kiosks repaired";
    }

    // ---- Read-only views for the GUI ----

    public boolean isCongestedMode() { return congestedMode; }

    public boolean areKiosksOnline() {
        lock.lock();
        try { return kiosksOnline; } finally { lock.unlock(); }
    }

    public int getStuckCustomers() {
        lock.lock();
        try { return stuckCustomers; } finally { lock.unlock(); }
    }

    public String getOwnerStatus() { return ownerStatus; }
}

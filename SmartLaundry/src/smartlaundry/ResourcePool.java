package smartlaundry;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe pool of identical machines (washers, dryers or kiosks).
 *
 * Two synchronization tools are combined, each doing one job:
 *
 *  1. Semaphore (counting, FAIR) - answers "may I have A machine?".
 *     It holds one permit per machine. When all machines are busy,
 *     acquire() blocks the customer thread. Fairness = true means waiting
 *     customers are served first-come-first-served (no starvation).
 *
 *  2. ReentrantLock - answers "WHICH machine do I get?".
 *     The semaphore only counts; it cannot hand out a machine number.
 *     The queue of free machine IDs is a normal ArrayDeque, which is NOT
 *     thread-safe, so every read/write of it happens inside lock()/unlock()
 *     (a critical section). This prevents two customers ever being given
 *     the same washer.
 *
 * One pool class replaces three copy-pasted blocks of washer/dryer/kiosk
 * code, so the locking logic is written (and tested) once.
 */
public class ResourcePool {

    private final ResourceType type;
    private final Semaphore permits;
    private final ReentrantLock lock = new ReentrantLock();

    // ---- Guarded by 'lock' ----
    private final Queue<Integer> freeIds = new ArrayDeque<Integer>();
    private final boolean[] busy;      // busy[id] == true while a customer holds machine id
    private final String[] holder;     // name of the thread using machine id (for the GUI)
    private int inUse = 0;             // machines currently in use
    private int peakInUse = 0;         // highest value inUse has reached (statistics)

    // Customers blocked in the semaphore queue. Atomic because it is updated
    // OUTSIDE the lock (a thread cannot hold the lock while it sleeps in acquire()).
    private final AtomicInteger waiting = new AtomicInteger(0);

    public ResourcePool(ResourceType type) {
        this.type = type;
        this.permits = new Semaphore(type.count(), true); // true = fair FIFO queue
        this.busy = new boolean[type.count() + 1];         // index 0 unused, machines are 1..n
        this.holder = new String[type.count() + 1];
        for (int id = 1; id <= type.count(); id++) {
            freeIds.add(id);
        }
    }

    /**
     * Blocks until a machine is free, then returns its ID.
     * The caller MUST call release(id) in a finally block.
     */
    public int acquire() throws InterruptedException {
        waiting.incrementAndGet();
        try {
            permits.acquire();          // blocks here while all machines are busy
        } finally {
            waiting.decrementAndGet();  // runs even if interrupted, so the count stays correct
        }

        lock.lock();
        try {
            // A permit guarantees at least one free ID exists, so poll() never returns null.
            int id = freeIds.poll();
            busy[id] = true;
            holder[id] = Thread.currentThread().getName();
            inUse++;
            if (inUse > peakInUse) {
                peakInUse = inUse;      // updated inside the same critical section -> always accurate
            }
            return id;
        } finally {
            lock.unlock();
        }
    }

    /** Returns machine 'id' to the pool and wakes the next waiting customer. */
    public void release(int id) {
        lock.lock();
        try {
            if (!busy[id]) {
                // Defensive check: releasing a machine twice would create an extra permit.
                throw new IllegalStateException(type.machineName(id) + " released twice");
            }
            busy[id] = false;
            holder[id] = null;
            inUse--;
            freeIds.add(id);
        } finally {
            lock.unlock();
        }
        // Release the permit only AFTER the ID is back in the queue, so the
        // woken customer is guaranteed to find a free ID.
        permits.release();
    }

    // ---- Read-only views (used by the GUI and statistics) ----

    public boolean isBusy(int id) {
        lock.lock();
        try { return busy[id]; } finally { lock.unlock(); }
    }

    /** Name of the customer thread using machine 'id', or null if it is free. */
    public String holderOf(int id) {
        lock.lock();
        try { return holder[id]; } finally { lock.unlock(); }
    }

    public int inUse() {
        lock.lock();
        try { return inUse; } finally { lock.unlock(); }
    }

    public int peakInUse() {
        lock.lock();
        try { return peakInUse; } finally { lock.unlock(); }
    }

    public int waiting() { return waiting.get(); }

    public ResourceType type() { return type; }
}

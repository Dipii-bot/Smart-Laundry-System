# Smart Laundry Facility Simulation

A concurrent Java simulation of a smart self-service laundromat, built for the
CT074-3-2 Concurrent Programming module at Asia Pacific University (APU).

Every customer is a separate thread. The simulation shows how many threads can
share a limited number of machines safely, without two customers ever using the
same machine, without corrupted statistics, and without anyone waiting forever.

## The facility

| Resource         | Count | Time per use |
|------------------|-------|--------------|
| Washing machines | 6     | 4–6 s        |
| Dryers           | 4     | 3–5 s        |
| Payment kiosks   | 2     | 1–2 s        |

- Customers arrive randomly every 0–3 seconds (up to 50 customers).
- The entrance is open for **60 seconds**; customers already inside still finish.
- Each customer washes, dries, pays, and leaves.
- A normal run takes about **65–75 seconds**.

## Features

- **Mutual exclusion** with a reusable `ResourcePool` (fair `Semaphore` + `ReentrantLock`)
- **Error handling**: 5% chance a washer fails mid-cycle (customer retries),
  5% chance a payment fails (retry after 2 s)
- **Statistics**: customers arrived and served, customers served in under
  60 seconds, average time per customer, maximum washers and dryers in use,
  failure counts
- **Thread-labelled output**: every log line shows the time and the thread that
  printed it
- **Bonus – congested scenario**: both payment kiosks are broken; when 30
  customers are stuck, the owner (a separate thread) is called to repair them
- **Bonus – GUI**: live Swing window showing machine status, queues and statistics

## Concurrency concepts used

| Java facility | Used for |
|---|---|
| `Semaphore` (fair) | Limits how many customers use machines at once; FIFO prevents starvation |
| `ReentrantLock` | Protects the list of free machine IDs (critical section) |
| `Lock` + `Condition` | Customers wait for kiosks to be repaired; owner calls `signalAll()` |
| `CountDownLatch` | Owner's "phone call"; main thread waits for all customers |
| `synchronized` | Keeps related statistics consistent; stops log lines mixing |
| `AtomicInteger` | Lock-free counters (arrivals, failures, queue sizes) |
| `volatile` | Visibility of values shared between threads |
| `try / finally` | Machines are always released, even after a failure |
| `SwingUtilities.invokeLater` + Swing `Timer` | Thread-safe GUI updates |

## Project structure

```
src/smartlaundry/
├── Main.java              # Entry point; starts threads, prints final statistics
├── Config.java            # All simulation settings
├── ResourceType.java      # Washer / Dryer / Kiosk types
├── ResourcePool.java      # Thread-safe machine pool (Semaphore + Lock)
├── LaundryFacility.java   # Wash, dry and pay stages with failures
├── Customer.java          # One thread per customer
├── EntranceGate.java      # Admits customers for 60 seconds
├── Statistics.java        # Shared statistics
├── SimulationLogger.java  # Timestamped, thread-labelled output
├── KioskOutage.java       # Congested scenario controller
├── ShopOwner.java         # Owner thread
└── LaundryGUI.java        # Swing visualisation
```

## How to run

Requires **Java 8 or newer**.

**IntelliJ IDEA:** open the project folder, set a JDK in
*File → Project Structure → Project*, then run `src/smartlaundry/Main.java`.
A dialog asks you to choose **Normal day** or **Congested**.

**Command line:**

```bash
javac -d out src/smartlaundry/*.java
java -cp out smartlaundry.Main              # choose scenario in a dialog
java -cp out smartlaundry.Main congested    # congested scenario directly
java -cp out smartlaundry.Main normal nogui # console only
```

## Sample results

| Scenario  | Customers served | Served under 60 s | Average time | Total run |
|-----------|------------------|-------------------|--------------|-----------|
| Normal    | 45 / 45          | 45 / 45           | ~11.6 s      | ~70 s     |
| Congested | 50 / 50          | 50 / 50           | ~41.4 s      | ~88 s     |

Results vary slightly between runs because arrival gaps, machine times and
failures are random.

## Author

Dipika Kumari Singh (NP070179)
Asia Pacific University of Technology & Innovation

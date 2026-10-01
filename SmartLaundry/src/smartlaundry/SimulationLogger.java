package smartlaundry;

/**
 * Central, thread-safe output for the whole simulation.
 *
 * Every line shows:
 *   [elapsed time] [name of the thread that printed it] message
 * so the video can prove that each thread only reports its OWN actions
 * (the assignment forbids one thread acting for another).
 *
 * The methods are 'static synchronized': only one thread at a time may be
 * inside any of them (they share the SimulationLogger.class monitor), so
 * lines from 50 customer threads never interleave half-way through.
 */
public final class SimulationLogger {

    // volatile: written once by the main/GUI thread, read by all others.
    // volatile guarantees every thread sees the latest value (visibility).
    private static volatile LaundryGUI gui;
    private static volatile long startTime = System.currentTimeMillis();

    private SimulationLogger() { }

    public static void setGUI(LaundryGUI laundryGUI) { gui = laundryGUI; }

    public static void markStart() { startTime = System.currentTimeMillis(); }

    public static double elapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }

    /** A customer thread reporting its own event. */
    public static synchronized void customer(int customerId, String message) {
        print(String.format("Customer-%02d %s", customerId, message));
    }

    /** Any other thread (Entrance, Owner, Main) reporting its own event. */
    public static synchronized void event(String message) {
        print(message);
    }

    public static synchronized void separator() {
        print("============================================================");
    }

    // Only called from the synchronized methods above, so it is already protected.
    private static void print(String message) {
        String line = String.format("[%6.1fs] [%-18s] %s",
                elapsedSeconds(), Thread.currentThread().getName(), message);
        System.out.println(line);
        LaundryGUI g = gui;           // read the volatile once into a local
        if (g != null) {
            g.addActivity(line);      // GUI hands this to the Swing thread itself
        }
    }
}

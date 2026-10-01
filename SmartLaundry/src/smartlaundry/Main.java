package smartlaundry;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Starts the Smart Laundry simulation.
 *
 * Run options (program arguments, all optional):
 *   normal     - normal day (default when no dialog is shown)
 *   congested  - BONUS: both payment kiosks broken, owner called at 30 in queue
 *   nogui      - console only
 * With no mode argument and a screen available, a dialog asks which scenario to run.
 *
 * Thread overview:
 *   Main        - builds everything, waits for all customers, prints statistics
 *   Entrance    - admits a new customer every 0-3 s
 *   Customer-NN - 50 threads, one per customer
 *   Owner       - only in the congested scenario
 *   AWT-Event.. - Swing's own thread, draws the GUI
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Main");
        List<String> options = Arrays.asList(args);

        final boolean useGui = !options.contains("nogui") && !GraphicsEnvironment.isHeadless();
        boolean congested;
        if (options.contains("congested")) {
            congested = true;
        } else if (options.contains("normal") || !useGui) {
            congested = false;
        } else {
            congested = askForScenario();
        }

        // ---------- Shared objects (one instance, used by all threads) ----------
        final ResourcePool washers = new ResourcePool(ResourceType.WASHER);
        final ResourcePool dryers  = new ResourcePool(ResourceType.DRYER);
        final ResourcePool kiosks  = new ResourcePool(ResourceType.PAYMENT_KIOSK);
        final Statistics statistics = new Statistics(washers, dryers);
        final KioskOutage outage = new KioskOutage(congested);
        final LaundryFacility facility = new LaundryFacility(washers, dryers, kiosks, statistics, outage);
        final CountDownLatch allCustomersDone = new CountDownLatch(Config.CUSTOMERS);

        // ---------- GUI (bonus) ----------
        final LaundryGUI[] guiHolder = new LaundryGUI[1];
        if (useGui) {
            // Swing components must be created on the Event Dispatch Thread.
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    guiHolder[0] = new LaundryGUI(washers, dryers, kiosks, statistics, outage);
                    guiHolder[0].setVisible(true);
                }
            });
            SimulationLogger.setGUI(guiHolder[0]);
        }

        // ---------- Start ----------
        SimulationLogger.markStart();
        SimulationLogger.separator();
        SimulationLogger.event("SMART LAUNDRY SIMULATION STARTED - "
                + (congested ? "CONGESTED SCENARIO" : "NORMAL SCENARIO"));
        SimulationLogger.event(Config.WASHERS + " washers, " + Config.DRYERS + " dryers, "
                + Config.PAYMENT_KIOSKS + " payment kiosks, up to " + Config.CUSTOMERS + " customers");
        SimulationLogger.event("Entrance is open for " + (Config.DOOR_OPEN_TIME / 1000) + " seconds");
        if (congested) {
            SimulationLogger.event("NOTICE: both payment kiosks are OUT OF ORDER today. Owner will be called at "
                    + Config.OWNER_CALL_THRESHOLD + " waiting customers.");
            new ShopOwner(outage).start();
        }
        SimulationLogger.separator();

        new EntranceGate(facility, statistics, allCustomersDone, outage).start();

        // Main blocks here (without busy-waiting) until every admitted customer has
        // finished. The entrance counts down for customers who never arrived.
        allCustomersDone.await();

        double totalSeconds = SimulationLogger.elapsedSeconds();
        SimulationLogger.event("ALL " + statistics.getCustomersArrived() + " ADMITTED CUSTOMERS COMPLETED");
        statistics.printReport(totalSeconds);

        if (guiHolder[0] != null) {
            guiHolder[0].simulationFinished(totalSeconds);
        }
    }

    /** Small dialog so the scenario can be chosen live during the video. */
    private static boolean askForScenario() throws Exception {
        final int[] choice = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                String[] buttons = {"Normal day", "Congested (kiosks broken)"};
                choice[0] = JOptionPane.showOptionDialog(null,
                        "Which scenario do you want to simulate?", "Smart Laundry",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, buttons, buttons[0]);
            }
        });
        if (choice[0] == JOptionPane.CLOSED_OPTION) {
            System.exit(0);
        }
        return choice[0] == 1;
    }
}

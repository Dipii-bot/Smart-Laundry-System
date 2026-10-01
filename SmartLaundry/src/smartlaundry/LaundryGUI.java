package smartlaundry;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * BONUS: GUI that visualises the laundromat live.
 *
 * Swing rule: Swing components are NOT thread-safe and may only be touched by
 * the Event Dispatch Thread (EDT). This class follows that rule in two ways:
 *
 *   1. Polling with javax.swing.Timer - the timer fires ON the EDT every
 *      200 ms and reads the shared state through the thread-safe getters of
 *      ResourcePool / Statistics / KioskOutage, then repaints.
 *   2. Customer threads never touch a component directly. addActivity() is
 *      called from worker threads but only queues work with
 *      SwingUtilities.invokeLater(), which the EDT runs later.
 */
public class LaundryGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color FREE_BG = new Color(220, 245, 225), FREE_FG = new Color(30, 120, 60);
    private static final Color BUSY_BG = new Color(255, 225, 200), BUSY_FG = new Color(170, 80, 20);
    private static final Color DOWN_BG = new Color(250, 210, 210), DOWN_FG = new Color(170, 30, 30);

    // Light blue theme for the LIVE STATUS section
    private static final Color STATUS_BG     = new Color(232, 243, 255);   // section background
    private static final Color STATUS_BOX    = new Color(207, 229, 255);   // each status box
    private static final Color STATUS_BORDER = new Color(120, 170, 230);   // box outline
    private static final Color STATUS_TEXT   = new Color(20, 60, 120);     // dark blue text

    private final ResourcePool washers, dryers, kiosks;
    private final Statistics statistics;
    private final KioskOutage outage;

    private final JLabel[] washerTiles = new JLabel[Config.WASHERS + 1];
    private final JLabel[] dryerTiles  = new JLabel[Config.DRYERS + 1];
    private final JLabel[] kioskTiles  = new JLabel[Config.PAYMENT_KIOSKS + 1];

    private JLabel clockLabel, arrivedLabel, inShopLabel, servedLabel;
    private JLabel washerQueueLabel, dryerQueueLabel, paymentQueueLabel, ownerLabel;
    private JLabel avgTimeLabel, under60Label, maxWasherLabel, maxDryerLabel, failuresLabel;
    private JTextArea activityArea;
    private final Timer refreshTimer;
    private volatile boolean finished = false;

    public LaundryGUI(ResourcePool washers, ResourcePool dryers, ResourcePool kiosks,
                      Statistics statistics, KioskOutage outage) {
        this.washers = washers;
        this.dryers = dryers;
        this.kiosks = kiosks;
        this.statistics = statistics;
        this.outage = outage;

        setTitle("Smart Laundry Simulation - "
                + (outage.isCongestedMode() ? "CONGESTED scenario" : "Normal scenario"));
        setSize(1000, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        buildLayout();

        refreshTimer = new Timer(200, e -> refresh());   // runs on the EDT
        refreshTimer.start();
    }

    // =========================================================
    // LAYOUT
    // =========================================================
    private void buildLayout() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ---- Header ----
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("SMART LAUNDRY SIMULATION", SwingConstants.LEFT);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        clockLabel = new JLabel("0.0 s", SwingConstants.RIGHT);
        clockLabel.setFont(new Font("Monospaced", Font.BOLD, 20));
        header.add(title, BorderLayout.WEST);
        header.add(clockLabel, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        // ---- Machines ----
        JPanel machines = new JPanel(new GridLayout(1, 3, 10, 10));
        machines.add(machinePanel("WASHERS (" + Config.WASHERS + ")", ResourceType.WASHER, washerTiles));
        machines.add(machinePanel("DRYERS (" + Config.DRYERS + ")", ResourceType.DRYER, dryerTiles));
        machines.add(machinePanel("PAYMENT KIOSKS (" + Config.PAYMENT_KIOSKS + ")",
                ResourceType.PAYMENT_KIOSK, kioskTiles));

        // ---- Live status (light blue) ----
        JPanel status = new JPanel(new GridLayout(2, 4, 8, 8));
        status.setBackground(STATUS_BG);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(STATUS_BORDER, 2),
                        "LIVE STATUS", 0, 0, new Font("Arial", Font.BOLD, 12), STATUS_TEXT),
                new EmptyBorder(4, 4, 4, 4)));
        arrivedLabel      = addStatus(status);
        inShopLabel       = addStatus(status);
        servedLabel       = addStatus(status);
        ownerLabel        = addStatus(status);
        washerQueueLabel  = addStatus(status);
        dryerQueueLabel   = addStatus(status);
        paymentQueueLabel = addStatus(status);
        addStatus(status).setText(outage.isCongestedMode() ? "Scenario: CONGESTED" : "Scenario: NORMAL");

        // ---- Statistics ----
        JPanel stats = new JPanel(new GridLayout(1, 5, 8, 8));
        stats.setBorder(BorderFactory.createTitledBorder("STATISTICS"));
        avgTimeLabel   = addInfo(stats);
        under60Label   = addInfo(stats);
        maxWasherLabel = addInfo(stats);
        maxDryerLabel  = addInfo(stats);
        failuresLabel  = addInfo(stats);

        JPanel centre = new JPanel(new BorderLayout(10, 10));
        centre.add(machines, BorderLayout.NORTH);
        centre.add(status, BorderLayout.CENTER);
        centre.add(stats, BorderLayout.SOUTH);
        main.add(centre, BorderLayout.CENTER);

        // ---- Activity log ----
        activityArea = new JTextArea();
        activityArea.setEditable(false);
        activityArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(activityArea);
        scroll.setBorder(BorderFactory.createTitledBorder("LIVE ACTIVITY"));
        scroll.setPreferredSize(new Dimension(900, 260));
        main.add(scroll, BorderLayout.SOUTH);

        add(main);
        refresh();
    }

    private JPanel machinePanel(String title, ResourceType type, JLabel[] tiles) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (int id = 1; id <= type.count(); id++) {
            JLabel tile = new JLabel("", SwingConstants.CENTER);
            tile.setOpaque(true);
            tile.setFont(new Font("Arial", Font.BOLD, 12));
            tile.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            tile.setPreferredSize(new Dimension(120, 44));
            tiles[id] = tile;
            panel.add(tile);
        }
        return panel;
    }

    private JLabel addInfo(JPanel parent) {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        label.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        label.setFont(new Font("Arial", Font.BOLD, 13));
        parent.add(label);
        return label;
    }

    /** A status box in the light-blue LIVE STATUS section. */
    private JLabel addStatus(JPanel parent) {
        JLabel label = addInfo(parent);
        label.setBackground(STATUS_BOX);
        label.setForeground(STATUS_TEXT);
        label.setBorder(BorderFactory.createLineBorder(STATUS_BORDER));
        return label;
    }

    // =========================================================
    // REFRESH (runs on the EDT via the Swing Timer)
    // =========================================================
    private void refresh() {
        if (!finished) {
            clockLabel.setText(String.format("%.1f s", SimulationLogger.elapsedSeconds()));
        }

        paintTiles(washers, washerTiles, false);
        paintTiles(dryers, dryerTiles, false);
        paintTiles(kiosks, kioskTiles, !outage.areKiosksOnline());

        int arrived = statistics.getCustomersArrived();
        int served = statistics.getCustomersServed();
        arrivedLabel.setText("Arrived: " + arrived + " / " + Config.CUSTOMERS);
        inShopLabel.setText("In shop now: " + (arrived - served));
        servedLabel.setText("Served: " + served + " / " + arrived);
        ownerLabel.setText("Owner: " + outage.getOwnerStatus());

        washerQueueLabel.setText("Washer queue: " + washers.waiting());
        dryerQueueLabel.setText("Dryer queue: " + dryers.waiting());
        int stuck = outage.getStuckCustomers();
        paymentQueueLabel.setText("Payment queue: " + (kiosks.waiting() + stuck));
        paymentQueueLabel.setBackground(stuck > 0 ? DOWN_BG : STATUS_BOX);   // red while stuck
        paymentQueueLabel.setForeground(stuck > 0 ? DOWN_FG : STATUS_TEXT);

        avgTimeLabel.setText(String.format("Avg time: %.2f s", statistics.getAverageTimeSeconds()));
        under60Label.setText("Served < 60 s: " + statistics.getServedUnderTarget());
        maxWasherLabel.setText("Max washers used: " + statistics.getMaxWashersInUse());
        maxDryerLabel.setText("Max dryers used: " + statistics.getMaxDryersInUse());
        failuresLabel.setText("Failures W/P: " + statistics.getWasherFailures()
                + " / " + statistics.getPaymentFailures());
    }

    private void paintTiles(ResourcePool pool, JLabel[] tiles, boolean outOfOrder) {
        String label = pool.type().label();
        for (int id = 1; id < tiles.length; id++) {
            String user = pool.holderOf(id);
            String name = label + " " + String.format("%02d", id);
            if (outOfOrder) {
                style(tiles[id], "<html><center>" + name + "<br>OUT OF ORDER</center></html>", DOWN_BG, DOWN_FG);
            } else if (user != null) {
                style(tiles[id], "<html><center>" + name + "<br>" + user + "</center></html>", BUSY_BG, BUSY_FG);
            } else {
                style(tiles[id], "<html><center>" + name + "<br>FREE</center></html>", FREE_BG, FREE_FG);
            }
        }
    }

    private static void style(JLabel tile, String text, Color bg, Color fg) {
        tile.setText(text);
        tile.setBackground(bg);
        tile.setForeground(fg);
    }

    // =========================================================
    // CALLED FROM WORKER THREADS
    // =========================================================

    /** Safe to call from any thread: the append is handed to the EDT. */
    public void addActivity(final String line) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                activityArea.append(line + "\n");
                if (activityArea.getLineCount() > 400) {        // keep memory bounded
                    try {
                        activityArea.getDocument().remove(0, activityArea.getLineEndOffset(99));
                    } catch (BadLocationException ignored) {
                        // cannot happen: we only remove lines that exist
                    }
                }
                activityArea.setCaretPosition(activityArea.getDocument().getLength());
            }
        });
    }

    /** Called by the Main thread when all customers have left. */
    public void simulationFinished(final double seconds) {
        finished = true;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refresh();
                clockLabel.setText(String.format("FINISHED %.1f s", seconds));
                JOptionPane.showMessageDialog(LaundryGUI.this, String.format(
                        "Simulation finished in %.1f s%n%n"
                                + "Customers arrived: %d / %d%n"
                                + "Customers served: %d / %d%n"
                                + "Served in under 60 s: %d / %d%n"
                                + "Average time per customer: %.2f s%n"
                                + "Max washers in use: %d / %d%n"
                                + "Max dryers in use: %d / %d%n"
                                + "Washer failures: %d   Payment failures: %d",
                        seconds, statistics.getCustomersArrived(), Config.CUSTOMERS,
                        statistics.getCustomersServed(), statistics.getCustomersArrived(),
                        statistics.getServedUnderTarget(), statistics.getCustomersServed(),
                        statistics.getAverageTimeSeconds(),
                        statistics.getMaxWashersInUse(), Config.WASHERS,
                        statistics.getMaxDryersInUse(), Config.DRYERS,
                        statistics.getWasherFailures(), statistics.getPaymentFailures()),
                        "Final statistics", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }
}

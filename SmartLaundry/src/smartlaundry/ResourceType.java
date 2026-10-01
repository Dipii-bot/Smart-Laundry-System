package smartlaundry;

/** The three kinds of shared resource in the laundry. Used to name pools and machines. */
public enum ResourceType {

    WASHER("Washer", Config.WASHERS),
    DRYER("Dryer", Config.DRYERS),
    PAYMENT_KIOSK("Kiosk", Config.PAYMENT_KIOSKS);

    private final String label;
    private final int count;

    ResourceType(String label, int count) {
        this.label = label;
        this.count = count;
    }

    public String label() { return label; }

    public int count() { return count; }

    /** e.g. WASHER.machineName(3) -> "Washer-03" */
    public String machineName(int id) {
        return label + "-" + String.format("%02d", id);
    }
}

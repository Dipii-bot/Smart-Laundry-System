package smartlaundry;

/**
 * All simulation constants in one place.
 *
 * Keeping the numbers here (instead of scattering "6", "4", "2" through the
 * code) means every class reads the same values and the case-study
 * requirements can be checked against a single file.
 *
 * All fields are static final, so they are immutable and safe to read from
 * any thread without synchronization.
 */
public final class Config {

    private Config() { }

    // ---- Facility size (case study) ----
    public static final int WASHERS        = 6;
    public static final int DRYERS         = 4;
    public static final int PAYMENT_KIOSKS = 2;
    public static final int CUSTOMERS      = 50;   // maximum; the door may close before all arrive

    // ---- Opening hours and service target ----
    public static final int DOOR_OPEN_TIME = 60000;  // entrance closes 60 s after the start
    public static final int SERVICE_TARGET = 60000;  // count customers whose time in shop is under 60 s

    // ---- Timings in milliseconds (basic requirements) ----
    public static final int ARRIVAL_GAP_MIN = 0,    ARRIVAL_GAP_MAX = 3000; // new customer every 0-3 s
    public static final int WASH_MIN        = 4000, WASH_MAX        = 6000; // 4-6 s
    public static final int DRY_MIN         = 3000, DRY_MAX         = 5000; // 3-5 s
    public static final int PAY_MIN         = 1000, PAY_MAX         = 2000; // 1-2 s

    // ---- Error handling (additional requirement 2) ----
    public static final double WASHER_FAIL_CHANCE = 0.05; // 5 % fails mid-cycle
    public static final double KIOSK_FAIL_CHANCE  = 0.05; // 5 % payment fails
    public static final int    WASH_RETRY_WAIT    = 1000; // assumption: customer waits 1 s before re-queueing
    public static final int    PAY_RETRY_WAIT     = 2000; // spec: retry after 2 s

    // ---- Congested scenario (bonus requirement) ----
    public static final int OWNER_CALL_THRESHOLD = 30;   // owner called when 30 are stuck at payment
    public static final int OWNER_TRAVEL_TIME    = 3000; // assumption: owner needs 3 s to arrive
    public static final int KIOSK_REPAIR_TIME    = 3000; // assumption: 3 s to reboot both kiosks
}

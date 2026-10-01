package smartlaundry;

/**
 * BONUS: the shop owner, only started in the congested scenario.
 * Runs as its own thread so the owner's actions are reported by the
 * "Owner" thread, never by a customer thread acting on the owner's behalf.
 */
public class ShopOwner extends Thread {

    private final KioskOutage outage;

    public ShopOwner(KioskOutage outage) {
        super("Owner");
        this.outage = outage;
    }

    @Override
    public void run() {
        try {
            outage.ownerRoutine();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

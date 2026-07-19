package com.philipxjm.neiratecalc.calc;

/** Result of running one recipe on one configured machine. */
public class RateResult {

    public final boolean ok;
    public final String error;
    /** Concurrent recipe copies actually running (EU-budget limited). */
    public final int parallels;
    public final int durationTicks;
    /** Total EU/t drawn by the machine including all parallels. */
    public final long eut;
    /** Crafts per minute for ONE machine, including parallels. */
    public final double craftsPerMin;

    private RateResult(boolean ok, String error, int parallels, int durationTicks, long eut, double craftsPerMin) {
        this.ok = ok;
        this.error = error;
        this.parallels = parallels;
        this.durationTicks = durationTicks;
        this.eut = eut;
        this.craftsPerMin = craftsPerMin;
    }

    public static RateResult of(int parallels, int durationTicks, long eut) {
        return new RateResult(true, null, parallels, durationTicks, eut, 1200.0 / durationTicks * parallels);
    }

    public static RateResult fail(String error) {
        return new RateResult(false, error, 0, 0, 0, 0);
    }
}

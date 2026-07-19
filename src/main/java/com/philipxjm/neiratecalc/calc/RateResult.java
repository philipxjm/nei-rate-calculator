package com.philipxjm.neiratecalc.calc;

public class RateResult {

    public final boolean ok;
    public final String error;

    public final int parallels;
    public final int durationTicks;

    public final long eut;

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

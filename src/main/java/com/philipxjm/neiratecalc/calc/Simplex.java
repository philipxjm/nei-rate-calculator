package com.philipxjm.neiratecalc.calc;

/**
 * Minimal two-phase simplex (dense tableau, Bland's rule) for the plan
 * solver. Models here are tiny, so clarity beats performance.
 */
public final class Simplex {

    public static final int OPTIMAL = 0;
    public static final int INFEASIBLE = 1;
    public static final int UNBOUNDED = 2;

    public static final int LE = -1;
    public static final int EQ = 0;
    public static final int GE = 1;

    private static final double EPS = 1e-9;
    private static final int MAX_ITERATIONS = 2000;

    public static class Result {

        public final int status;
        public final double[] x;

        Result(int status, double[] x) {
            this.status = status;
            this.x = x;
        }
    }

    private Simplex() {}

    /** Maximizes c.x subject to a[i].x (rel[i]) b[i], x >= 0. */
    public static Result maximize(double[] c, double[][] a, int[] rel, double[] b) {
        int m = b.length;
        int n = c.length;

        // Normalize to b >= 0.
        double[][] rows = new double[m][];
        double[] rhs = new double[m];
        int[] relation = new int[m];
        for (int i = 0; i < m; i++) {
            rows[i] = a[i].clone();
            rhs[i] = b[i];
            relation[i] = rel[i];
            if (rhs[i] < 0) {
                for (int j = 0; j < n; j++) {
                    rows[i][j] = -rows[i][j];
                }
                rhs[i] = -rhs[i];
                relation[i] = -relation[i];
            }
        }

        int slacks = 0;
        int artificials = 0;
        for (int i = 0; i < m; i++) {
            if (relation[i] != EQ) {
                slacks++;
            }
            if (relation[i] != LE) {
                artificials++;
            }
        }
        int cols = n + slacks + artificials;
        int artStart = n + slacks;

        double[][] t = new double[m][cols + 1];
        int[] basis = new int[m];
        int slackIdx = n;
        int artIdx = artStart;
        for (int i = 0; i < m; i++) {
            System.arraycopy(rows[i], 0, t[i], 0, n);
            t[i][cols] = rhs[i];
            if (relation[i] == LE) {
                t[i][slackIdx] = 1;
                basis[i] = slackIdx++;
            } else if (relation[i] == GE) {
                t[i][slackIdx] = -1;
                slackIdx++;
                t[i][artIdx] = 1;
                basis[i] = artIdx++;
            } else {
                t[i][artIdx] = 1;
                basis[i] = artIdx++;
            }
        }

        if (artificials > 0) {
            // Phase 1: maximize -(sum of artificials).
            double[] phase1 = new double[cols];
            for (int j = artStart; j < cols; j++) {
                phase1[j] = -1;
            }
            if (!pivotLoop(t, basis, phase1, cols, cols)) {
                return new Result(UNBOUNDED, null);
            }
            double artSum = 0;
            for (int i = 0; i < m; i++) {
                if (basis[i] >= artStart) {
                    artSum += t[i][cols];
                }
            }
            if (artSum > 1e-6) {
                return new Result(INFEASIBLE, null);
            }
        }

        // Phase 2: original objective; artificials barred from re-entering.
        double[] phase2 = new double[cols];
        System.arraycopy(c, 0, phase2, 0, n);
        if (!pivotLoop(t, basis, phase2, artStart, cols)) {
            return new Result(UNBOUNDED, null);
        }

        double[] x = new double[n];
        for (int i = 0; i < m; i++) {
            if (basis[i] < n) {
                x[basis[i]] = t[i][cols];
            }
        }
        return new Result(OPTIMAL, x);
    }

    /** Pivots until optimal; false on unbounded or iteration blow-up. */
    private static boolean pivotLoop(double[][] t, int[] basis, double[] costs, int enterLimit, int cols) {
        int m = t.length;
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // Reduced costs: r_j = c_j - c_B . column_j; Bland: first positive.
            int entering = -1;
            for (int j = 0; j < enterLimit; j++) {
                double r = costs[j];
                for (int i = 0; i < m; i++) {
                    r -= costs[basis[i]] * t[i][j];
                }
                if (r > EPS) {
                    entering = j;
                    break;
                }
            }
            if (entering < 0) {
                return true;
            }
            int leaving = -1;
            double bestRatio = 0;
            for (int i = 0; i < m; i++) {
                if (t[i][entering] > EPS) {
                    double ratio = t[i][cols] / t[i][entering];
                    if (leaving < 0 || ratio < bestRatio - EPS
                        || (ratio < bestRatio + EPS && basis[i] < basis[leaving])) {
                        leaving = i;
                        bestRatio = ratio;
                    }
                }
            }
            if (leaving < 0) {
                return false;
            }
            pivot(t, basis, leaving, entering, cols);
        }
        return false;
    }

    private static void pivot(double[][] t, int[] basis, int row, int col, int cols) {
        double p = t[row][col];
        for (int j = 0; j <= cols; j++) {
            t[row][j] /= p;
        }
        for (int i = 0; i < t.length; i++) {
            if (i == row) {
                continue;
            }
            double factor = t[i][col];
            if (factor != 0) {
                for (int j = 0; j <= cols; j++) {
                    t[i][j] -= factor * t[row][j];
                }
            }
        }
        basis[row] = col;
    }
}

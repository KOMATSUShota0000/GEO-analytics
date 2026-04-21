package com.geo.analytics.domain.phase10;

public final class PSquareQuantileUpdater {

    private PSquareQuantileUpdater() {
    }

    public static PSquareQuantileState update(PSquareQuantileState state, double x, double lambda) {
        long count = state.count() + 1L;
        double q1 = state.q1();
        double q2 = state.q2();
        double q3 = state.q3();
        double q4 = state.q4();
        double q5 = state.q5();
        double n1 = state.n1();
        double n2 = state.n2();
        double n3 = state.n3();
        double n4 = state.n4();
        double n5 = state.n5();
        double nPrime1 = state.nPrime1();
        double nPrime2 = state.nPrime2();
        double nPrime3 = state.nPrime3();
        double nPrime4 = state.nPrime4();
        double nPrime5 = state.nPrime5();

        if (state.count() < 5L) {
            if (count == 1L) {
                q1 = x;
                q2 = x;
                q3 = x;
                q4 = x;
                q5 = x;
            } else if (count == 2L) {
                double lo = StrictMath.min(q1, x);
                double hi = StrictMath.max(q1, x);
                q1 = lo;
                q2 = lo;
                q3 = (lo + hi) / 2.0d;
                q4 = hi;
                q5 = hi;
            } else if (count == 3L) {
                double t1 = StrictMath.min(StrictMath.min(q1, q5), x);
                double t3 = StrictMath.max(StrictMath.max(q1, q5), x);
                double t2 = q1 + q5 + x - t1 - t3;
                q1 = t1;
                q2 = (t1 + t2) / 2.0d;
                q3 = t2;
                q4 = (t2 + t3) / 2.0d;
                q5 = t3;
            } else if (count == 4L) {
                double a = q1;
                double b = q3;
                double c = q5;
                double d = x;
                double m0 = StrictMath.min(a, b);
                double m1 = StrictMath.max(a, b);
                double m2 = StrictMath.min(c, d);
                double m3 = StrictMath.max(c, d);
                double left = StrictMath.min(m0, m2);
                double right = StrictMath.max(m1, m3);
                double midLeft = StrictMath.max(m0, m2);
                double midRight = StrictMath.min(m1, m3);
                double s1 = StrictMath.min(midLeft, midRight);
                double s2 = StrictMath.max(midLeft, midRight);
                q1 = left;
                q2 = s1;
                q3 = s2;
                q4 = right;
                q5 = right;
            } else if (count == 5L) {
                double o0 = q1;
                double o1 = q2;
                double o2 = q3;
                double o3 = q4;
                double y = x;
                if (y <= o0) {
                    q1 = y;
                    q2 = o0;
                    q3 = o1;
                    q4 = o2;
                    q5 = o3;
                } else if (y <= o1) {
                    q1 = o0;
                    q2 = y;
                    q3 = o1;
                    q4 = o2;
                    q5 = o3;
                } else if (y <= o2) {
                    q1 = o0;
                    q2 = o1;
                    q3 = y;
                    q4 = o2;
                    q5 = o3;
                } else if (y <= o3) {
                    q1 = o0;
                    q2 = o1;
                    q3 = o2;
                    q4 = y;
                    q5 = o3;
                } else {
                    q1 = o0;
                    q2 = o1;
                    q3 = o2;
                    q4 = o3;
                    q5 = y;
                }
                n1 = 1.0d;
                n2 = 2.0d;
                n3 = 3.0d;
                n4 = 4.0d;
                n5 = 5.0d;
                nPrime1 = 1.0d;
                nPrime2 = 2.0d;
                nPrime3 = 3.0d;
                nPrime4 = 4.0d;
                nPrime5 = 5.0d;
            }
            return new PSquareQuantileState(count, q1, q2, q3, q4, q5, n1, n2, n3, n4, n5, nPrime1,
                    nPrime2, nPrime3, nPrime4, nPrime5);
        }

        int k;
        if (x < q1) {
            q1 = x;
            k = 1;
        } else if (x < q2) {
            k = 1;
        } else if (x < q3) {
            k = 2;
        } else if (x < q4) {
            k = 3;
        } else if (x < q5) {
            k = 4;
        } else {
            q5 = x;
            k = 4;
        }

        n1 = n1 * lambda;
        n2 = n2 * lambda;
        n3 = n3 * lambda;
        n4 = n4 * lambda;
        n5 = n5 * lambda;
        nPrime1 = nPrime1 * lambda;
        nPrime2 = nPrime2 * lambda;
        nPrime3 = nPrime3 * lambda;
        nPrime4 = nPrime4 * lambda;
        nPrime5 = nPrime5 * lambda;

        if (k <= 1) {
            n2 = n2 + 1.0d;
            n3 = n3 + 1.0d;
            n4 = n4 + 1.0d;
            n5 = n5 + 1.0d;
        } else if (k == 2) {
            n3 = n3 + 1.0d;
            n4 = n4 + 1.0d;
            n5 = n5 + 1.0d;
        } else if (k == 3) {
            n4 = n4 + 1.0d;
            n5 = n5 + 1.0d;
        } else {
            n5 = n5 + 1.0d;
        }

        nPrime2 = nPrime2 + 0.25d;
        nPrime3 = nPrime3 + 0.5d;
        nPrime4 = nPrime4 + 0.75d;
        nPrime5 = nPrime5 + 1.0d;

        double d2 = nPrime2 - n2;
        if (d2 >= 1.0d && n3 - n2 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q1, n1, q2, n2, q3, n3, 1);
            if (q1 < qP && qP < q3) {
                q2 = qP;
            } else {
                q2 = PSquarePredictor.linear(q2, n2, q3, n3, 1);
            }
            n2 = n2 + 1.0d;
        } else if (d2 <= -1.0d && n2 - n1 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q1, n1, q2, n2, q3, n3, -1);
            if (q1 < qP && qP < q3) {
                q2 = qP;
            } else {
                q2 = PSquarePredictor.linear(q2, n2, q1, n1, -1);
            }
            n2 = n2 - 1.0d;
        }

        double d3 = nPrime3 - n3;
        if (d3 >= 1.0d && n4 - n3 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q2, n2, q3, n3, q4, n4, 1);
            if (q2 < qP && qP < q4) {
                q3 = qP;
            } else {
                q3 = PSquarePredictor.linear(q3, n3, q4, n4, 1);
            }
            n3 = n3 + 1.0d;
        } else if (d3 <= -1.0d && n3 - n2 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q2, n2, q3, n3, q4, n4, -1);
            if (q2 < qP && qP < q4) {
                q3 = qP;
            } else {
                q3 = PSquarePredictor.linear(q3, n3, q2, n2, -1);
            }
            n3 = n3 - 1.0d;
        }

        double d4 = nPrime4 - n4;
        if (d4 >= 1.0d && n5 - n4 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q3, n3, q4, n4, q5, n5, 1);
            if (q3 < qP && qP < q5) {
                q4 = qP;
            } else {
                q4 = PSquarePredictor.linear(q4, n4, q5, n5, 1);
            }
            n4 = n4 + 1.0d;
        } else if (d4 <= -1.0d && n4 - n3 > 1.0d) {
            double qP = PSquarePredictor.parabolic(q3, n3, q4, n4, q5, n5, -1);
            if (q3 < qP && qP < q5) {
                q4 = qP;
            } else {
                q4 = PSquarePredictor.linear(q4, n4, q3, n3, -1);
            }
            n4 = n4 - 1.0d;
        }

        return new PSquareQuantileState(count, q1, q2, q3, q4, q5, n1, n2, n3, n4, n5, nPrime1,
                nPrime2, nPrime3, nPrime4, nPrime5);
    }
}

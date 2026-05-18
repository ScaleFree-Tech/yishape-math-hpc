package com.yishape.lab.math.hpc;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在原生库可用时对 {@link YishapeHpc} 做功能与数值正确性覆盖；无 DLL/SO 时整类跳过。
 */
class YishapeHpcFunctionalTest {

    private static final double TOL_LA = 1e-9;
    private static final double TOL_LP = 1e-5;
    private static final double TOL_SVD = 1e-7;

    private static String nativeSkipReason;

    @BeforeAll
    static void probeNative() {
        if (!YishapeHpc.isNativeRuntimeAvailable() || YishapeHpc.abiVersion() <= 0) {
            nativeSkipReason = "需要可用的 yishape_math_rust（ABI>0 且符号加载成功）";
        } else {
            nativeSkipReason = null;
        }
    }

    private static void assumeNative() {
        Assumptions.assumeTrue(nativeSkipReason == null, nativeSkipReason);
    }

    @Test
    void abiVersionMatchesRust() {
        assumeNative();
        assertTrue(YishapeHpc.abiVersion() >= 3, "期望 ABI ≥ 3（含 col-major dgemm 等）");
    }

    @Test
    void matMulRectangularVsNaive() {
        assumeNative();
        int m = 17;
        int n = 9;
        int p = 13;
        double[][] a = rndMatrix(m, n);
        double[][] b = rndMatrix(n, p);
        double[][] expect = naiveMatMul(a, b);
        double[][] got = YishapeHpc.tryMatMul(a, b);
        assertNotNull(got);
        assertClose2d(expect, got, TOL_LA);
    }

    @Test
    void matMulSkinnyAndSquare() {
        assumeNative();
        double[][] a = {{1, 2, 3}};
        double[][] b = {{10}, {20}, {30}};
        double[][] got = YishapeHpc.tryMatMul(a, b);
        assertNotNull(got);
        assertEquals(1, got.length);
        assertEquals(1, got[0].length);
        assertEquals(140.0, got[0][0], TOL_LA);

        double[][] id3 = eye(3);
        double[][] r = YishapeHpc.tryMatMul(id3, id3);
        assertNotNull(r);
        assertClose2d(id3, r, TOL_LA);
    }

    @Test
    void solveRandomInvertible5() {
        assumeNative();
        int n = 5;
        double[][] a = spdMatrix(n);
        double[] b = new double[n];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            b[i] = rng.nextDouble(-1, 1);
        }
        DenseSolveResult sol = YishapeHpc.solveSquare(a, b);
        assertTrue(sol.ok(), "status=" + sol.status());
        double[] ax = matVec(a, sol.x());
        assertClose1d(b, ax, TOL_LA * 100);
    }

    @Test
    void svdReconstructsRandomTall() {
        assumeNative();
        int m = 24;
        int n = 16;
        double[][] a = rndMatrix(m, n);
        assertSvdReconstructs(a, TOL_SVD);
    }

    @Test
    void svdReconstructsRandomWide() {
        assumeNative();
        int m = 7;
        int n = 21;
        double[][] a = rndMatrix(m, n);
        assertSvdReconstructs(a, TOL_SVD);
    }

    @Test
    void svdIdentitySingularValues() {
        assumeNative();
        int n = 6;
        double[][] id = eye(n);
        SvdResult r = YishapeHpc.svd(id);
        assertTrue(r.ok());
        for (int i = 0; i < n; i++) {
            assertEquals(1.0, r.singularValues()[i], 1e-8, "sigma " + i);
        }
        assertSigmaNonIncreasing(r.singularValues());
    }

    @Test
    void choleskyReconstructsSpd() {
        assumeNative();
        int n = 12;
        double[][] a = spdMatrix(n);
        CholeskyResult ch = YishapeHpc.cholesky(a);
        assertTrue(ch.ok());
        double[][] l = ch.lLower();
        double[][] llT = naiveMatMul(l, transpose(l));
        assertClose2d(a, llT, TOL_LA * 50);
    }

    @Test
    void choleskyNonSpdFails() {
        assumeNative();
        double[][] a = {{1, 2}, {2, 1}};
        CholeskyResult ch = YishapeHpc.cholesky(a);
        assertEquals(YishapeHpcStatus.NOT_POSITIVE_DEFINITE, ch.status());
    }

    @Test
    void eigenDiagonalAscendingAndResidual() {
        assumeNative();
        double[][] a = {
                {9, 0, 0},
                {0, 4, 0},
                {0, 0, 1}
        };
        SymmetricEigenResult ev = YishapeHpc.eigenSymmetric(a);
        assertTrue(ev.ok());
        assertArrayEquals(new double[] {1, 4, 9}, ev.eigenvaluesAscending(), 1e-10);
        assertEigenResidual(a, ev, 1e-9);
    }

    @Test
    void eigenRandomSymmetricResidual() {
        assumeNative();
        int n = 10;
        double[][] m = rndMatrix(n, n);
        double[][] a = symmetrize(m);
        SymmetricEigenResult ev = YishapeHpc.eigenSymmetric(a);
        assertTrue(ev.ok());
        for (int i = 1; i < n; i++) {
            assertTrue(ev.eigenvaluesAscending()[i] >= ev.eigenvaluesAscending()[i - 1] - 1e-12);
        }
        assertEigenResidual(a, ev, 1e-7);
        assertEigenvectorsOrthonormal(ev, 1e-6);
    }

    @Test
    void lpDenseWithEqualityOnly() {
        assumeNative();
        LpNonnegativeResult r = YishapeHpc.lpNonnegative(
                new double[] {1, 1},
                null,
                null,
                new double[][] {{1, 1}},
                new double[] {1.0});
        assertEquals(YishapeHpcStatus.OK, r.status());
        assertTrue(Math.abs(r.objective() - 1.0) < TOL_LP);
        assertTrue(r.x()[0] + r.x()[1] > 1.0 - TOL_LP && r.x()[0] + r.x()[1] < 1.0 + TOL_LP);
    }

    @Test
    void lpDenseInfeasible() {
        assumeNative();
        LpNonnegativeResult r = YishapeHpc.lpNonnegative(
                new double[] {1, 1},
                new double[][] {{1, 1}},
                new double[] {-1},
                null,
                null);
        assertEquals(YishapeHpcStatus.LP_INFEASIBLE, r.status());
    }

    @Test
    void lpDenseUnbounded() {
        assumeNative();
        LpNonnegativeResult r = YishapeHpc.lpNonnegative(
                new double[] {-1, 0},
                null,
                null,
                null,
                null);
        assertEquals(YishapeHpcStatus.LP_UNBOUNDED, r.status());
    }

    @Test
    void lpSparseMatchesDenseToy() {
        assumeNative();
        double[] c = {-1, -2};
        LpNonnegativeResult dense = YishapeHpc.lpNonnegative(
                c,
                new double[][] {{1, 1}},
                new double[] {1},
                null,
                null);
        assertEquals(YishapeHpcStatus.OK, dense.status());

        LpCsrMatrix csr = LpCsrMatrix.fromCoo(1, 2, new int[] {0, 0}, new int[] {0, 1}, new double[] {1, 1});
        LpNonnegativeResult sp = YishapeHpc.lpSparse(
                c,
                new double[] {0, 0},
                new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
                csr,
                new double[] {Double.NEGATIVE_INFINITY},
                new double[] {1});
        assertEquals(YishapeHpcStatus.OK, sp.status());
        assertTrue(Math.abs(dense.objective() - sp.objective()) < TOL_LP);
        assertClose1d(dense.x(), sp.x(), TOL_LP);
    }

    @Test
    void lpSparseTwoRowsBox() {
        assumeNative();
        int n = 3;
        int m = 4;
        int[] rows = {0, 0, 1, 2, 3, 3};
        int[] cols = {0, 1, 1, 2, 0, 2};
        double[] vals = {1, 1, 1, 1, 1, 1};
        LpCsrMatrix csr = LpCsrMatrix.fromCoo(m, n, rows, cols, vals);
        double[] c = {-1, -1, -0.5};
        double[] colLb = {0, 0, 0};
        double[] colUb = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] rowLb = {
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0.3,
                Double.NEGATIVE_INFINITY
        };
        double[] rowUb = {1.0, 0.7, Double.POSITIVE_INFINITY, 2.0};
        LpNonnegativeResult r = YishapeHpc.lpSparse(c, colLb, colUb, csr, rowLb, rowUb);
        assertEquals(YishapeHpcStatus.OK, r.status());
        double[] x = r.x();
        assertTrue(x[0] + x[1] <= 1.0 + TOL_LP);
        assertTrue(x[1] <= 0.7 + TOL_LP);
        assertTrue(x[2] >= 0.3 - TOL_LP);
        assertTrue(x[0] + x[2] <= 2.0 + TOL_LP);
    }

    private static void assertSvdReconstructs(double[][] a, double tol) {
        SvdResult r = YishapeHpc.svd(a);
        assertTrue(r.ok());
        assertSigmaNonIncreasing(r.singularValues());
        double[][] recon = reconstructFromSvd(r);
        assertClose2d(a, recon, tol);
    }

    private static double[][] reconstructFromSvd(SvdResult r) {
        double[][] u = r.u();
        double[] s = r.singularValues();
        double[][] vt = r.vt();
        int m = u.length;
        int k = s.length;
        int n = vt[0].length;
        double[][] out = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int p = 0; p < k; p++) {
                    sum += u[i][p] * s[p] * vt[p][j];
                }
                out[i][j] = sum;
            }
        }
        return out;
    }

    private static void assertSigmaNonIncreasing(double[] sigma) {
        for (int i = 1; i < sigma.length; i++) {
            assertTrue(sigma[i - 1] + 1e-14 >= sigma[i], "singular values must be non-increasing");
        }
    }

    private static void assertEigenResidual(double[][] a, SymmetricEigenResult ev, double tol) {
        int n = a.length;
        double[] w = ev.eigenvaluesAscending();
        double[][] z = ev.eigenvectors();
        for (int j = 0; j < n; j++) {
            double[] vcol = new double[n];
            for (int i = 0; i < n; i++) {
                vcol[i] = z[i][j];
            }
            double[] av = matVec(a, vcol);
            for (int i = 0; i < n; i++) {
                assertEquals(w[j] * vcol[i], av[i], Math.max(tol, 1e-8 * Math.abs(av[i])));
            }
        }
    }

    private static void assertEigenvectorsOrthonormal(SymmetricEigenResult ev, double tol) {
        int n = ev.eigenvectors()[0].length;
        double[][] z = ev.eigenvectors();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double dot = 0;
                for (int r = 0; r < z.length; r++) {
                    dot += z[r][i] * z[r][j];
                }
                double expect = i == j ? 1.0 : 0.0;
                assertEquals(expect, dot, tol);
            }
        }
    }

    private static double[][] naiveMatMul(double[][] a, double[][] b) {
        int m = a.length;
        int n = a[0].length;
        int p = b[0].length;
        double[][] c = new double[m][p];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) {
                double s = 0;
                for (int k = 0; k < n; k++) {
                    s += a[i][k] * b[k][j];
                }
                c[i][j] = s;
            }
        }
        return c;
    }

    private static double[][] transpose(double[][] a) {
        int m = a.length;
        int n = a[0].length;
        double[][] t = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                t[j][i] = a[i][j];
            }
        }
        return t;
    }

    private static double[] matVec(double[][] a, double[] x) {
        int m = a.length;
        int n = a[0].length;
        double[] y = new double[m];
        for (int i = 0; i < m; i++) {
            double s = 0;
            for (int j = 0; j < n; j++) {
                s += a[i][j] * x[j];
            }
            y[i] = s;
        }
        return y;
    }

    private static void assertClose1d(double[] a, double[] b, double tol) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], tol);
        }
    }

    private static void assertClose2d(double[][] a, double[][] b, double tol) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertArrayEquals(a[i], b[i], tol);
        }
    }

    private static double[][] rndMatrix(int m, int n) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double[][] a = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = rng.nextDouble(-1, 1);
            }
        }
        return a;
    }

    /** 随机 SPD：G G^T + n I */
    private static double[][] spdMatrix(int n) {
        double[][] g = rndMatrix(n, n);
        double[][] gt = transpose(g);
        double[][] gg = naiveMatMul(g, gt);
        for (int i = 0; i < n; i++) {
            gg[i][i] += n;
        }
        return gg;
    }

    private static double[][] symmetrize(double[][] m) {
        int n = m.length;
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = 0.5 * (m[i][j] + m[j][i]);
            }
        }
        return a;
    }

    private static double[][] eye(int n) {
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            a[i][i] = 1;
        }
        return a;
    }
}

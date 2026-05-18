package com.yishape.lab.math.hpc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 需要 {@code yishape_math_rust}：由 JAR 内嵌、{@code -Dyishape.hpc.library.path}，或系统 {@code java.library.path} 加载；无原生时本类测试跳过。
 */
class YishapeHpcSmokeTest {

    private static String nativeSkipReason;

    @BeforeAll
    static void probeNative() {
        if (YishapeHpc.abiVersion() > 0) {
            nativeSkipReason = null;
        } else {
            nativeSkipReason = "无法加载 yishape_math_rust（需 JAR 内嵌 DLL/SO、java.library.path 或 -Dyishape.hpc.library.path）";
        }
    }

    private static void assumeNative() {
        Assumptions.assumeTrue(nativeSkipReason == null, nativeSkipReason);
    }

    @Test
    void solve2x2MatchesExpected() {
        assumeNative();
        DenseSolveResult r = YishapeHpc.solveSquare(new double[][] {{2.0, 3.0}, {2.0, 4.0}}, new double[] {5.0, 6.0});
        assertTrue(r.ok());
        assertArrayEquals(new double[] {1.0, 1.0}, r.x(), 1e-12);
    }

    @Test
    void smallCholesky() {
        assumeNative();
        CholeskyResult r = YishapeHpc.cholesky(new double[][] {{4.0, 2.0}, {2.0, 3.0}});
        assertEquals(YishapeHpcStatus.OK, r.status());
        assertTrue(r.lLower()[0][0] > 0 && Math.abs(r.lLower()[0][0] * r.lLower()[0][0] - 4.0) < 1e-9);
    }

    @Test
    void lpFeasibleToy() {
        assumeNative();
        LpNonnegativeResult lp = YishapeHpc.lpNonnegative(
                new double[] {-1, -2},
                new double[][] {{1, 1}},
                new double[] {1.0},
                null,
                null);
        assertEquals(YishapeHpcStatus.OK, lp.status());
        assertTrue(lp.x()[0] >= -1e-6 && lp.x()[1] >= -1e-6);
        assertTrue(Math.abs(lp.x()[0] + lp.x()[1] - 1.0) < 1e-5);
    }

    @Test
    void lpSparseCsrMatchesToy() {
        assumeNative();
        LpCsrMatrix csr = LpCsrMatrix.fromCoo(1, 2, new int[] {0, 0}, new int[] {0, 1}, new double[] {1, 1});
        LpNonnegativeResult r = YishapeHpc.lpSparse(
                new double[] {-1, -2},
                new double[] {0, 0},
                new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
                csr,
                new double[] {Double.NEGATIVE_INFINITY},
                new double[] {1.0});
        assertEquals(YishapeHpcStatus.OK, r.status());
        assertTrue(r.x()[0] >= -1e-6 && r.x()[1] >= -1e-6);
        assertTrue(Math.abs(r.x()[0] + r.x()[1] - 1.0) < 1e-5);
    }
}

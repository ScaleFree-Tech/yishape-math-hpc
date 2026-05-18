package com.yishape.lab.math.hpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link LpCsrMatrix#fromCoo(int, int, int[], int[], double[])} 的排序、合并与 CSR 结构校验。 */
class LpCsrMatrixTest {

    @Test
    void fromCooBuildsSortedCsrRows() {
        LpCsrMatrix csr = LpCsrMatrix.fromCoo(
                2,
                3,
                new int[] {1, 0, 1, 0},
                new int[] {2, 0, 0, 1},
                new double[] {3.0, 1.0, 2.0, 4.0});
        assertEquals(2, csr.numConstraints());
        assertEquals(3, csr.numVariables());
        assertEquals(4, csr.nnz());
        assertArrayEquals(new int[] {0, 2, 4}, csr.rowPtr());
        assertArrayEquals(new int[] {0, 1, 0, 2}, csr.colInd());
        assertArrayEquals(new double[] {1.0, 4.0, 2.0, 3.0}, csr.values(), 1e-15);
    }

    @Test
    void fromCooMergesDuplicates() {
        LpCsrMatrix csr = LpCsrMatrix.fromCoo(1, 2, new int[] {0, 0}, new int[] {0, 0}, new double[] {1.0, 2.0});
        assertEquals(1, csr.nnz());
        assertArrayEquals(new int[] {0, 1}, csr.rowPtr());
        assertArrayEquals(new int[] {0}, csr.colInd());
        assertArrayEquals(new double[] {3.0}, csr.values(), 1e-15);
    }
}

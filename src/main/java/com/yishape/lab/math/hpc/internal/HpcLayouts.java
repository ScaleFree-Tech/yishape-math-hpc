package com.yishape.lab.math.hpc.internal;

/**
 * 行主序 {@code double[][]}（与 yishape-math 常用 {@code toDoubleArray()} 布局一致）与原生列主序打包数组互转。
 */
public final class HpcLayouts {

    private HpcLayouts() {
    }

    /**
     * 矩形矩阵：行主序 {@code a[i][j]} → 列主序打包 {@code dst[j * rows + i]}。
     *
     * @param dst 长度至少 {@code rows * cols}
     */
    public static void rowMajorMatrixToColMajorPacked(double[][] a, int rows, int cols, double[] dst) {
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                dst[j * rows + i] = a[i][j];
            }
        }
    }

    /**
     * 同 {@link #rowMajorMatrixToColMajorPacked(double[][], int, int, double[])}；
     * {@code m×n} 展平长度为 {@code m*n}。
     */
    public static void rowMajorRectToColMajorPacked(double[][] a, int m, int n, double[] dst) {
        rowMajorMatrixToColMajorPacked(a, m, n, dst);
    }

    /**
     * 列主序 packed → 行主序写回已有 {@code dst}（{@code dst.length} 至少 {@code rows}，每行长度 {@code cols}）。
     */
    public static void colMajorPackedToRowMajorMatrix(double[] src, int rows, int cols, double[][] dst) {
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                dst[i][j] = src[j * rows + i];
            }
        }
    }

    /**
     * 列主序 packed → 新分配的行主序 {@code rows×cols} 矩阵。
     */
    public static double[][] colMajorPackedToRowMajorNew(double[] src, int rows, int cols) {
        double[][] out = new double[rows][cols];
        colMajorPackedToRowMajorMatrix(src, rows, cols, out);
        return out;
    }
}

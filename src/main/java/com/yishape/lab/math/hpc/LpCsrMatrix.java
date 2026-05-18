package com.yishape.lab.math.hpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 线性约束矩阵 {@code A} 的 CSR 形式（{@code m} 约束 × {@code n} 变量）：适合直接交给 {@link YishapeHpc#lpSparse}。
 * 亦可由 COO 三元组通过 {@link #fromCoo} 构造（自动按行列排序并合并同位非零元）。
 */
public final class LpCsrMatrix {

    private final int m;
    private final int n;
    private final int nnz;
    private final int[] rowPtr;
    private final int[] colInd;
    private final double[] values;

    private LpCsrMatrix(int m, int n, int nnz, int[] rowPtr, int[] colInd, double[] values) {
        this.m = m;
        this.n = n;
        this.nnz = nnz;
        this.rowPtr = rowPtr;
        this.colInd = colInd;
        this.values = values;
    }

    /** 约束（行）数 {@code m}。 */
    public int numConstraints() {
        return m;
    }

    /** 变量（列）数 {@code n}。 */
    public int numVariables() {
        return n;
    }

    /** 非零元个数；满足 {@code rowPtr[m] == nnz}。 */
    public int nnz() {
        return nnz;
    }

    /**
     * CSR 行偏移，长度 {@code m+1}；第 {@code r} 行非零列为 {@code colInd[rowPtr[r] .. rowPtr[r+1])}。
     */
    public int[] rowPtr() {
        return rowPtr;
    }

    /** 列下标，长度 {@code nnz}，与 {@link #values()} 对齐。 */
    public int[] colInd() {
        return colInd;
    }

    /** 非零值，长度 {@code nnz}。 */
    public double[] values() {
        return values;
    }

    /**
     * 自 COO 构造 CSR：{@code rows[k],cols[k],vals[k]} 为一条非零。重复 {@code (row,col)} 会<strong>相加</strong>合并。
     */
    public static LpCsrMatrix fromCoo(int numConstraints, int numVariables, int[] rows, int[] cols, double[] vals) {
        if (numConstraints <= 0 || numVariables <= 0) {
            throw new IllegalArgumentException("numConstraints and numVariables must be positive");
        }
        int len = rows.length;
        if (cols.length != len || vals.length != len) {
            throw new IllegalArgumentException("rows, cols, vals same length");
        }
        Integer[] ix = new Integer[len];
        for (int i = 0; i < len; i++) {
            ix[i] = i;
        }
        Arrays.sort(ix, Comparator.comparingInt((Integer i) -> rows[i]).thenComparingInt(i -> cols[i]));

        ArrayList<Integer> mr = new ArrayList<>();
        ArrayList<Integer> mc = new ArrayList<>();
        ArrayList<Double> mv = new ArrayList<>();

        int pr = -1;
        int pc = -1;
        double acc = 0.0;
        boolean has = false;

        for (int k = 0; k < len; k++) {
            int t = ix[k];
            int r = rows[t];
            int c = cols[t];
            double v = vals[t];
            if (r < 0 || r >= numConstraints || c < 0 || c >= numVariables) {
                throw new IllegalArgumentException("COO index out of range: (" + r + "," + c + ")");
            }
            if (pr == r && pc == c) {
                acc += v;
            } else {
                if (has && Math.abs(acc) > 1e-18) {
                    mr.add(pr);
                    mc.add(pc);
                    mv.add(acc);
                }
                pr = r;
                pc = c;
                acc = v;
                has = true;
            }
        }
        if (has && Math.abs(acc) > 1e-18) {
            mr.add(pr);
            mc.add(pc);
            mv.add(acc);
        }

        int merged = mr.size();
        int[] rowPtr = new int[numConstraints + 1];
        int[] colInd = new int[merged];
        double[] aVals = new double[merged];

        Arrays.fill(rowPtr, 0);
        for (int i = 0; i < merged; i++) {
            rowPtr[mr.get(i) + 1]++;
        }
        for (int r = 1; r <= numConstraints; r++) {
            rowPtr[r] += rowPtr[r - 1];
        }
        int[] pos = Arrays.copyOf(rowPtr, numConstraints + 1);
        for (int i = 0; i < merged; i++) {
            int r = mr.get(i);
            int slot = pos[r]++;
            colInd[slot] = mc.get(i);
            aVals[slot] = mv.get(i);
        }
        int nnz = merged;
        return new LpCsrMatrix(numConstraints, numVariables, nnz, rowPtr, colInd, aVals);
    }
}

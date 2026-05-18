package com.yishape.lab.math.hpc;

/**
 * 稠密 LU 分解结果 {@code P A = L U}。
 *
 * @param status {@link YishapeHpcStatus}
 * @param l      单位下三角 {@code n×n} 行主序；失败时为 {@code null}
 * @param u      上三角 {@code n×n} 行主序；失败时为 {@code null}
 * @param p      行置换数组，长度 {@code n}；{@code p[i]} 为第 {@code i} 行对应原始行号；失败时为 {@code null}
 */
public record LuResult(int status, double[][] l, double[][] u, int[] p) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

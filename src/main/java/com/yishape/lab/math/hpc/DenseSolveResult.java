package com.yishape.lab.math.hpc;

/**
 * 稠密方阵求解 {@link YishapeHpc#solveSquare} 的结果。
 *
 * @param status 见 {@link YishapeHpcStatus}
 * @param x      成功时为解向量，否则为 {@code null}
 */
public record DenseSolveResult(int status, double[] x) {
    /** 是否 {@link YishapeHpcStatus#OK}。 */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

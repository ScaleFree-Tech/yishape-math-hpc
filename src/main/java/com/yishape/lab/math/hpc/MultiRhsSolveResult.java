package com.yishape.lab.math.hpc;

/**
 * 多右端项线性求解 {@code AX=B} 结果。
 *
 * @param status {@link YishapeHpcStatus}
 * @param x      解矩阵 {@code n×nrhs} 行主序；失败时为 {@code null}
 */
public record MultiRhsSolveResult(int status, double[][] x) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

package com.yishape.lab.math.hpc;

/**
 * L-BFGS / OWL-QN 优化结果。
 *
 * @param status {@link YishapeHpcStatus}
 * @param x      解向量；失败时为 {@code null}
 * @param fx     目标函数终值
 */
public record LbfgsResult(int status, double[] x, double fx) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

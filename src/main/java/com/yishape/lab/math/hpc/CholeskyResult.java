package com.yishape.lab.math.hpc;

/**
 * SPD Cholesky 下三角因子 {@code L}（行主序 {@code n×n}，上三角为 0）。
 *
 * @param status {@link YishapeHpcStatus}；{@link YishapeHpcStatus#OK} 时其余字段有效
 * @param lLower 下三角 {@code L}；失败时为 {@code null}
 */
public record CholeskyResult(int status, double[][] lLower) {
    /** 是否 {@link YishapeHpcStatus#OK}。 */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

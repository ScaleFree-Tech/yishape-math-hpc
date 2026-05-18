package com.yishape.lab.math.hpc;

/**
 * 稠密方阵求逆结果。
 *
 * @param status {@link YishapeHpcStatus}
 * @param inv    逆矩阵 {@code n×n} 行主序；失败时为 {@code null}
 */
public record InverseResult(int status, double[][] inv) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

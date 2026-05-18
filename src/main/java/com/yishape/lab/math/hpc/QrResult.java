package com.yishape.lab.math.hpc;

/**
 * 稠密 QR 分解结果 {@code A = Q R}。
 *
 * @param status {@link YishapeHpcStatus}
 * @param q      正交阵 {@code m×m} 行主序；失败时为 {@code null}
 * @param r      上梯形阵 {@code m×n} 行主序；失败时为 {@code null}
 */
public record QrResult(int status, double[][] q, double[][] r) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

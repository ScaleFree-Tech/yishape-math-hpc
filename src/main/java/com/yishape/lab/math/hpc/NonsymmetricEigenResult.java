package com.yishape.lab.math.hpc;

/**
 * 非对称实矩阵特征分解结果 {@code A = U S U^{-1}}。
 *
 * @param status        {@link YishapeHpcStatus}
 * @param eigenvaluesReal   特征值实部，长度 {@code n}；失败时为 {@code null}
 * @param eigenvaluesImag   特征值虚部，长度 {@code n}；失败时为 {@code null}
 * @param eigenvectorsReal  特征向量实部矩阵 {@code n×n} 行主序；失败时为 {@code null}
 * @param eigenvectorsImag  特征向量虚部矩阵 {@code n×n} 行主序；失败时为 {@code null}
 */
public record NonsymmetricEigenResult(
        int status,
        double[] eigenvaluesReal,
        double[] eigenvaluesImag,
        double[][] eigenvectorsReal,
        double[][] eigenvectorsImag) {
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

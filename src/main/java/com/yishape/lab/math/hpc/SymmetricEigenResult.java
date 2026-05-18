package com.yishape.lab.math.hpc;

/**
 * 实对称阵特征分解；特征值按 faer 为<strong>非降序</strong>。
 * {@code eigenvectors[i][j]} 为第 {@code j} 个特征向量的第 {@code i} 个分量（列为主特征向量，行主序二维数组）。
 *
 * @param status                 {@link YishapeHpcStatus}
 * @param eigenvaluesAscending   长度 {@code n}，非降序；失败时为 {@code null}
 * @param eigenvectors           行主序特征向量矩阵；失败时为 {@code null}
 */
public record SymmetricEigenResult(int status, double[] eigenvaluesAscending, double[][] eigenvectors) {
    /** 是否 {@link YishapeHpcStatus#OK}。 */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

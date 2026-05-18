package com.yishape.lab.math.hpc;

/**
 * 稠密 SVD，与 yishape-math 约定一致：{@code U} 瘦型行主序 {@code m×k}，{@code Vᵀ} 行主序 {@code n×n}，σ 长度 {@code k}。
 *
 * @param status         见 {@link YishapeHpcStatus}
 * @param u              行主序 {@code U}，{@code u[i][j]} 为元素 {@code (i,j)}；失败时为 {@code null}
 * @param singularValues 降序非负奇异值，长度 {@code k=min(m,n)}
 * @param vt             行主序 {@code Vᵀ}；失败时为 {@code null}
 */
public record SvdResult(int status, double[][] u, double[] singularValues, double[][] vt) {
    /** 是否 {@link YishapeHpcStatus#OK}。 */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

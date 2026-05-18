package com.yishape.lab.math.hpc;

/**
 * HiGHS 线性规划 / 混合整数规划求解结果（稠密非负形式或稀疏 CSR 等形式共用）。
 * 稠密入口：最小化 {@code cᵀx}，{@code A_ub x ≤ b_ub}、{@code A_eq x = b_eq}、{@code x ≥ 0}；
 * MILP 入口见 {@link YishapeHpc#lpMixedIntegerNonnegative}。
 * 稀疏入口：见 {@link YishapeHpc#lpSparse}。
 *
 * @param status {@link YishapeHpcStatus#OK}、{@link YishapeHpcStatus#LP_INFEASIBLE}、{@link YishapeHpcStatus#LP_UNBOUNDED} 或负错误码
 * @param objective 最优目标值（仅当 status 为 OK 时有效）
 * @param x 原始决策变量（长度 n；稀疏 LP 可为盒式界内最优解）
 */
public record LpNonnegativeResult(int status, double objective, double[] x) {
    /** 是否 {@link YishapeHpcStatus#OK}（LP 最优收敛）。 */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

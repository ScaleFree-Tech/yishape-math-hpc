package com.yishape.lab.math.hpc;

/**
 * 与 {@code yishape_math_rust} C ABI 约定一致的整数状态码；各 API 的 {@code status} 字段与此对齐。
 */
public final class YishapeHpcStatus {
    private YishapeHpcStatus() {
    }

    /** 成功。 */
    public static final int OK = 0;
    /** 线性规划不可行。 */
    public static final int LP_INFEASIBLE = 1;
    /** 线性规划无界。 */
    public static final int LP_UNBOUNDED = 2;

    /** 原生侧空指针（或等效 ABI 错误）。 */
    public static final int NULL_POINTER = -1;
    /** Java 侧维度/长度不匹配或非法参数。 */
    public static final int BAD_DIMENSION = -2;
    /** 分解类算法失败（如数值问题）。 */
    public static final int DECOMPOSITION_FAILED = -3;
    /** 求解器内部失败。 */
    public static final int SOLVER_FAILED = -4;
    /** Cholesky：矩阵非正定。 */
    public static final int NOT_POSITIVE_DEFINITE = -5;
    /** HNSW：无效句柄。 */
    public static final int HNSW_INVALID_HANDLE = -6;
    /** HNSW：重复点 ID。 */
    public static final int HNSW_DUPLICATE_ID = -7;
    /** HNSW：维度不匹配。 */
    public static final int HNSW_DIMENSION_MISMATCH = -8;
    /** HNSW：点未找到。 */
    public static final int HNSW_NOT_FOUND = -9;
    /** HNSW：原生 panic（已通过 catch_unwind 捕获，不会崩溃 JVM）。 */
    public static final int HNSW_PANIC = -10;
}

package com.yishape.lab.math.hpc.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.Linker.Option;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;

/**
 * 底层 FFM：仅列主序 / CSR 裸数组。原生库<strong>延迟加载</strong>，未加载时方法返回错误码而非在类初始化阶段抛错。
 * 业务侧请用 {@link com.yishape.lab.math.hpc.YishapeHpc}。
 * <p>返回值中的整型状态与 {@link com.yishape.lab.math.hpc.YishapeHpcStatus} 对齐。</p>
 */
public final class YishapeMathRust {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Object INIT_LOCK = new Object();

    /** 共享 Arena，避免每次 FFM 调用都创建/销毁 Arena。线程封闭，无锁竞争。 */
    private static final ThreadLocal<Arena> SHARED_ARENA = new ThreadLocal<>();
    private static final ThreadLocal<Long> ARENA_BYTES = new ThreadLocal<>();
    private static final long ARENA_RESET_THRESHOLD = 4 * 1024 * 1024; // 4 MB

    /** 0 = 未尝试, 1 = 成功, 2 = 失败 */
    private static volatile int initState;

    private static MethodHandle mhAbiVersion;
    private static MethodHandle mhTestHello;
    private static MethodHandle mhSolveLinearSystem;
    private static MethodHandle mhDenseSvdColMajor;
    private static MethodHandle mhMatmulColMajor;
    private static MethodHandle mhCholeskyLowerColMajor;
    private static MethodHandle mhSymmetricEigenColMajor;
    private static MethodHandle mhLpMinimizeNonneg;
    /** 可选；旧版原生库无此符号时为 null */
    private static MethodHandle mhMilpMinimizeNonneg;
    private static MethodHandle mhLpSparseCsrMinimize;
    // v0.5.0 新增
    private static MethodHandle mhSolveMultiRhs;
    private static MethodHandle mhDenseInverseColMajor;
    private static MethodHandle mhDenseQrColMajor;
    private static MethodHandle mhDenseLuColMajor;
    private static MethodHandle mhEigenNonsymmetricColMajor;
    // v0.6.0: L-BFGS / OWL-QN
    private static MethodHandle mhLbfgsMinimize;
    private static MethodHandle mhOwlqnMinimize;
    // v0.7.0: HNSW vector index
    private static MethodHandle mhHnswBuildF32;
    private static MethodHandle mhHnswAddF32;
    private static MethodHandle mhHnswSearchF32;
    private static MethodHandle mhHnswGetF32;
    private static MethodHandle mhHnswSize;
    private static MethodHandle mhHnswSetEf;
    private static MethodHandle mhHnswFree;

    private YishapeMathRust() {
    }

    /**
     * 是否已成功加载 {@code yishape_math_rust} 并完成符号绑定。
     */
    public static boolean isNativeAvailable() {
        ensureInit();
        return initState == 1;
    }

    private static void ensureInit() {
        if (initState != 0) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initState != 0) {
                return;
            }
            try {
                HpcNativeLoader.loadYishapeMathRust();
                SymbolLookup lookup = SymbolLookup.loaderLookup();
                mhAbiVersion = downcall(lookup, "yishape_hpc_abi_version", FunctionDescriptor.of(ValueLayout.JAVA_INT));
                mhTestHello = downcall(lookup, "test_hello", FunctionDescriptor.ofVoid());
                // 与 MemorySegment.ofArray 配合：须 critical(true) 才允许堆段作为 ADDRESS（见 downcallAllowHeap）
                mhSolveLinearSystem = downcallAllowHeap(lookup, "solve_linear_system",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhDenseSvdColMajor = downcallAllowHeap(lookup, "yishape_hpc_dense_svd_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhMatmulColMajor = downcallAllowHeap(lookup, "yishape_hpc_dgemm_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhCholeskyLowerColMajor = downcallAllowHeap(lookup, "yishape_hpc_dense_cholesky_lower_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhSymmetricEigenColMajor = downcallAllowHeap(lookup, "yishape_hpc_symmetric_eigen_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhLpMinimizeNonneg = downcall(lookup, "yishape_hpc_lp_minimize_nonneg",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhLpSparseCsrMinimize = downcall(lookup, "yishape_hpc_lp_sparse_csr_minimize",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhMilpMinimizeNonneg = downcallOptional(lookup, "yishape_hpc_milp_minimize_nonneg",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                // v0.5.0 新增（可选探测，旧版库缺少时回退）
                mhSolveMultiRhs = downcallOptionalAllowHeap(lookup, "yishape_hpc_solve_multi_rhs_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS));
                mhDenseInverseColMajor = downcallOptionalAllowHeap(lookup, "yishape_hpc_dense_inverse_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhDenseQrColMajor = downcallOptionalAllowHeap(lookup, "yishape_hpc_dense_qr_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhDenseLuColMajor = downcallOptionalAllowHeap(lookup, "yishape_hpc_dense_lu_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhEigenNonsymmetricColMajor = downcallOptionalAllowHeap(lookup, "yishape_hpc_eigen_nonsymmetric_col_major",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                // v0.6.0: L-BFGS / OWL-QN
                mhLbfgsMinimize = downcall(lookup, "yishape_hpc_lbfgs_minimize",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_DOUBLE,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhOwlqnMinimize = downcall(lookup, "yishape_hpc_owlqn_minimize",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_DOUBLE,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_DOUBLE,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                // v0.7.0: HNSW vector index（可选探测，旧版库缺少时回退）
                mhHnswBuildF32 = downcallOptional(lookup, "yishape_hpc_hnsw_build_f32",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS));
                mhHnswAddF32 = downcallOptional(lookup, "yishape_hpc_hnsw_add_f32",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT));
                mhHnswSearchF32 = downcallOptional(lookup, "yishape_hpc_hnsw_search_f32",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhHnswGetF32 = downcallOptional(lookup, "yishape_hpc_hnsw_get_f32",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT));
                mhHnswSize = downcallOptional(lookup, "yishape_hpc_hnsw_size",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                mhHnswSetEf = downcallOptional(lookup, "yishape_hpc_hnsw_set_ef",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT));
                mhHnswFree = downcallOptional(lookup, "yishape_hpc_hnsw_free",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS));
                initState = 1;
            } catch (Throwable t) {
                initState = 2;
            }
        }
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        MemorySegment symbol = lookup.findOrThrow(name);
        return LINKER.downcallHandle(symbol, fd);
    }

    /**
     * 允许将 {@link MemorySegment#ofArray(double[])} 等堆段作为指针参数传入原生；
     * 原生须在调用期间内完成读写、且不得再泄漏该指针（与 faer 同步计算一致）。
     */
    private static MethodHandle downcallAllowHeap(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        MemorySegment symbol = lookup.findOrThrow(name);
        return LINKER.downcallHandle(symbol, fd, Option.critical(true));
    }

    /** 无符号时返回 null，不抛错（便于 ABI 向后兼容） */
    private static MethodHandle downcallOptional(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        return lookup.find(name).map(se -> LINKER.downcallHandle(se, fd)).orElse(null);
    }

    /** 无符号时返回 null，允许堆段（便于 ABI 向后兼容 + heap pass-through） */
    private static MethodHandle downcallOptionalAllowHeap(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        return lookup.find(name).map(se -> LINKER.downcallHandle(se, fd, Option.critical(true))).orElse(null);
    }

    private static Arena sharedArena() {
        Arena a = SHARED_ARENA.get();
        if (a == null) {
            a = Arena.ofConfined();
            SHARED_ARENA.set(a);
            ARENA_BYTES.set(0L);
        }
        return a;
    }

    private static void trackAlloc(long bytes) {
        Long cur = ARENA_BYTES.get();
        if (cur == null) cur = 0L;
        long total = cur + bytes;
        if (total > ARENA_RESET_THRESHOLD) {
            Arena old = SHARED_ARENA.get();
            if (old != null) {
                old.close();
            }
            SHARED_ARENA.set(Arena.ofConfined());
            ARENA_BYTES.set(bytes);
        } else {
            ARENA_BYTES.set(total);
        }
    }

    /**
     * 查询原生侧 ABI 版本整数；库未加载或调用失败时返回 {@code -1}。
     */
    public static int abiVersion() {
        ensureInit();
        if (initState != 1) {
            return -1;
        }
        try {
            return (int) mhAbiVersion.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 调用原生测试符号；未加载时静默返回。
     *
     * @throws RuntimeException 向下传递 FFM 调用失败
     */
    public static void testHello() {
        ensureInit();
        if (initState != 1) {
            return;
        }
        try {
            mhTestHello.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 解 {@code A x = b}：{@code A} 为 {@code n×n} 列主序 packed，与 {@code b}、{@code x} 在 downcall 期间由堆段映射到原生。
     *
     * @return {@link com.yishape.lab.math.hpc.YishapeHpcStatus} 常量
     */
    public static int solveLinearSystem(double[] aColMajor, int n, double[] b, double[] x) {
        ensureInit();
        if (initState != 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n || b.length < n || x.length < n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment bSeg = MemorySegment.ofArray(b);
        MemorySegment xSeg = MemorySegment.ofArray(x);
        try {
            return (int) mhSolveLinearSystem.invokeExact(aSeg, (long) n, bSeg, xSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密矩阵乘 {@code C = A×B}，三者均为列主序 packed：{@code A} 为 {@code m×n}，{@code B} 为 {@code n×p}，{@code cColOut} 为 {@code m×p}。
     */
    public static int matmulColMajor(int m, int n, int p, double[] aCol, double[] bCol, double[] cColOut) {
        ensureInit();
        if (initState != 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (m <= 0 || n <= 0 || p <= 0
                || aCol.length < m * (long) n
                || bCol.length < n * (long) p
                || cColOut.length < m * (long) p) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        // 使用堆数组段避免 allocateFrom / copy：原生在同一线程内同步读写完，指针在 downcall 期间有效。
        MemorySegment aSeg = MemorySegment.ofArray(aCol);
        MemorySegment bSeg = MemorySegment.ofArray(bCol);
        MemorySegment cSeg = MemorySegment.ofArray(cColOut);
        try {
            return (int) mhMatmulColMajor.invokeExact(m, n, p, aSeg, bSeg, cSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密 SVD（列主序）。{@code k = min(m,n)}；{@code uOut} 长度 {@code m*k}，{@code sOut} 长度 {@code k}，{@code vtOut} 为完整 {@code Vᵀ}（{@code n×n}）列主序 packed。
     */
    public static int denseSvdColMajor(int m, int n, double[] aColMajor,
            double[] uOut, double[] sOut, double[] vtOut) {
        ensureInit();
        if (initState != 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        int k = Math.min(m, n);
        if (aColMajor.length < m * (long) n
                || uOut.length < m * (long) k
                || sOut.length < k
                || vtOut.length < n * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment uSeg = MemorySegment.ofArray(uOut);
        MemorySegment sSeg = MemorySegment.ofArray(sOut);
        MemorySegment vtSeg = MemorySegment.ofArray(vtOut);
        try {
            return (int) mhDenseSvdColMajor.invokeExact(m, n, aSeg, uSeg, sSeg, vtSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * SPD Cholesky：{@code A} 与输出的 {@code L} 均为 {@code n×n} 列主序下三角约定（上三角未定义/填 0）。
     */
    public static int choleskyLowerColMajor(int n, double[] aColMajor, double[] lOut) {
        ensureInit();
        if (initState != 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n || lOut.length < n * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment lSeg = MemorySegment.ofArray(lOut);
        try {
            return (int) mhCholeskyLowerColMajor.invokeExact(n, aSeg, lSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 实对称特征分解：{@code A} 列主序 {@code n×n}；特征值写入 {@code eigenvaluesOut}，特征向量矩阵列主序 {@code n×n} 写入 {@code eigenvectorsColMajorOut}。
     */
    public static int symmetricEigenColMajor(int n, double[] aColMajor, double[] eigenvaluesOut, double[] eigenvectorsColMajorOut) {
        ensureInit();
        if (initState != 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n
                || eigenvaluesOut.length < n
                || eigenvectorsColMajorOut.length < n * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment wSeg = MemorySegment.ofArray(eigenvaluesOut);
        MemorySegment uSeg = MemorySegment.ofArray(eigenvectorsColMajorOut);
        try {
            return (int) mhSymmetricEigenColMajor.invokeExact(n, aSeg, wSeg, uSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密非负 LP：目标与约束数组均为列主序行块（与 {@link com.yishape.lab.math.hpc.YishapeHpc#lpNonnegative} 一致）。
     */
    public static com.yishape.lab.math.hpc.LpNonnegativeResult lpMinimizeNonnegative(int n, int mLe, int mEq,
            double[] c,
            double[] aUbColMajor, double[] bUb,
            double[] aEqColMajor, double[] bEq) {
        ensureInit();
        if (initState != 1) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (c.length < n) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (mLe > 0 && (aUbColMajor.length < mLe * (long) n || bUb.length < mLe)) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (mEq > 0 && (aEqColMajor.length < mEq * (long) n || bEq.length < mEq)) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, c);
            MemorySegment aLeSeg = mLe == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aUbColMajor);
            MemorySegment bLeSeg = mLe == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, bUb);
            MemorySegment aEqSeg = mEq == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aEqColMajor);
            MemorySegment bEqSeg = mEq == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, bEq);
            MemorySegment xSeg = arena.allocate((long) n * ValueLayout.JAVA_DOUBLE.byteSize(), ValueLayout.JAVA_DOUBLE.byteSize());
            MemorySegment objSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
            int rc = (int) mhLpMinimizeNonneg.invokeExact(
                    n, mLe, mEq,
                    cSeg, aLeSeg, bLeSeg, aEqSeg, bEqSeg,
                    xSeg, objSeg);
            double obj = objSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            double[] x = new double[n];
            MemorySegment.copy(xSeg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(rc, obj, x);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 是否已绑定 {@code yishape_hpc_milp_minimize_nonneg}（旧版仅有 LP 的原生库为 false）。
     */
    public static boolean isMilpMinimizeNonnegativeAvailable() {
        ensureInit();
        return initState == 1 && mhMilpMinimizeNonneg != null;
    }

    /**
     * 稠密非负 MILP；{@code integrality} 与 {@link com.yishape.lab.math.hpc.YishapeHpc#lpMixedIntegerNonnegative} 一致。若当前原生库无 MILP 符号则返回空结果。
     */
    public static com.yishape.lab.math.hpc.LpNonnegativeResult lpMinimizeMixedIntegerNonnegative(
            int n, int mLe, int mEq,
            double[] c,
            double[] aUbColMajor, double[] bUb,
            double[] aEqColMajor, double[] bEq,
            int[] integrality) {
        ensureInit();
        if (initState != 1 || mhMilpMinimizeNonneg == null) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                    com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (integrality == null || integrality.length < n) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                    com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        for (int i = 0; i < n; i++) {
            int t = integrality[i];
            if (t < 0 || t > 2) {
                return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                        com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
            }
        }
        if (c.length < n) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                    com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (mLe > 0 && (aUbColMajor.length < mLe * (long) n || bUb.length < mLe)) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                    com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (mEq > 0 && (aEqColMajor.length < mEq * (long) n || bEq.length < mEq)) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(
                    com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, c);
            MemorySegment aLeSeg = mLe == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aUbColMajor);
            MemorySegment bLeSeg = mLe == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, bUb);
            MemorySegment aEqSeg = mEq == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aEqColMajor);
            MemorySegment bEqSeg = mEq == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_DOUBLE, bEq);
            MemorySegment intSeg = arena.allocateFrom(ValueLayout.JAVA_INT, Arrays.copyOf(integrality, n));
            MemorySegment xSeg = arena.allocate((long) n * ValueLayout.JAVA_DOUBLE.byteSize(), ValueLayout.JAVA_DOUBLE.byteSize());
            MemorySegment objSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
            int rc = (int) mhMilpMinimizeNonneg.invokeExact(
                    n, mLe, mEq,
                    cSeg, aLeSeg, bLeSeg, aEqSeg, bEqSeg, intSeg,
                    xSeg, objSeg);
            double obj = objSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            double[] x = new double[n];
            MemorySegment.copy(xSeg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(rc, obj, x);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稀疏 CSR LP；数组布局与 HiGHS 封装约定一致，{@code rowPtr} 长度 {@code m+1} 且 {@code rowPtr[0]=0}、{@code rowPtr[m]=nnz}。
     */
    public static com.yishape.lab.math.hpc.LpNonnegativeResult lpMinimizeSparseCsr(
            int n,
            int m,
            int nnz,
            double[] c,
            double[] colLb,
            double[] colUb,
            int[] rowPtr,
            int[] colInd,
            double[] aVals,
            double[] rowLb,
            double[] rowUb) {
        ensureInit();
        if (initState != 1) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (n <= 0 || m < 0 || nnz < 0 || c.length < n || colLb.length < n || colUb.length < n
                || rowPtr.length != m + 1 || colInd.length < nnz || aVals.length < nnz
                || rowLb.length < m || rowUb.length < m) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        if (rowPtr[0] != 0 || rowPtr[m] != nnz) {
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, c);
            MemorySegment colLbSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colLb);
            MemorySegment colUbSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, colUb);
            MemorySegment rowPtrSeg = arena.allocateFrom(ValueLayout.JAVA_INT, rowPtr);
            MemorySegment colIndSeg = arena.allocateFrom(ValueLayout.JAVA_INT, colInd);
            MemorySegment aSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, aVals);
            MemorySegment rowLbSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, rowLb);
            MemorySegment rowUbSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, rowUb);
            MemorySegment xSeg = arena.allocate((long) n * ValueLayout.JAVA_DOUBLE.byteSize(), ValueLayout.JAVA_DOUBLE.byteSize());
            MemorySegment objSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
            int rc = (int) mhLpSparseCsrMinimize.invokeExact(
                    n, m, nnz,
                    cSeg, colLbSeg, colUbSeg,
                    rowPtrSeg, colIndSeg, aSeg,
                    rowLbSeg, rowUbSeg,
                    xSeg, objSeg);
            double obj = objSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            double[] x = new double[n];
            MemorySegment.copy(xSeg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
            return new com.yishape.lab.math.hpc.LpNonnegativeResult(rc, obj, x);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // ===================== v0.5.0 新增 =====================

    /**
     * 多右端项求解 {@code AX=B}，{@code A} 列主序 {@code n×n}，{@code bColMajor} 列主序 {@code n×nrhs}，{@code xColMajorOut} 列主序 {@code n×nrhs}。
     */
    public static int solveMultiRhsColMajor(double[] aColMajor, int n, double[] bColMajor, int nrhs, double[] xColMajorOut) {
        ensureInit();
        if (initState != 1 || mhSolveMultiRhs == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n || bColMajor.length < n * (long) nrhs || xColMajorOut.length < n * (long) nrhs) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment bSeg = MemorySegment.ofArray(bColMajor);
        MemorySegment xSeg = MemorySegment.ofArray(xColMajorOut);
        try {
            return (int) mhSolveMultiRhs.invokeExact(aSeg, n, bSeg, nrhs, xSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密方阵求逆，{@code aColMajor} 与 {@code invOut} 均为 {@code n×n} 列主序。
     */
    public static int denseInverseColMajor(int n, double[] aColMajor, double[] invOut) {
        ensureInit();
        if (initState != 1 || mhDenseInverseColMajor == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n || invOut.length < n * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment invSeg = MemorySegment.ofArray(invOut);
        try {
            return (int) mhDenseInverseColMajor.invokeExact(n, aSeg, invSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密 QR 分解，{@code qOut} 列主序 {@code m×m}，{@code rOut} 列主序 {@code m×n}。
     */
    public static int denseQrColMajor(int m, int n, double[] aColMajor, double[] qOut, double[] rOut) {
        ensureInit();
        if (initState != 1 || mhDenseQrColMajor == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < m * (long) n || qOut.length < m * (long) m || rOut.length < m * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment qSeg = MemorySegment.ofArray(qOut);
        MemorySegment rSeg = MemorySegment.ofArray(rOut);
        try {
            return (int) mhDenseQrColMajor.invokeExact(m, n, aSeg, qSeg, rSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 稠密 LU 分解，{@code lOut} 与 {@code uOut} 列主序 {@code n×n}，{@code pOut} 长度 {@code n}。
     */
    public static int denseLuColMajor(int n, double[] aColMajor, double[] lOut, double[] uOut, int[] pOut) {
        ensureInit();
        if (initState != 1 || mhDenseLuColMajor == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n || lOut.length < n * (long) n || uOut.length < n * (long) n || pOut.length < n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment lSeg = MemorySegment.ofArray(lOut);
        MemorySegment uSeg = MemorySegment.ofArray(uOut);
        MemorySegment pSeg = MemorySegment.ofArray(pOut);
        try {
            return (int) mhDenseLuColMajor.invokeExact(n, aSeg, lSeg, uSeg, pSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 非对称特征分解，{@code aColMajor} {@code n×n}；{@code evalsReal/Imag} 长度 {@code n}；{@code evecsReal/Imag} 列主序 {@code n×n}。
     */
    public static int eigenNonsymmetricColMajor(int n, double[] aColMajor,
            double[] evalsRealOut, double[] evalsImagOut,
            double[] evecsRealOut, double[] evecsImagOut) {
        ensureInit();
        if (initState != 1 || mhEigenNonsymmetricColMajor == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (aColMajor.length < n * (long) n
                || evalsRealOut.length < n || evalsImagOut.length < n
                || evecsRealOut.length < n * (long) n || evecsImagOut.length < n * (long) n) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        MemorySegment aSeg = MemorySegment.ofArray(aColMajor);
        MemorySegment erSeg = MemorySegment.ofArray(evalsRealOut);
        MemorySegment eiSeg = MemorySegment.ofArray(evalsImagOut);
        MemorySegment vrSeg = MemorySegment.ofArray(evecsRealOut);
        MemorySegment viSeg = MemorySegment.ofArray(evecsImagOut);
        try {
            return (int) mhEigenNonsymmetricColMajor.invokeExact(n, aSeg, erSeg, eiSeg, vrSeg, viSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // ===================== v0.6.0: L-BFGS / OWL-QN =====================

    /**
     * L-BFGS 最小化（原始 FFM）。
     * {@code x} 为 in/out（初始点 → 解），{@code fxOut} 长度 1，返回目标终值。
     * {@code evaluateFn} 为 {@code double (*)(const double*, double*, int, void*)} 的 upcall stub。
     */
    public static int lbfgsMinimize(
            double[] x, int n, int m, double epsilon, int maxIterations,
            MemorySegment evaluateFn, MemorySegment userData, double[] fxOut) {
        ensureInit();
        if (initState != 1 || mhLbfgsMinimize == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (x.length < n || fxOut.length < 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, x);
            MemorySegment fxSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
            int rc = (int) mhLbfgsMinimize.invokeExact(
                    xSeg, n, m, epsilon, maxIterations,
                    evaluateFn, userData, fxSeg);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                MemorySegment.copy(xSeg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
                fxOut[0] = fxSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * OWL-QN 最小化（原始 FFM）。额外参数 {@code orthantwiseC} 为 L1 正则化权重。
     */
    public static int owlqnMinimize(
            double[] x, int n, int m, double epsilon, int maxIterations,
            double orthantwiseC,
            MemorySegment evaluateFn, MemorySegment userData, double[] fxOut) {
        ensureInit();
        if (initState != 1 || mhOwlqnMinimize == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (x.length < n || fxOut.length < 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xSeg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, x);
            MemorySegment fxSeg = arena.allocate(ValueLayout.JAVA_DOUBLE);
            int rc = (int) mhOwlqnMinimize.invokeExact(
                    xSeg, n, m, epsilon, maxIterations, orthantwiseC,
                    evaluateFn, userData, fxSeg);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                MemorySegment.copy(xSeg, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
                fxOut[0] = fxSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // ===================== v0.7.0: HNSW vector index =====================

    /**
     * 原生库是否导出 HNSW 符号。
     */
    public static boolean isHnswAvailable() {
        ensureInit();
        return initState == 1 && mhHnswBuildF32 != null;
    }

    /**
     * 从初始 f32 向量批次构建 HNSW 索引。
     *
     * @return 非负 opaque handle；失败时返回 {@code -1}
     */
    public static long hnswBuildF32(int dims, int n, float[] data, long[] ids, int metricType,
            int m, int efConstruction, int efSearch) {
        ensureInit();
        if (initState != 1 || mhHnswBuildF32 == null) {
            return -1;
        }
        if (dims <= 0 || n < 0 || data == null) {
            return -1;
        }
        if (n > 0 && (ids == null || data.length < n * (long) dims || ids.length < n)) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = n == 0 ? MemorySegment.NULL
                    : arena.allocateFrom(ValueLayout.JAVA_FLOAT, data);
            MemorySegment idsSeg = n == 0 ? MemorySegment.NULL
                    : arena.allocateFrom(ValueLayout.JAVA_LONG, ids);
            MemorySegment handleOutSeg = arena.allocate(ValueLayout.ADDRESS);
            int rc = (int) mhHnswBuildF32.invokeExact(dims, n, dataSeg, idsSeg, metricType,
                    m, efConstruction, efSearch, handleOutSeg);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                return handleOutSeg.get(ValueLayout.ADDRESS, 0).address();
            }
            return -1;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 向 HNSW 索引添加单条 f32 向量。
     */
    public static int hnswAddF32(long handle, long id, float[] data) {
        ensureInit();
        if (initState != 1 || mhHnswAddF32 == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0 || data == null || data.length == 0) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        Arena arena = sharedArena();
        long allocBytes = 0;
        try {
            MemorySegment dataSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, data);
            allocBytes += (long) data.length * ValueLayout.JAVA_FLOAT.byteSize();
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            return (int) mhHnswAddF32.invokeExact(handleSeg, id, dataSeg, data.length);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            trackAlloc(allocBytes);
        }
    }

    /**
     * HNSW k-NN 搜索；结果写入预分配数组。
     *
     * @return 状态码；{@code OK} 时 {@code idsOut[0..found)} 与 {@code distancesOut[0..found)} 有效
     */
    public static int hnswSearchF32(long handle, float[] query, int k,
            long[] idsOut, float[] distancesOut) {
        ensureInit();
        if (initState != 1 || mhHnswSearchF32 == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0 || query == null || query.length == 0 || k <= 0
                || idsOut == null || distancesOut == null
                || idsOut.length < k || distancesOut.length < k) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        Arena arena = sharedArena();
        long allocBytes = 0;
        try {
            MemorySegment querySeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, query);
            allocBytes += (long) query.length * ValueLayout.JAVA_FLOAT.byteSize();
            MemorySegment idsSeg = arena.allocate((long) k * ValueLayout.JAVA_LONG.byteSize(),
                    ValueLayout.JAVA_LONG.byteSize());
            allocBytes += (long) k * ValueLayout.JAVA_LONG.byteSize();
            MemorySegment distSeg = arena.allocate((long) k * ValueLayout.JAVA_FLOAT.byteSize(),
                    ValueLayout.JAVA_FLOAT.byteSize());
            allocBytes += (long) k * ValueLayout.JAVA_FLOAT.byteSize();
            MemorySegment foundSeg = arena.allocate(ValueLayout.JAVA_INT);
            allocBytes += ValueLayout.JAVA_INT.byteSize();
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            int rc = (int) mhHnswSearchF32.invokeExact(handleSeg, querySeg, query.length, k,
                    idsSeg, distSeg, foundSeg);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                int found = foundSeg.get(ValueLayout.JAVA_INT, 0);
                found = Math.min(found, k);
                MemorySegment.copy(idsSeg, ValueLayout.JAVA_LONG, 0, idsOut, 0, found);
                MemorySegment.copy(distSeg, ValueLayout.JAVA_FLOAT, 0, distancesOut, 0, found);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            trackAlloc(allocBytes);
        }
    }

    /**
     * 按 ID 取回存储的 f32 向量。
     */
    public static int hnswGetF32(long handle, long id, float[] dataOut) {
        ensureInit();
        if (initState != 1 || mhHnswGetF32 == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0 || dataOut == null || dataOut.length == 0) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        Arena arena = sharedArena();
        long allocBytes = 0;
        try {
            MemorySegment dataSeg = arena.allocate((long) dataOut.length * ValueLayout.JAVA_FLOAT.byteSize(),
                    ValueLayout.JAVA_FLOAT.byteSize());
            allocBytes += (long) dataOut.length * ValueLayout.JAVA_FLOAT.byteSize();
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            int rc = (int) mhHnswGetF32.invokeExact(handleSeg, id, dataSeg, dataOut.length);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                MemorySegment.copy(dataSeg, ValueLayout.JAVA_FLOAT, 0, dataOut, 0, dataOut.length);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            trackAlloc(allocBytes);
        }
    }

    /**
     * 返回索引中当前点数。
     */
    public static int hnswSize(long handle, int[] sizeOut) {
        ensureInit();
        if (initState != 1 || mhHnswSize == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0 || sizeOut == null || sizeOut.length < 1) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizeSeg = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            int rc = (int) mhHnswSize.invokeExact(handleSeg, sizeSeg);
            if (rc == com.yishape.lab.math.hpc.YishapeHpcStatus.OK) {
                sizeOut[0] = sizeSeg.get(ValueLayout.JAVA_INT, 0);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 设置 ef_search（越大召回率越高、延迟越大）。
     */
    public static int hnswSetEf(long handle, int ef) {
        ensureInit();
        if (initState != 1 || mhHnswSetEf == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0 || ef <= 0) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        try {
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            return (int) mhHnswSetEf.invokeExact(handleSeg, ef);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 释放 HNSW 索引及关联内存。
     */
    public static int hnswFree(long handle) {
        ensureInit();
        if (initState != 1 || mhHnswFree == null) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.BAD_DIMENSION;
        }
        if (handle <= 0) {
            return com.yishape.lab.math.hpc.YishapeHpcStatus.NULL_POINTER;
        }
        try {
            MemorySegment handleSeg = MemorySegment.ofAddress(handle);
            return (int) mhHnswFree.invokeExact(handleSeg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

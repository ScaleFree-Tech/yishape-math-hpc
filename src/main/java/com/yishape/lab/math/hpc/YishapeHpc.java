package com.yishape.lab.math.hpc;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.yishape.lab.math.hpc.internal.HpcLayouts;
import com.yishape.lab.math.hpc.internal.YishapeMathRust;

/**
 * yishape-math-hpc 对业务代码的<strong>唯一推荐入口</strong>：所有矩阵均为<strong>行主序</strong>
 * {@code double[][]}（与 yishape-math 一致），列主序打包与 CSR 细节由本类委托给内部实现。
 *
 * <h2>约定</h2>
 * <ul>
 *   <li>需要 <strong>Java 25+</strong>（FFM）；运行时常需 {@code --enable-native-access=ALL-UNNAMED} 或等价配置。</li>
 *   <li>线性代数结果的<strong>状态码</strong>见 {@link YishapeHpcStatus}；稠密 MatMul 使用 {@link #tryMatMul} 时在失败或原生不可用时返回 {@code null}。</li>
 *   <li>首次调用任一会触及原生的方法将触发加载（见 {@link #isNativeRuntimeAvailable()}）；具体线程安全以原生与 FFM 调用为准。</li>
 * </ul>
 *
 * <p>模块级总体说明见 {@code com.yishape.lab.math.hpc} 包的 {@code package-info.java}。</p>
 */
public final class YishapeHpc {

    private static final Linker LINKER = Linker.nativeLinker();

    /**
     * 供 L-BFGS / OWL-QN 求解器使用的评价函数接口。
     * 计算目标函数 f(x) 及其梯度 g(x)（原地写入 {@code g}），返回 f(x)。
     */
    @FunctionalInterface
    public interface LbfgsEvaluateFunction {
        double evaluate(double[] x, double[] g);
    }

    private static final ThreadLocal<LbfgsEvaluateFunction> lbfgsContext = new ThreadLocal<>();

    private static final MethodHandle MH_EVALUATE_CALLBACK;

    static {
        try {
            MH_EVALUATE_CALLBACK = MethodHandles.lookup().findStatic(
                    YishapeHpc.class, "evaluateCallback",
                    MethodType.methodType(
                            double.class,
                            MemorySegment.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static double evaluateCallback(MemorySegment xSeg, MemorySegment gSeg, int n, MemorySegment userData) {
        LbfgsEvaluateFunction fn = lbfgsContext.get();
        if (fn == null) return Double.NaN;
        long byteSize = n * (long) Double.BYTES;
        MemorySegment xSized = xSeg.reinterpret(byteSize);
        MemorySegment gSized = gSeg.reinterpret(byteSize);
        double[] x = new double[n];
        double[] g = new double[n];
        MemorySegment.copy(xSized, ValueLayout.JAVA_DOUBLE, 0, x, 0, n);
        double fx = fn.evaluate(x, g);
        MemorySegment.copy(g, 0, gSized, ValueLayout.JAVA_DOUBLE, 0, n);
        return fx;
    }

    private YishapeHpc() {
    }

    /**
     * ABI 版本号；未加载原生库时为 {@code -1}。
     */
    public static int abiVersion() {
        return YishapeMathRust.abiVersion();
    }

    /**
     * 是否已成功加载 {@code yishape_math_rust} 并完成符号解析。
     */
    public static boolean isNativeRuntimeAvailable() {
        return YishapeMathRust.isNativeAvailable();
    }

    /**
     * 调用原生侧连通性测试符号；若库未成功加载则静默返回（不抛异常）。
     */
    public static void testHello() {
        YishapeMathRust.testHello();
    }

    /**
     * 稠密 {@code C = A × B}（行主序）。原生不可用或计算失败时返回 {@code null}。
     * <p>数据路径：行主序 {@code double[][]} → 列主序打包（Java 循环）→ FFM 使用
     * {@link java.lang.foreign.MemorySegment#ofArray(double[])} 将打包数组映射到原生（避免 {@code allocateFrom}+返回 {@code copy} 的额外大块拷贝）→ 结果列主序再展回行主序 {@code double[][]}。</p>
     *
     * @return 新矩阵 {@code m×p}；维度非法、原生失败或未加载时 {@code null}
     */
    public static double[][] tryMatMul(double[][] a, double[][] b) {
        if (!YishapeMathRust.isNativeAvailable()) {
            return null;
        }
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return null;
        }
        int m = a.length;
        int n = a[0].length;
        int p = b[0].length;
        if (b.length != n) {
            return null;
        }
        if (!rowsConsistent(a, m, n) || !rowsConsistent(b, n, p)) {
            return null;
        }
        double[] aCol = new double[m * n];
        double[] bCol = new double[n * p];
        HpcLayouts.rowMajorRectToColMajorPacked(a, m, n, aCol);
        HpcLayouts.rowMajorRectToColMajorPacked(b, n, p, bCol);
        double[] cCol = new double[m * p];
        int rc = YishapeMathRust.matmulColMajor(m, n, p, aCol, bCol, cCol);
        if (rc != YishapeHpcStatus.OK) {
            return null;
        }
        return HpcLayouts.colMajorPackedToRowMajorNew(cCol, m, p);
    }

    /**
     * 解稠密方阵线性方程 {@code A x = b}（一般稠密，不假设对称）。
     *
     * @param a {@code n×n} 行主序系数阵
     * @param b 右端项，长度至少 {@code n}
     * @return {@link YishapeHpcStatus#OK} 时 {@link DenseSolveResult#x()} 为解；否则 {@code x} 为 {@code null}
     */
    public static DenseSolveResult solveSquare(double[][] a, double[] b) {
        if (!isSquare(a)) {
            return new DenseSolveResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        int n = a.length;
        if (b == null || b.length < n) {
            return new DenseSolveResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] x = new double[n];
        int rc = YishapeMathRust.solveLinearSystem(aCol, n, b, x);
        if (rc != YishapeHpcStatus.OK) {
            return new DenseSolveResult(rc, null);
        }
        return new DenseSolveResult(rc, x);
    }

    /**
     * 稠密 SVD：{@code A ≈ U Σ Vᵀ}，与 yishape-math 形状约定一致。
     *
     * @param a {@code m×n} 行主序
     * @return 成功时 {@link SvdResult#singularValues()} 长度 {@code min(m,n)} 且降序非负；失败时各数组字段多为 {@code null}
     */
    public static SvdResult svd(double[][] a) {
        if (a == null || a.length == 0) {
            return new SvdResult(YishapeHpcStatus.BAD_DIMENSION, null, null, null);
        }
        int m = a.length;
        int n = a[0] == null ? 0 : a[0].length;
        if (n == 0 || !rowsConsistent(a, m, n)) {
            return new SvdResult(YishapeHpcStatus.BAD_DIMENSION, null, null, null);
        }
        double[] aCol = new double[m * n];
        HpcLayouts.rowMajorRectToColMajorPacked(a, m, n, aCol);
        int k = Math.min(m, n);
        double[] uCol = new double[m * k];
        double[] s = new double[k];
        double[] vtCol = new double[n * n];
        int rc = YishapeMathRust.denseSvdColMajor(m, n, aCol, uCol, s, vtCol);
        if (rc != YishapeHpcStatus.OK) {
            return new SvdResult(rc, null, null, null);
        }
        double[][] u = HpcLayouts.colMajorPackedToRowMajorNew(uCol, m, k);
        double[][] vt = HpcLayouts.colMajorPackedToRowMajorNew(vtCol, n, n);
        return new SvdResult(rc, u, s, vt);
    }

    /**
     * SPD（对称正定）Cholesky：{@code A = L Lᵀ}，返回下三角 {@code L}（行主序 {@code n×n}，严格上三角为 0）。
     *
     * @param a {@code n×n} 行主序对称正定阵（未做 Java 侧全面校验时由原生返回 {@link YishapeHpcStatus#NOT_POSITIVE_DEFINITE} 等）
     */
    public static CholeskyResult cholesky(double[][] a) {
        if (!isSquare(a)) {
            return new CholeskyResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        int n = a.length;
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] lCol = new double[n * n];
        int rc = YishapeMathRust.choleskyLowerColMajor(n, aCol, lCol);
        if (rc != YishapeHpcStatus.OK) {
            return new CholeskyResult(rc, null);
        }
        double[][] l = HpcLayouts.colMajorPackedToRowMajorNew(lCol, n, n);
        return new CholeskyResult(rc, l);
    }

    /**
     * 实对称阵特征分解：{@code A Z = Z Λ}，列特征向量（存于行主序二维数组中）。
     * 特征值<strong>非降序</strong>；{@code eigenvectors[i][j]} 为第 {@code j} 个特征向量的第 {@code i} 分量。
     *
     * @param a {@code n×n} 行主序实对称阵（仅用上三角或全阵由原生约定；调用方应保证对称性）
     */
    public static SymmetricEigenResult eigenSymmetric(double[][] a) {
        if (!isSquare(a)) {
            return new SymmetricEigenResult(YishapeHpcStatus.BAD_DIMENSION, null, null);
        }
        int n = a.length;
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] w = new double[n];
        double[] zCol = new double[n * n];
        int rc = YishapeMathRust.symmetricEigenColMajor(n, aCol, w, zCol);
        if (rc != YishapeHpcStatus.OK) {
            return new SymmetricEigenResult(rc, null, null);
        }
        double[][] vecs = HpcLayouts.colMajorPackedToRowMajorNew(zCol, n, n);
        return new SymmetricEigenResult(rc, w, vecs);
    }

    /**
     * 当前加载的原生库是否导出 MILP（混合整数）入口。
     *
     * @return 仅当库已加载且存在 {@code yishape_hpc_milp_minimize_nonneg} 符号时为 true
     */
    public static boolean isMixedIntegerLpNativeAvailable() {
        return YishapeMathRust.isMilpMinimizeNonnegativeAvailable();
    }

    /**
     * 稠密非负 MILP：在 {@link #lpNonnegative} 相同约束下，按 {@code integrality[j]} 指定列类型：
     * {@code 0} 连续，{@code 1} 非负整数，{@code 2} 0-1。
     * 需 {@link #isMixedIntegerLpNativeAvailable()} 为 true；否则结果为维度/能力错误。
     *
     * @param c     目标 {@code cᵀx} 线性项，长度 {@code n}
     * @param aUb   {@code m_le × n} 行主序，{@code A_ub x ≤ b_ub}；无则 {@code null}
     * @param bUb   长度 {@code m_le}
     * @param aEq   {@code m_eq × n} 行主序，{@code A_eq x = b_eq}；无则 {@code null}
     * @param bEq   长度 {@code m_eq}
     * @param integrality 长度 ≥ {@code n}
     */
    public static LpNonnegativeResult lpMixedIntegerNonnegative(
            double[] c,
            double[][] aUb, double[] bUb,
            double[][] aEq, double[] bEq,
            int[] integrality) {
        if (c == null || integrality == null) {
            return badLp();
        }
        int n = c.length;
        if (n <= 0 || integrality.length < n) {
            return badLp();
        }
        int mLe = aUb == null ? 0 : aUb.length;
        int mEq = aEq == null ? 0 : aEq.length;
        if (mLe > 0 && (bUb == null || bUb.length < mLe)) {
            return badLp();
        }
        if (mEq > 0 && (bEq == null || bEq.length < mEq)) {
            return badLp();
        }
        if (mLe > 0 && !rowsConsistent(aUb, mLe, n)) {
            return badLp();
        }
        if (mEq > 0 && !rowsConsistent(aEq, mEq, n)) {
            return badLp();
        }
        double[] aUbCol = new double[Math.max(0, mLe * n)];
        if (mLe > 0) {
            HpcLayouts.rowMajorRectToColMajorPacked(aUb, mLe, n, aUbCol);
        }
        double[] aEqCol = new double[Math.max(0, mEq * n)];
        if (mEq > 0) {
            HpcLayouts.rowMajorRectToColMajorPacked(aEq, mEq, n, aEqCol);
        }
        double[] bLe = mLe == 0 ? new double[0] : bUb;
        double[] bE = mEq == 0 ? new double[0] : bEq;
        return YishapeMathRust.lpMinimizeMixedIntegerNonnegative(n, mLe, mEq, c, aUbCol, bLe, aEqCol, bE, integrality);
    }

    /**
     * 稠密非负 LP：最小化 {@code cᵀx}，s.t. {@code A_ub x ≤ b_ub}、{@code A_eq x = b_eq}、{@code x ≥ 0}。
     * {@code aUb} / {@code aEq} 为行主序；无某类约束时对应矩阵传 {@code null}，且 {@code b*} 长度视为 0。
     *
     * @param c   长度 {@code n}
     * @param aUb {@code m_le × n}；{@code null} 表示无不等式约束
     * @param bUb {@code null} 或与 {@code aUb} 行数一致
     * @param aEq {@code m_eq × n}；{@code null} 表示无等式约束
     * @param bEq {@code null} 或与 {@code aEq} 行数一致
     */
    public static LpNonnegativeResult lpNonnegative(
            double[] c,
            double[][] aUb, double[] bUb,
            double[][] aEq, double[] bEq) {
        if (c == null) {
            return badLp();
        }
        int n = c.length;
        if (n <= 0) {
            return badLp();
        }
        int mLe = aUb == null ? 0 : aUb.length;
        int mEq = aEq == null ? 0 : aEq.length;
        if (mLe > 0 && (bUb == null || bUb.length < mLe)) {
            return badLp();
        }
        if (mEq > 0 && (bEq == null || bEq.length < mEq)) {
            return badLp();
        }
        if (mLe > 0 && !rowsConsistent(aUb, mLe, n)) {
            return badLp();
        }
        if (mEq > 0 && !rowsConsistent(aEq, mEq, n)) {
            return badLp();
        }
        double[] aUbCol = new double[Math.max(0, mLe * n)];
        if (mLe > 0) {
            HpcLayouts.rowMajorRectToColMajorPacked(aUb, mLe, n, aUbCol);
        }
        double[] aEqCol = new double[Math.max(0, mEq * n)];
        if (mEq > 0) {
            HpcLayouts.rowMajorRectToColMajorPacked(aEq, mEq, n, aEqCol);
        }
        double[] bLe = mLe == 0 ? new double[0] : bUb;
        double[] bE = mEq == 0 ? new double[0] : bEq;
        return YishapeMathRust.lpMinimizeNonnegative(n, mLe, mEq, c, aUbCol, bLe, aEqCol, bE);
    }

    /**
     * 稀疏 CSR 形式 LP：约束矩阵为 {@code m×n}，非零结构由 {@link LpCsrMatrix} 描述；
     * 列界、行界与 HiGHS 语义一致（可为 {@code ±∞} 表示无界）。
     *
     * @param c     目标系数，长度 {@code n}
     * @param colLb 列下界，长度 {@code n}
     * @param colUb 列上界，长度 {@code n}
     * @param a     CSR；{@code rowPtr[0]=0}，{@code rowPtr[m]=nnz}
     * @param rowLb 约束侧下界，长度 {@code m}
     * @param rowUb 约束侧上界，长度 {@code m}
     */
    public static LpNonnegativeResult lpSparse(
            double[] c,
            double[] colLb, double[] colUb,
            LpCsrMatrix a,
            double[] rowLb, double[] rowUb) {
        if (a == null) {
            return badLp();
        }
        return YishapeMathRust.lpMinimizeSparseCsr(
                a.numVariables(),
                a.numConstraints(),
                a.nnz(),
                c,
                colLb,
                colUb,
                a.rowPtr(),
                a.colInd(),
                a.values(),
                rowLb,
                rowUb);
    }

    private static LpNonnegativeResult badLp() {
        return new LpNonnegativeResult(YishapeHpcStatus.BAD_DIMENSION, Double.NaN, new double[0]);
    }

    private static boolean isSquare(double[][] a) {
        if (a == null) {
            return false;
        }
        int n = a.length;
        if (n == 0) {
            return false;
        }
        for (double[] row : a) {
            if (row == null || row.length != n) {
                return false;
            }
        }
        return true;
    }

    private static boolean rowsConsistent(double[][] a, int m, int n) {
        for (int i = 0; i < m; i++) {
            if (a[i] == null || a[i].length != n) {
                return false;
            }
        }
        return true;
    }

    // ===================== v0.5.0 新增 =====================

    /**
     * 多右端项求解 {@code AX=B}，{@code A} 与 {@code B} 均为行主序；{@code B} 为 {@code n×k}。
     */
    public static MultiRhsSolveResult solveMultiRhs(double[][] a, double[][] b) {
        if (!isSquare(a) || b == null || b.length == 0) {
            return new MultiRhsSolveResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        int n = a.length;
        int nrhs = b[0].length;
        if (b.length != n || nrhs == 0) {
            return new MultiRhsSolveResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        for (int i = 0; i < n; i++) {
            if (b[i] == null || b[i].length != nrhs) {
                return new MultiRhsSolveResult(YishapeHpcStatus.BAD_DIMENSION, null);
            }
        }
        double[] aCol = new double[n * n];
        double[] bCol = new double[n * nrhs];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        HpcLayouts.rowMajorRectToColMajorPacked(b, n, nrhs, bCol);
        double[] xCol = new double[n * nrhs];
        int rc = YishapeMathRust.solveMultiRhsColMajor(aCol, n, bCol, nrhs, xCol);
        if (rc != YishapeHpcStatus.OK) {
            return new MultiRhsSolveResult(rc, null);
        }
        double[][] x = HpcLayouts.colMajorPackedToRowMajorNew(xCol, n, nrhs);
        return new MultiRhsSolveResult(rc, x);
    }

    /**
     * 稠密方阵求逆 {@code A^{-1}}，{@code A} 行主序 {@code n×n}。
     */
    public static InverseResult inverse(double[][] a) {
        if (!isSquare(a)) {
            return new InverseResult(YishapeHpcStatus.BAD_DIMENSION, null);
        }
        int n = a.length;
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] invCol = new double[n * n];
        int rc = YishapeMathRust.denseInverseColMajor(n, aCol, invCol);
        if (rc != YishapeHpcStatus.OK) {
            return new InverseResult(rc, null);
        }
        double[][] inv = HpcLayouts.colMajorPackedToRowMajorNew(invCol, n, n);
        return new InverseResult(rc, inv);
    }

    /**
     * 稠密 QR 分解 {@code A = Q R}，{@code A} 行主序 {@code m×n}。
     */
    public static QrResult qr(double[][] a) {
        if (a == null || a.length == 0) {
            return new QrResult(YishapeHpcStatus.BAD_DIMENSION, null, null);
        }
        int m = a.length;
        int n = a[0] == null ? 0 : a[0].length;
        if (n == 0 || !rowsConsistent(a, m, n)) {
            return new QrResult(YishapeHpcStatus.BAD_DIMENSION, null, null);
        }
        double[] aCol = new double[m * n];
        HpcLayouts.rowMajorRectToColMajorPacked(a, m, n, aCol);
        double[] qCol = new double[m * m];
        double[] rCol = new double[m * n];
        int rc = YishapeMathRust.denseQrColMajor(m, n, aCol, qCol, rCol);
        if (rc != YishapeHpcStatus.OK) {
            return new QrResult(rc, null, null);
        }
        double[][] q = HpcLayouts.colMajorPackedToRowMajorNew(qCol, m, m);
        double[][] r = HpcLayouts.colMajorPackedToRowMajorNew(rCol, m, n);
        return new QrResult(rc, q, r);
    }

    /**
     * 稠密 LU 分解 {@code P A = L U}，{@code A} 行主序 {@code n×n}。
     */
    public static LuResult lu(double[][] a) {
        if (!isSquare(a)) {
            return new LuResult(YishapeHpcStatus.BAD_DIMENSION, null, null, null);
        }
        int n = a.length;
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] lCol = new double[n * n];
        double[] uCol = new double[n * n];
        int[] p = new int[n];
        int rc = YishapeMathRust.denseLuColMajor(n, aCol, lCol, uCol, p);
        if (rc != YishapeHpcStatus.OK) {
            return new LuResult(rc, null, null, null);
        }
        double[][] l = HpcLayouts.colMajorPackedToRowMajorNew(lCol, n, n);
        double[][] u = HpcLayouts.colMajorPackedToRowMajorNew(uCol, n, n);
        return new LuResult(rc, l, u, p);
    }

    /**
     * 非对称实矩阵特征分解 {@code A = U S U^{-1}}；{@code A} 行主序 {@code n×n}。
     */
    public static NonsymmetricEigenResult eigenNonsymmetric(double[][] a) {
        if (!isSquare(a)) {
            return new NonsymmetricEigenResult(YishapeHpcStatus.BAD_DIMENSION, null, null, null, null);
        }
        int n = a.length;
        double[] aCol = new double[n * n];
        HpcLayouts.rowMajorMatrixToColMajorPacked(a, n, n, aCol);
        double[] evalsReal = new double[n];
        double[] evalsImag = new double[n];
        double[] evecsReal = new double[n * n];
        double[] evecsImag = new double[n * n];
        int rc = YishapeMathRust.eigenNonsymmetricColMajor(n, aCol, evalsReal, evalsImag, evecsReal, evecsImag);
        if (rc != YishapeHpcStatus.OK) {
            return new NonsymmetricEigenResult(rc, null, null, null, null);
        }
        double[][] evecsRealMat = HpcLayouts.colMajorPackedToRowMajorNew(evecsReal, n, n);
        double[][] evecsImagMat = HpcLayouts.colMajorPackedToRowMajorNew(evecsImag, n, n);
        return new NonsymmetricEigenResult(rc, evalsReal, evalsImag, evecsRealMat, evecsImagMat);
    }

    // ===================== v0.6.0: L-BFGS / OWL-QN =====================

    /**
     * L-BFGS 无约束最小化。
     * <p>通过 FFM upcall stub 在每次迭代时回调 {@code evaluate} 以计算目标值和梯度。
     * 调用线程安全（同步运行，使用 {@link ThreadLocal} 传递上下文）。</p>
     *
     * @param x             初始点（不会被修改；结果含于返回值）
     * @param m             L-BFGS 历史校正数（通常 3~20；推荐默认 10）
     * @param epsilon       收敛容差（梯度范数 &lt; epsilon * max(1, ||x||)）
     * @param maxIterations 最大迭代次数（0=无限制直至收敛）
     * @param evaluate      评价函数，接收 {@code (x, g)}，将梯度写入 {@code g} 并返回 f(x)
     * @return 优化结果
     */
    public static LbfgsResult lbfgsMinimize(
            double[] x, int m, double epsilon, int maxIterations,
            LbfgsEvaluateFunction evaluate) {
        if (!isNativeOk(x, evaluate)) {
            return new LbfgsResult(YishapeHpcStatus.BAD_DIMENSION, null, Double.NaN);
        }
        int n = x.length;
        double[] xCopy = x.clone();
        double[] fxBuf = new double[1];

        try (Arena arena = Arena.ofConfined()) {
            FunctionDescriptor upcallDesc = FunctionDescriptor.of(
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS);
            MemorySegment upcallStub = LINKER.upcallStub(MH_EVALUATE_CALLBACK, upcallDesc, arena);

            lbfgsContext.set(evaluate);
            try {
                int rc = YishapeMathRust.lbfgsMinimize(
                        xCopy, n, m, epsilon, maxIterations,
                        upcallStub, MemorySegment.NULL, fxBuf);
                return new LbfgsResult(
                        rc,
                        rc == YishapeHpcStatus.OK ? xCopy : null,
                        fxBuf[0]);
            } finally {
                lbfgsContext.remove();
            }
        }
    }

    /**
     * OWL-QN（L1 正则化 L-BFGS）最小化。
     * <p>与 {@link #lbfgsMinimize} 相同，但增加 L1 正则化权重 {@code orthantwiseC}。
     * 零权重时退化到普通 L-BFGS。</p>
     *
     * @param x             初始点
     * @param m             历史校正数
     * @param epsilon       收敛容差
     * @param maxIterations 最大迭代次数
     * @param orthantwiseC  L1 正则化权重（≥0；0 等价于无正则化）
     * @param evaluate      评价函数
     * @return 优化结果
     */
    public static LbfgsResult owlqnMinimize(
            double[] x, int m, double epsilon, int maxIterations,
            double orthantwiseC,
            LbfgsEvaluateFunction evaluate) {
        if (!isNativeOk(x, evaluate)) {
            return new LbfgsResult(YishapeHpcStatus.BAD_DIMENSION, null, Double.NaN);
        }
        int n = x.length;
        double[] xCopy = x.clone();
        double[] fxBuf = new double[1];

        try (Arena arena = Arena.ofConfined()) {
            FunctionDescriptor upcallDesc = FunctionDescriptor.of(
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS);
            MemorySegment upcallStub = LINKER.upcallStub(MH_EVALUATE_CALLBACK, upcallDesc, arena);

            lbfgsContext.set(evaluate);
            try {
                int rc = YishapeMathRust.owlqnMinimize(
                        xCopy, n, m, epsilon, maxIterations, orthantwiseC,
                        upcallStub, MemorySegment.NULL, fxBuf);
                return new LbfgsResult(
                        rc,
                        rc == YishapeHpcStatus.OK ? xCopy : null,
                        fxBuf[0]);
            } finally {
                lbfgsContext.remove();
            }
        }
    }

    private static boolean isNativeOk(double[] x, LbfgsEvaluateFunction evaluate) {
        return YishapeMathRust.isNativeAvailable() && x != null && x.length > 0 && evaluate != null;
    }

    // ===================== v0.7.0: HNSW vector index =====================

    /**
     * 当前加载的原生库是否导出 HNSW 符号。
     */
    public static boolean isHnswNativeAvailable() {
        return YishapeMathRust.isHnswAvailable();
    }

    /**
     * 从初始 f32 向量批次构建 HNSW 近似索引。
     *
     * @param dims           向量维度
     * @param data           全部向量数据，长度 {@code n × dims}，行优先（第 i 条为 {@code data[i*dims .. (i+1)*dims)}）
     * @param ids            每条向量的 u64 ID，长度 {@code n}
     * @param metricType     0 = L2（平方欧氏距离），1 = Cosine
     * @param m              HNSW 图参数 M（0 表示默认 16）
     * @param efConstruction 建图候选列表大小（0 表示默认 200）
     * @param efSearch       默认搜索候选列表大小（0 表示默认 50）
     * @return 非负 opaque handle；原生不可用或参数非法时返回 {@code -1}
     */
    public static long hnswBuildF32(int dims, float[] data, long[] ids, int metricType,
            int m, int efConstruction, int efSearch) {
        if (!YishapeMathRust.isHnswAvailable() || dims <= 0 || data == null || ids == null) {
            return -1;
        }
        int n = ids.length;
        if (data.length != n * (long) dims) {
            return -1;
        }
        return YishapeMathRust.hnswBuildF32(dims, n, data, ids, metricType, m, efConstruction, efSearch);
    }

    /**
     * 向现有 HNSW 索引插入单条 f32 向量。
     */
    public static int hnswAddF32(long handle, long id, float[] data) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0 || data == null || data.length == 0) {
            return YishapeHpcStatus.BAD_DIMENSION;
        }
        return YishapeMathRust.hnswAddF32(handle, id, data);
    }

    /**
     * HNSW k-NN 搜索。
     *
     * @param handle    由 {@link #hnswBuildF32} 返回的句柄
     * @param query     查询向量
     * @param k         返回近邻数上限
     * @return 搜索结果；失败时 {@link HnswSearchResult#ok()} 为 false
     */
    public static HnswSearchResult hnswSearchF32(long handle, float[] query, int k) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0 || query == null || query.length == 0 || k <= 0) {
            return new HnswSearchResult(YishapeHpcStatus.BAD_DIMENSION, new long[0], new float[0], 0);
        }
        long[] ids = new long[k];
        float[] distances = new float[k];
        int rc = YishapeMathRust.hnswSearchF32(handle, query, k, ids, distances);
        if (rc != YishapeHpcStatus.OK) {
            return new HnswSearchResult(rc, new long[0], new float[0], 0);
        }
        return new HnswSearchResult(rc, ids, distances, k);
    }

    /**
     * 按 ID 取回存储的 f32 向量。
     *
     * @return {@link YishapeHpcStatus#OK} 时 {@code dataOut} 已写入；否则为错误码
     */
    public static int hnswGetF32(long handle, long id, float[] dataOut) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0 || dataOut == null || dataOut.length == 0) {
            return YishapeHpcStatus.BAD_DIMENSION;
        }
        return YishapeMathRust.hnswGetF32(handle, id, dataOut);
    }

    /**
     * 返回索引当前包含的向量数。
     */
    public static int hnswSize(long handle) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0) {
            return -1;
        }
        int[] size = new int[1];
        int rc = YishapeMathRust.hnswSize(handle, size);
        return rc == YishapeHpcStatus.OK ? size[0] : -1;
    }

    /**
     * 设置 ef_search（影响召回率与延迟的权衡）。
     */
    public static int hnswSetEf(long handle, int ef) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0 || ef <= 0) {
            return YishapeHpcStatus.BAD_DIMENSION;
        }
        return YishapeMathRust.hnswSetEf(handle, ef);
    }

    /**
     * 释放 HNSW 索引及全部关联内存。
     */
    public static int hnswFree(long handle) {
        if (!YishapeMathRust.isHnswAvailable() || handle <= 0) {
            return YishapeHpcStatus.BAD_DIMENSION;
        }
        return YishapeMathRust.hnswFree(handle);
    }
}

package com.yishape.lab.math.hpc.bench;

import java.nio.file.Files;
import java.nio.file.Path;

import com.yishape.lab.math.hpc.CholeskyResult;
import com.yishape.lab.math.hpc.DenseSolveResult;
import com.yishape.lab.math.hpc.SvdResult;
import com.yishape.lab.math.hpc.SymmetricEigenResult;
import com.yishape.lab.math.hpc.YishapeHpc;

/**
 * 供 Python/NumPy 通过二进制文件对比正确性与耗时：{@code mvn test-compile exec:java}。
 * <pre>
 *   HpcNumpyInteropMain matmul  &lt;a.bin&gt; &lt;b.bin&gt; &lt;c.out.bin&gt; &lt;m&gt; &lt;n&gt; &lt;p&gt; &lt;warmup&gt; &lt;iters&gt;
 *   HpcNumpyInteropMain solve   &lt;a.bin&gt; &lt;b.bin&gt; &lt;x.out.bin&gt; &lt;n&gt; &lt;warmup&gt; &lt;iters&gt;
 *   HpcNumpyInteropMain cholesky &lt;a.bin&gt; &lt;l.out.bin&gt; &lt;n&gt; &lt;warmup&gt; &lt;iters&gt;
 *   HpcNumpyInteropMain svd     &lt;a.bin&gt; &lt;m&gt; &lt;n&gt; &lt;outDir&gt; &lt;warmup&gt; &lt;iters&gt;
 *   HpcNumpyInteropMain eigen   &lt;a.bin&gt; &lt;outDir&gt; &lt;n&gt; &lt;warmup&gt; &lt;iters&gt;
 * </pre>
 * 成功时在 stderr 打印一行 {@code MS_PER_CALL &lt;double&gt;}；失败 exit 2。
 */
public final class HpcNumpyInteropMain {

    public static void main(String[] args) throws Exception {
        if (!YishapeHpc.isNativeRuntimeAvailable()) {
            System.err.println("NATIVE_UNAVAILABLE");
            System.exit(2);
        }
        if (args.length < 1) {
            System.err.println("missing command");
            System.exit(2);
        }
        String cmd = args[0];
        switch (cmd) {
            case "matmul" -> runMatmul(args);
            case "solve" -> runSolve(args);
            case "cholesky" -> runCholesky(args);
            case "svd" -> runSvd(args);
            case "eigen" -> runEigen(args);
            default -> {
                System.err.println("unknown command: " + cmd);
                System.exit(2);
            }
        }
    }

    private static void runMatmul(String[] args) throws Exception {
        if (args.length != 9) {
            System.exit(2);
        }
        Path pa = Path.of(args[1]);
        Path pb = Path.of(args[2]);
        Path pc = Path.of(args[3]);
        int m = Integer.parseInt(args[4]);
        int n = Integer.parseInt(args[5]);
        int p = Integer.parseInt(args[6]);
        int warmup = Integer.parseInt(args[7]);
        int iters = Integer.parseInt(args[8]);
        double[][] a = BinaryRowMajor.readMatrix(pa, m, n);
        double[][] b = BinaryRowMajor.readMatrix(pb, n, p);
        for (int i = 0; i < warmup; i++) {
            double[][] c = YishapeHpc.tryMatMul(a, b);
            if (c == null) {
                System.err.println("MATMUL_FAILED");
                System.exit(2);
            }
        }
        long t0 = System.nanoTime();
        double[][] cout = null;
        for (int i = 0; i < iters; i++) {
            cout = YishapeHpc.tryMatMul(a, b);
            if (cout == null) {
                System.err.println("MATMUL_FAILED");
                System.exit(2);
            }
        }
        long t1 = System.nanoTime();
        BinaryRowMajor.writeMatrix(pc, cout);
        double ms = (t1 - t0) / 1_000_000.0 / Math.max(1, iters);
        System.err.printf("MS_PER_CALL %.9f%n", ms);
    }

    private static void runSolve(String[] args) throws Exception {
        if (args.length != 7) {
            System.exit(2);
        }
        Path pa = Path.of(args[1]);
        Path pb = Path.of(args[2]);
        Path px = Path.of(args[3]);
        int n = Integer.parseInt(args[4]);
        int warmup = Integer.parseInt(args[5]);
        int iters = Integer.parseInt(args[6]);
        double[][] a = BinaryRowMajor.readMatrix(pa, n, n);
        double[] b = BinaryRowMajor.readVector(pb, n);
        for (int i = 0; i < warmup; i++) {
            DenseSolveResult r = YishapeHpc.solveSquare(a, b);
            if (!r.ok() || r.x() == null) {
                System.err.println("SOLVE_FAILED");
                System.exit(2);
            }
        }
        long t0 = System.nanoTime();
        DenseSolveResult last = null;
        for (int i = 0; i < iters; i++) {
            last = YishapeHpc.solveSquare(a, b);
            if (!last.ok()) {
                System.err.println("SOLVE_FAILED");
                System.exit(2);
            }
        }
        long t1 = System.nanoTime();
        BinaryRowMajor.writeVector(px, last.x());
        double ms = (t1 - t0) / 1_000_000.0 / Math.max(1, iters);
        System.err.printf("MS_PER_CALL %.9f%n", ms);
    }

    private static void runCholesky(String[] args) throws Exception {
        if (args.length != 6) {
            System.exit(2);
        }
        Path pa = Path.of(args[1]);
        Path pl = Path.of(args[2]);
        int n = Integer.parseInt(args[3]);
        int warmup = Integer.parseInt(args[4]);
        int iters = Integer.parseInt(args[5]);
        double[][] a = BinaryRowMajor.readMatrix(pa, n, n);
        for (int i = 0; i < warmup; i++) {
            CholeskyResult r = YishapeHpc.cholesky(a);
            if (!r.ok()) {
                System.err.println("CHOL_FAILED");
                System.exit(2);
            }
        }
        long t0 = System.nanoTime();
        CholeskyResult last = null;
        for (int i = 0; i < iters; i++) {
            last = YishapeHpc.cholesky(a);
            if (!last.ok()) {
                System.err.println("CHOL_FAILED");
                System.exit(2);
            }
        }
        long t1 = System.nanoTime();
        BinaryRowMajor.writeMatrix(pl, last.lLower());
        double ms = (t1 - t0) / 1_000_000.0 / Math.max(1, iters);
        System.err.printf("MS_PER_CALL %.9f%n", ms);
    }

    private static void runSvd(String[] args) throws Exception {
        if (args.length != 7) {
            System.exit(2);
        }
        Path pa = Path.of(args[1]);
        int m = Integer.parseInt(args[2]);
        int n = Integer.parseInt(args[3]);
        Path dir = Path.of(args[4]);
        int warmup = Integer.parseInt(args[5]);
        int iters = Integer.parseInt(args[6]);
        Files.createDirectories(dir);
        double[][] a = BinaryRowMajor.readMatrix(pa, m, n);
        for (int i = 0; i < warmup; i++) {
            SvdResult r = YishapeHpc.svd(a);
            if (!r.ok()) {
                System.err.println("SVD_FAILED");
                System.exit(2);
            }
        }
        long t0 = System.nanoTime();
        SvdResult last = null;
        for (int i = 0; i < iters; i++) {
            last = YishapeHpc.svd(a);
            if (!last.ok()) {
                System.err.println("SVD_FAILED");
                System.exit(2);
            }
        }
        long t1 = System.nanoTime();
        BinaryRowMajor.writeMatrix(dir.resolve("u.bin"), last.u());
        BinaryRowMajor.writeVector(dir.resolve("s.bin"), last.singularValues());
        BinaryRowMajor.writeMatrix(dir.resolve("vt.bin"), last.vt());
        double ms = (t1 - t0) / 1_000_000.0 / Math.max(1, iters);
        System.err.printf("MS_PER_CALL %.9f%n", ms);
    }

    private static void runEigen(String[] args) throws Exception {
        if (args.length != 6) {
            System.exit(2);
        }
        Path pa = Path.of(args[1]);
        Path dir = Path.of(args[2]);
        int n = Integer.parseInt(args[3]);
        int warmup = Integer.parseInt(args[4]);
        int iters = Integer.parseInt(args[5]);
        Files.createDirectories(dir);
        double[][] a = BinaryRowMajor.readMatrix(pa, n, n);
        for (int i = 0; i < warmup; i++) {
            SymmetricEigenResult ev = YishapeHpc.eigenSymmetric(a);
            if (!ev.ok()) {
                System.err.println("EIGEN_FAILED");
                System.exit(2);
            }
        }
        long t0 = System.nanoTime();
        SymmetricEigenResult last = null;
        for (int i = 0; i < iters; i++) {
            last = YishapeHpc.eigenSymmetric(a);
            if (!last.ok()) {
                System.err.println("EIGEN_FAILED");
                System.exit(2);
            }
        }
        long t1 = System.nanoTime();
        BinaryRowMajor.writeVector(dir.resolve("w.bin"), last.eigenvaluesAscending());
        BinaryRowMajor.writeMatrix(dir.resolve("z.bin"), last.eigenvectors());
        double ms = (t1 - t0) / 1_000_000.0 / Math.max(1, iters);
        System.err.printf("MS_PER_CALL %.9f%n", ms);
    }
}

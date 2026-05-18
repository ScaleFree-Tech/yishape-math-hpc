#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
与 NumPy 对比 yishape-math-hpc（Java 原生）的正确性与耗时。

前置：已在本模块执行 mvn test-compile（target/classes 含 DLL），本机可用 java 与 numpy。

用法（在 yishape-math-hpc 根目录或任意目录）：
  python benchmarks/compare_with_numpy.py
  python benchmarks/compare_with_numpy.py --quick   # 只跑小矩阵正确性

说明：Rust 侧应使用 faer::linalg::matmul::matmul（分块+SIMD+可选并行），
与 NumPy 所链接的 OpenBLAS/MKL 属同类“工程级 GEMM”比拼；若仍明显慢，
请检查线程环境（见脚本末尾分析）及是否重新编译了最新 DLL。
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _java_cmd(repo: Path) -> list[str]:
    java_home = os.environ.get("JAVA_HOME", "").strip()
    java = shutil.which("java")
    if java_home:
        cand = Path(java_home) / "bin" / ("java.exe" if sys.platform == "win32" else "java")
        if cand.is_file():
            java = str(cand)
    if not java:
        sys.exit("未找到 java，请设置 JAVA_HOME 或 PATH")
    classes = repo / "target" / "classes"
    test_classes = repo / "target" / "test-classes"
    if not (classes / "com").is_dir() or not (test_classes / "com").is_dir():
        sys.exit(
            f"缺少编译输出，请在 {repo} 执行: mvn test-compile\n"
            "（需带原生库时勿加 -DskipNativeBuild，除非已手动放 DLL）"
        )
    sep = os.pathsep
    cp = f"{classes}{sep}{test_classes}"
    return [
        java,
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        cp,
        "com.yishape.lab.math.hpc.bench.HpcNumpyInteropMain",
    ]


def _run_java(java_base: list[str], args: list[str]) -> float:
    r = subprocess.run(
        java_base + args,
        capture_output=True,
        text=True,
        check=False,
    )
    if r.returncode != 0:
        sys.stderr.write(r.stdout or "")
        sys.stderr.write(r.stderr or "")
        sys.exit(f"Java 退出码 {r.returncode}")
    ms = None
    for line in (r.stderr or "").splitlines():
        line = line.strip()
        if line.startswith("MS_PER_CALL"):
            ms = float(line.split()[1])
            break
    if ms is None:
        sys.exit("Java 未输出 MS_PER_CALL: " + (r.stderr or ""))
    return ms


def _write_row_major_bin(path: Path, arr) -> None:
    import numpy as np

    a = np.ascontiguousarray(arr, dtype=np.float64)
    a.tofile(path)


def _read_row_major_bin(path: Path, rows: int, cols: int):
    import numpy as np

    raw = np.fromfile(path, dtype=np.float64)
    return raw.reshape(rows, cols, order="C")


def _read_vector_bin(path: Path, n: int):
    import numpy as np

    return np.fromfile(path, dtype=np.float64).reshape(n)


def _print_runtime_env() -> None:
    import numpy as np

    print("\n== 运行环境（可复现对比时请记录）==")
    keys = (
        "OMP_NUM_THREADS",
        "OPENBLAS_NUM_THREADS",
        "MKL_NUM_THREADS",
        "NUMEXPR_NUM_THREADS",
        "VECLIB_MAXIMUM_THREADS",
    )
    for k in keys:
        v = os.environ.get(k, "")
        if v:
            print(f"  {k}={v}")
    try:
        b = getattr(np.__config__, "blas_opt_info", None)
        if b:
            print(f"  numpy.__config__.blas_opt_info: {str(b)[:300]}")
    except Exception:
        pass


def print_analysis() -> None:
    text = r"""
== 分析与反思（为何会出现「比 BLAS 慢很多」的错觉）==

1) 测到的是什么
   - NumPy 的 @ / dot 几乎总是走高度优化的第三方 BLAS（OpenBLAS、MKL、Apple Accelerate 等），
     多线程 + 体系结构专用核函数，这是「工业界金标准」基线。
   - 本项目的 Java 路径是：Java 堆上 double[][] → 列主序打包 → FFM 调 Rust。
     FFM 单次调用的固定开销在 O(n^3) 面前通常可忽略；真正决定速度的是 Rust 里调的**具体函数**。

2) 之前的误区（已修复方向）
   - 若 Rust 里 dgemm 用手写三层 for 循环而没有调用 faer 的 matmul，则相当于「教学版 O(n^3)」
     对垒 OpenBLAS，慢一个数量级以上是**预期结果**，与「faer 厉不厉害」无关——压根没用上 faer 的核。
   - 正确做法：`MatRef::from_column_major_slice` 零拷贝视图 + `faer::linalg::matmul::matmul`
     + `faer::get_global_parallelism()`，才与「严肃 GEMM 实现」同一量级。

3) faer / HiGHS 与 BLAS 的关系
   - faer 的稠密乘法是自研分块内核，论文级实现，总体目标是与 BLAS 同量级竞争；在特定形状、线程数、CPU 上
     可能互有胜负，并非「理论上一定更快」。
   - HiGHS 是线性规划求解器，与 `numpy.linalg` 的 LU/SVD 不是同一类问题；LP 对比应对 scipy.optimize.linprog 等。

4) 线性方程组 solve 仍可能慢于 NumPy
   - `numpy.linalg.solve` 通常走 LAPACK **多线程** dgesv；faer 的 LU 求解也很快，但 Java 侧每次 `solveSquare`
     若重复分配/打包矩阵，会放大常数项。公平对比应在**同等线程限制**下用纯 Rust bench 或固定摊销打包成本。

5) 建议的可复现实验
   - 固定 `OMP_NUM_THREADS` / `OPENBLAS_NUM_THREADS`，与 faer/Rayon 线程池对照；
   - 矩阵尺度至少到中大型（例如 512^3、1024^3）再谈「谁更快」，小矩阵全是启动与内存带宽噪声。

6) Release 与「全链路」
   - Rust `profile.release` 已建议 `lto = "thin"`、`codegen-units = 1`，便于 faer/gemm 充分内联。
   - 本脚本测的是 **Java 打包列主序 + FFM + Rust faer**；若要与论文里「纯 faer」对比，请在 crate 内另写 `cargo bench` 去掉 Java 拷贝。
   - 可再加 `RUSTFLAGS=-C target-cpu=native` 做本机极限优化（可复现性略降）。

（以上内容随实现与硬件变化；以本机 `compare_with_numpy.py` 打印为准。）
"""
    print(text)


def section_correctness(_repo: Path, java_base: list[str], quick: bool) -> None:
    import numpy as np

    sizes = [(32, 32, 32), (48, 40, 36), (17, 29, 23)]
    if not quick:
        sizes.extend([(96, 80, 88), (128, 128, 128), (200, 120, 160)])

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        for m, n, p in sizes:
            rng = np.random.default_rng(42 + m + n + p)
            A = rng.standard_normal((m, n))
            B = rng.standard_normal((n, p))
            fa = tdp / f"A_{m}_{n}_{p}.bin"
            fb = tdp / f"B_{m}_{n}_{p}.bin"
            fc = tdp / f"C_{m}_{n}_{p}.bin"
            _write_row_major_bin(fa, A)
            _write_row_major_bin(fb, B)
            _run_java(
                java_base,
                [
                    "matmul",
                    str(fa),
                    str(fb),
                    str(fc),
                    str(m),
                    str(n),
                    str(p),
                    "1",
                    "1",
                ],
            )
            C_np = A @ B
            C_j = _read_row_major_bin(fc, m, p)
            ok = np.allclose(C_np, C_j, rtol=1e-9, atol=1e-10)
            assert ok, f"matmul mismatch {m}x{n}x{p}"
            print(f"  [OK] matmul {m}x{n}x{p} allclose vs NumPy")

    if not quick:
        with tempfile.TemporaryDirectory() as td:
            tdp = Path(td)
            rng = np.random.default_rng(2026)
            m = n = p = 256
            A = rng.standard_normal((m, n))
            B = rng.standard_normal((n, p))
            fa = tdp / "Al.bin"
            fb = tdp / "Bl.bin"
            fc = tdp / "Cl.bin"
            _write_row_major_bin(fa, A)
            _write_row_major_bin(fb, B)
            _run_java(
                java_base,
                ["matmul", str(fa), str(fb), str(fc), str(m), str(n), str(p), "0", "1"],
            )
            assert np.allclose(A @ B, _read_row_major_bin(fc, m, p), rtol=1e-8, atol=1e-9)
            print("  [OK] matmul 256x256x256 allclose vs NumPy (stress)")

    n_s = 24 if quick else 48
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        rng = np.random.default_rng(7)
        M = rng.standard_normal((n_s, n_s))
        A = M @ M.T + n_s * np.eye(n_s)
        b = rng.standard_normal(n_s)
        fA = tdp / "As.bin"
        fb = tdp / "bs.bin"
        fx = tdp / "xs.bin"
        _write_row_major_bin(fA, A)
        b.astype(np.float64).tofile(fb)

        _run_java(java_base, ["solve", str(fA), str(fb), str(fx), str(n_s), "1", "1"])
        x_np = np.linalg.solve(A, b)
        x_j = _read_vector_bin(fx, n_s)
        assert np.allclose(x_np, x_j, rtol=1e-8, atol=1e-9)
        print(f"  [OK] solve n={n_s} allclose vs NumPy")

        fL = tdp / "L.bin"
        _run_java(java_base, ["cholesky", str(fA), str(fL), str(n_s), "1", "1"])
        L_np = np.linalg.cholesky(A)
        L_j = _read_row_major_bin(fL, n_s, n_s)
        assert np.allclose(L_np, L_j, rtol=1e-7, atol=1e-8)
        print(f"  [OK] cholesky n={n_s} allclose vs NumPy")

        m, nmat = 18, 14
        if not quick:
            m, nmat = 128, 96
        Ar = rng.standard_normal((m, nmat))
        fAr = tdp / "Ar.bin"
        svd_dir = tdp / "svdout"
        _write_row_major_bin(fAr, Ar)
        _run_java(
            java_base,
            ["svd", str(fAr), str(m), str(nmat), str(svd_dir), "1", "1"],
        )
        Uj = _read_row_major_bin(svd_dir / "u.bin", m, min(m, nmat))
        sj = _read_vector_bin(svd_dir / "s.bin", min(m, nmat))
        Vtj = _read_row_major_bin(svd_dir / "vt.bin", nmat, nmat)
        k = min(m, nmat)
        recon = (Uj * sj[np.newaxis, :]) @ Vtj[:k, :]
        assert np.allclose(Ar, recon, rtol=1e-5, atol=1e-6)
        print(f"  [OK] svd {m}x{nmat} reconstruction vs NumPy SVD layout")

        # symmetric eigen
        ne = 16 if quick else 64
        As = rng.standard_normal((ne, ne))
        As = (As + As.T) / 2
        fAs = tdp / "Asym.bin"
        evd = tdp / "evout"
        _write_row_major_bin(fAs, As)
        _run_java(java_base, ["eigen", str(fAs), str(evd), str(ne), "1", "1"])
        w_j = _read_vector_bin(evd / "w.bin", ne)
        z_j = _read_row_major_bin(evd / "z.bin", ne, ne)
        w_np, z_np = np.linalg.eigh(As)
        assert np.allclose(w_np, w_j, rtol=1e-7, atol=1e-8)
        for j in range(ne):
            lhs = As @ z_j[:, j]
            rhs = w_j[j] * z_j[:, j]
            assert np.allclose(lhs, rhs, rtol=1e-6, atol=1e-7)
        print(f"  [OK] eigh n={ne} eigenvalues & residuals vs NumPy")


def section_benchmark(_repo: Path, java_base: list[str], quick: bool) -> None:
    import numpy as np
    import time

    warmup = 3 if quick else 5
    iters = 6 if quick else 15

    def bench_matmul(m, n, p, label):
        rng = np.random.default_rng(99)
        A = rng.standard_normal((m, n))
        B = rng.standard_normal((n, p))
        with tempfile.TemporaryDirectory() as td:
            tdp = Path(td)
            fa, fb, fc = tdp / "a.bin", tdp / "b.bin", tdp / "c.bin"
            _write_row_major_bin(fa, A)
            _write_row_major_bin(fb, B)
            for _ in range(warmup):
                _ = A @ B
            t0 = time.perf_counter()
            for _ in range(iters):
                C_np = A @ B
            t1 = time.perf_counter()
            ms_np = (t1 - t0) * 1000.0 / iters
            ms_java = _run_java(
                java_base,
                [
                    "matmul",
                    str(fa),
                    str(fb),
                    str(fc),
                    str(m),
                    str(n),
                    str(p),
                    str(warmup),
                    str(iters),
                ],
            )
        ratio = ms_java / ms_np if ms_np > 0 else float("inf")
        winner = "NumPy 更快" if ratio > 1.01 else ("接近" if ratio > 0.99 else "Java/HPC 更快")
        print(
            f"  {label}: NumPy {ms_np:.4f} ms/iter | Java {ms_java:.4f} ms/iter | "
            f"Java/NumPy={ratio:.2f}x  ({winner})"
        )

    # 规模：quick 用较小，完整用中大
    if quick:
        bench_matmul(192, 192, 192, "matmul 192x192x192")
    else:
        bench_matmul(384, 384, 384, "matmul 384x384x384")
        bench_matmul(512, 512, 512, "matmul 512x512x512")
        bench_matmul(256, 512, 384, "matmul 256x512x384")
        bench_matmul(800, 200, 800, "matmul 800x200x800 (skinny K)")

    n = 120 if quick else 280
    rng = np.random.default_rng(3)
    M = rng.standard_normal((n, n))
    A = M @ M.T + n * np.eye(n)
    b = rng.standard_normal(n)
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        fA, fb, fx = tdp / "a.bin", tdp / "b.bin", tdp / "x.bin"
        _write_row_major_bin(fA, A)
        b.astype(np.float64).tofile(fb)
        for _ in range(warmup):
            _ = np.linalg.solve(A, b)
        t0 = time.perf_counter()
        for _ in range(iters):
            _ = np.linalg.solve(A, b)
        t1 = time.perf_counter()
        ms_np = (t1 - t0) * 1000.0 / iters
        ms_java = _run_java(
            java_base, ["solve", str(fA), str(fb), str(fx), str(n), str(warmup), str(iters)]
        )
        ratio = ms_java / ms_np
        print(
            f"  solve n={n}: NumPy {ms_np:.4f} ms/iter | Java {ms_java:.4f} ms/iter | "
            f"Java/NumPy={ratio:.2f}x"
        )

    m, nv = (120, 100) if quick else (400, 320)
    rng = np.random.default_rng(11)
    Ar = rng.standard_normal((m, nv))
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        far = tdp / "a.bin"
        svd_dir = tdp / "o"
        _write_row_major_bin(far, Ar)
        for _ in range(warmup):
            _ = np.linalg.svd(Ar, full_matrices=False)
        t0 = time.perf_counter()
        for _ in range(iters):
            _ = np.linalg.svd(Ar, full_matrices=False)
        t1 = time.perf_counter()
        ms_np = (t1 - t0) * 1000.0 / iters
        ms_java = _run_java(
            java_base,
            ["svd", str(far), str(m), str(nv), str(svd_dir), str(warmup), str(iters)],
        )
        ratio = ms_java / ms_np
        print(
            f"  svd {m}x{nv}: NumPy {ms_np:.4f} ms/iter | Java {ms_java:.4f} ms/iter | "
            f"Java/NumPy={ratio:.2f}x"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="yishape-math-hpc vs NumPy 正确性与性能")
    parser.add_argument("--quick", action="store_true", help="更小矩阵、更少迭代")
    args = parser.parse_args()

    try:
        import numpy as np  # noqa: F401
    except ImportError:
        sys.exit("请先安装: pip install -r benchmarks/requirements.txt")

    repo = _repo_root()
    java_base = _java_cmd(repo)

    _print_runtime_env()
    print("== 正确性（与 NumPy 数值对比）==")
    section_correctness(repo, java_base, args.quick)
    print("\n== 耗时（粗基准；受 CPU、NumPy BLAS、JVM 预热影响）==")
    section_benchmark(repo, java_base, args.quick)
    print_analysis()
    print("完成。")


if __name__ == "__main__":
    main()

/**
 * <p><b>YiShape Math HPC</b>：通过 JDK FFM 调用原生库 {@code yishape_math_rust}（faer 稠密线代 + HiGHS 线性规划），
 * 针对主库 {@code yishape-math} 中<strong>纯 Java 或可选 OpenBLAS 仍显吃力</strong>的场景。
 * 需要 <strong>Java 25+</strong>。</p>
 *
 * <h2>与 yishape-math 的互补关系</h2>
 * <ul>
 *   <li><b>大规模稠密 SVD</b>：{@code RereSVDDecomposition} 与分治路径在超大 {@code m×n} 上时间与内存压力大；
 *       OpenBLAS 路径有 U/V<sup>T</sup> 缓冲上限。此处提供不设该 Java 阈值的 faer 全量 SVD（代价是 U<sub>full</sub> 在 Rust 内计算，仍受内存约束）。</li>
 *   <li><b>对称特征分解</b>：Java 端 Hessenberg + QR 迭代对大稠密阵偏慢；此处委托 faer 的 {@code SelfAdjointEigen}。</li>
 *   <li><b>SPD Cholesky</b>：与 OpenBLAS 类似，用于大 {@code n} 时减少 JVM 内因子化开销（仍建议在主库侧做对称性/正定检查以与异常语义一致）。</li>
 *   <li><b>线性规划</b>：主库 {@code RereSimplexLinProgSolver} 等面向教学与中规模；大规模、病态或工业级由 HiGHS 承担。
 *       <ul>
 *         <li><b>稠密</b>：非负变量、{@code A_ub}、{@code A_eq} 列主序。</li>
 *         <li><b>稀疏 CSR</b>：约束矩阵压缩行存（{@code rowPtr/colInd/values}），变量盒式界与行界支持 ±∞；适合大规模稀疏约束。</li>
 *       </ul>
 *       与主库 {@code ILinProgSolver} 之间通常需标准化与形式转换（集成阶段完成）。</li>
 * </ul>
 *
 * <p>入口类：{@link com.yishape.lab.math.hpc.YishapeHpc}（列主序 / FFM 细节见 {@code internal} 包）。
 * 在对应操作系统上执行 {@code mvn package} 时，会尝试用 cargo 编译并把你当前平台的原生库打进 JAR（{@code META-INF/native-libs/…}），
 * 运行时可自动解压加载；亦可通过 {@code -Dyishape.hpc.library.path=} 显式指定动态库路径。</p>
 */
package com.yishape.lab.math.hpc;

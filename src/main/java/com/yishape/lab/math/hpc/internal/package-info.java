/**
 * <p>供 {@link com.yishape.lab.math.hpc.YishapeHpc} 使用的内部实现：<strong>不应</strong>作为稳定对外 API 依赖。</p>
 * <ul>
 *   <li>{@link com.yishape.lab.math.hpc.internal.YishapeMathRust}：FFM 下调用、列主序与 CSR 裸数组约定</li>
 *   <li>{@link com.yishape.lab.math.hpc.internal.HpcLayouts}：行主序 {@code double[][]} 与列主序打包互转</li>
 *   <li>{@link com.yishape.lab.math.hpc.internal.HpcNativeLoader} / {@link com.yishape.lab.math.hpc.internal.HpcNativePlatform}：加载路径与 OS classifier</li>
 * </ul>
 */
package com.yishape.lab.math.hpc.internal;

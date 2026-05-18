package com.yishape.lab.math.hpc;

/**
 * HNSW 近似最近邻搜索结果。
 *
 * @param status     状态码，与 {@link YishapeHpcStatus} 对齐
 * @param ids        结果点 ID 数组，长度 {@code found}
 * @param distances  结果距离数组（由底层 metric 定义），长度 {@code found}
 * @param found      实际返回的结果数（可能小于请求的 {@code k}）
 */
public record HnswSearchResult(int status, long[] ids, float[] distances, int found) {

    /**
     * 是否成功返回结果（状态码为 {@link YishapeHpcStatus#OK}）。
     */
    public boolean ok() {
        return status == YishapeHpcStatus.OK;
    }
}

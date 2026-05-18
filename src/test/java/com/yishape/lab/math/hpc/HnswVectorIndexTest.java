package com.yishape.lab.math.hpc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HNSW 向量索引 FFI 端到端测试。
 *
 * <p>需要 yishape_math_rust 原生库已加载；无原生时本类测试跳过。</p>
 */
class HnswVectorIndexTest {

    private static String nativeSkipReason;

    @BeforeAll
    static void probeNative() {
        if (YishapeHpc.abiVersion() > 0) {
            nativeSkipReason = null;
        } else {
            nativeSkipReason = "无法加载 yishape_math_rust（需 JAR 内嵌 DLL/SO、java.library.path 或 -Dyishape.hpc.library.path）";
        }
    }

    private static void assumeNative() {
        Assumptions.assumeTrue(nativeSkipReason == null, nativeSkipReason);
    }

    @Test
    void hnswBuildSearchGetAddSmoke() {
        assumeNative();
        Assumptions.assumeTrue(YishapeHpc.isHnswNativeAvailable(), "HNSW 原生模块不可用");

        int dims = 4;
        float[][] data = {
                {1.0f, 0.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f, 0.0f},
                {0.0f, 0.0f, 1.0f, 0.0f},
                {0.0f, 0.0f, 0.0f, 1.0f}
        };
        long[] ids = {10L, 20L, 30L, 40L};
        float[] flat = flatten(data);

        // build
        Long handle = YishapeHpc.hnswBuildF32(dims, flat, ids, 0, 16, 200, 50);
        assertNotNull(handle, "build must return non-null handle");
        assertTrue(handle > 0, "handle must be positive");

        try {
            // size
            int sz = YishapeHpc.hnswSize(handle);
            assertEquals(4, sz, "index size should be 4");

            // search: query near id=10 -> expect 10 first
            float[] query = {1.0f, 0.1f, 0.0f, 0.0f};
            HnswSearchResult res = YishapeHpc.hnswSearchF32(handle, query, 2);
            assertNotNull(res, "search result must not be null");
            assertEquals(YishapeHpcStatus.OK, res.status(), "search status should be OK");
            assertTrue(res.found() >= 1, "search should return at least 1 result");
            assertEquals(10L, res.ids()[0], "nearest neighbor should be id 10");
            assertTrue(res.distances()[0] >= 0.0f, "distance should be non-negative");

            // get
            float[] buf = new float[dims];
            int rc = YishapeHpc.hnswGetF32(handle, 10L, buf);
            assertEquals(YishapeHpcStatus.OK, rc, "get status should be OK");
            assertEquals(1.0f, buf[0], 1e-5f, "get vector[0] mismatch");
            assertEquals(0.0f, buf[1], 1e-5f, "get vector[1] mismatch");

            // add
            float[] newVec = {0.5f, 0.5f, 0.5f, 0.5f};
            int addRc = YishapeHpc.hnswAddF32(handle, 50L, newVec);
            assertEquals(YishapeHpcStatus.OK, addRc, "add status should be OK");
            assertEquals(5, YishapeHpc.hnswSize(handle), "size should be 5 after add");

            // set ef and search again
            int efRc = YishapeHpc.hnswSetEf(handle, 100);
            assertEquals(YishapeHpcStatus.OK, efRc, "setEf status should be OK");

            HnswSearchResult res2 = YishapeHpc.hnswSearchF32(handle, query, 3);
            assertNotNull(res2);
            assertTrue(res2.found() >= 1, "search after add should still work");
        } finally {
            // free
            int freeRc = YishapeHpc.hnswFree(handle);
            assertEquals(YishapeHpcStatus.OK, freeRc, "free status should be OK");
        }
    }

    @Test
    void hnswCosineMetricSmoke() {
        assumeNative();
        Assumptions.assumeTrue(YishapeHpc.isHnswNativeAvailable(), "HNSW 原生模块不可用");

        int dims = 3;
        float[][] data = {
                {1.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f},
                {1.0f, 1.0f, 0.0f}
        };
        long[] ids = {0L, 1L, 2L};
        // metric 1 = cosine
        Long handle = YishapeHpc.hnswBuildF32(dims, flatten(data), ids, 1, 16, 200, 50);
        assertNotNull(handle);
        assertTrue(handle > 0);

        try {
            float[] query = {1.0f, 0.0f, 0.0f};
            HnswSearchResult res = YishapeHpc.hnswSearchF32(handle, query, 1);
            assertNotNull(res);
            assertEquals(YishapeHpcStatus.OK, res.status());
            assertTrue(res.found() >= 1);
            // query [1,0,0] should be closest to [1,0,0] (id=0)
            assertEquals(0L, res.ids()[0]);
        } finally {
            YishapeHpc.hnswFree(handle);
        }
    }

    private static float[] flatten(float[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        float[] flat = new float[rows * cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flat, i * cols, cols);
        }
        return flat;
    }
}

package com.yishape.lab.math.hpc.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 加载动态库 {@code yishape_math_rust}，解析顺序为：
 * <ol>
 *   <li>系统属性 {@code yishape.hpc.library.path}（绝对路径，见 {@link java.lang.System#load(String)}）</li>
 *   <li>Classpath / 模块路径上 JAR 内资源 {@code META-INF/native-libs/<classifier>/<文件名>}（解压到临时文件后 {@link System#load}）</li>
 *   <li>{@link System#loadLibrary(String)} 按平台默认库名搜索路径</li>
 * </ol>
 * 临时文件在 VM 退出时按 {@link java.io.File#deleteOnExit()} 清理。
 *
 * @see com.yishape.lab.math.hpc.internal.HpcNativePlatform
 */
public final class HpcNativeLoader {

    private static final String BUNDLED_PREFIX = "META-INF/native-libs/";

    private HpcNativeLoader() {
    }

    /**
     * @throws UnsatisfiedLinkError 与 {@link System#load} / {@link System#loadLibrary} 一致
     */
    public static void loadYishapeMathRust() {
        String override = System.getProperty("yishape.hpc.library.path");
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override).toAbsolutePath().normalize();
            System.load(p.toString());
            return;
        }
        if (loadBundledFromClasspath()) {
            return;
        }
        System.loadLibrary("yishape_math_rust");
    }

    private static boolean loadBundledFromClasspath() {
        String classifier = HpcNativePlatform.classifier();
        String fileName = HpcNativePlatform.bundledLibraryFileName();
        String resource = BUNDLED_PREFIX + classifier + "/" + fileName;
        ClassLoader cl = HpcNativeLoader.class.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            Path tmp = extractToTempFile(in, fileName);
            System.load(tmp.toAbsolutePath().normalize().toString());
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("无法从 Classpath 释放原生库: " + resource, e);
        }
    }

    private static Path extractToTempFile(InputStream in, String suffixHint) throws IOException {
        String suffix = suffixHint.contains(".") ? suffixHint.substring(suffixHint.lastIndexOf('.')) : ".bin";
        Path f = Files.createTempFile("yishape_hpc_yishape_math_rust_", suffix);
        try {
            Files.copy(Objects.requireNonNull(in), f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignored) {
                // ignore
            }
            throw e;
        }
        f.toFile().deleteOnExit();
        return f;
    }
}

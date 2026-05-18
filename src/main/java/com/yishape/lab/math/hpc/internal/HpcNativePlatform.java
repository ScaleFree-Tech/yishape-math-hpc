package com.yishape.lab.math.hpc.internal;

import java.util.Locale;

/**
 * 与 Maven {@code os-maven-plugin} 的 {@code ${os.detected.classifier}} 对齐，用于在 JAR 内定位
 * {@code META-INF/native-libs/<classifier>/<file>}。
 */
public final class HpcNativePlatform {

    private HpcNativePlatform() {
    }

    /**
     * 例如 {@code windows-x86_64}、{@code linux-aarch_64}、{@code osx-x86_64}。
     */
    public static String classifier() {
        return normalizedOs() + "-" + normalizedArch();
    }

    /**
     * 与 {@link com.yishape.lab.math.hpc.internal.HpcNativeLoader} 在 JAR 内查找的资源文件名一致：
     * Windows 为 {@code .dll}，macOS 为 {@code .dylib}，其余 Unix 常为 {@code .so}。
     */
    public static String bundledLibraryFileName() {
        String os = normalizedOs();
        if ("windows".equals(os)) {
            return "yishape_math_rust.dll";
        }
        if ("osx".equals(os)) {
            return "libyishape_math_rust.dylib";
        }
        return "libyishape_math_rust.so";
    }

    private static String normalizedOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("windows")) {
            return "windows";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    private static String normalizedArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch_64";
            case "x86", "i386", "i686" -> "x86_32";
            default -> arch.replace('-', '_');
        };
    }
}

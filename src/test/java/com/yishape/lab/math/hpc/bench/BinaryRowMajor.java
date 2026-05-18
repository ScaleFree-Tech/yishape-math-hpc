package com.yishape.lab.math.hpc.bench;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 与 NumPy {@code ndarray.tofile()} / {@code fromfile()} 一致：行主序、little-endian float64，供 {@link HpcNumpyInteropMain} 使用。 */
public final class BinaryRowMajor {

    private BinaryRowMajor() {
    }

    /**
     * 从二进制文件读取 {@code rows×cols} 矩阵；文件须恰好 {@code rows*cols*8} 字节。
     */
    public static double[][] readMatrix(Path path, int rows, int cols) throws IOException {
        long need = (long) rows * cols * Double.BYTES;
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            if (ch.size() != need) {
                throw new IOException("expected " + need + " bytes, got " + ch.size() + " for " + path);
            }
            ByteBuffer bb = ByteBuffer.allocate((int) need).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(bb);
            bb.flip();
            double[][] a = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    a[i][j] = bb.getDouble();
                }
            }
            return a;
        }
    }

    /** 读取长度 {@code n} 的向量，文件须 {@code n*8} 字节。 */
    public static double[] readVector(Path path, int n) throws IOException {
        long need = (long) n * Double.BYTES;
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            if (ch.size() != need) {
                throw new IOException("expected " + need + " bytes for vector " + path);
            }
            ByteBuffer bb = ByteBuffer.allocate(n * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(bb);
            bb.flip();
            double[] v = new double[n];
            for (int i = 0; i < n; i++) {
                v[i] = bb.getDouble();
            }
            return v;
        }
    }

    /** 将行主序矩阵写出为 little-endian float64 原始字节。 */
    public static void writeMatrix(Path path, double[][] a) throws IOException {
        int rows = a.length;
        int cols = a[0].length;
        ByteBuffer bb = ByteBuffer.allocate(rows * cols * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                bb.putDouble(a[i][j]);
            }
        }
        bb.flip();
        byte[] raw = new byte[bb.remaining()];
        bb.get(raw);
        Files.write(path, raw, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /** 将向量写出为 little-endian float64 原始字节。 */
    public static void writeVector(Path path, double[] v) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(v.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (double x : v) {
            bb.putDouble(x);
        }
        bb.flip();
        byte[] raw = new byte[bb.remaining()];
        bb.get(raw);
        Files.write(path, raw, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}

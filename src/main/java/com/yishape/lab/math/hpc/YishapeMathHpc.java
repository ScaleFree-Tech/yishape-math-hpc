package com.yishape.lab.math.hpc;

/**
 * 命令行冒烟示例：打印 ABI、调用 {@link YishapeHpc#testHello()}、演示 {@link YishapeHpc#solveSquare}
 * 与 {@link YishapeHpc#lpNonnegative}。需已成功加载原生库（见 README / {@link com.yishape.lab.math.hpc.internal.HpcNativeLoader}）。
 */
public final class YishapeMathHpc {

    private YishapeMathHpc() {
    }

    /**
     * 入口：无参时也可直接运行以观察是否能在当前环境加载原生侧。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        System.out.println("ABI v" + YishapeHpc.abiVersion());
        YishapeHpc.testHello();

        DenseSolveResult sol = YishapeHpc.solveSquare(
                new double[][] {{2.0, 3.0}, {2.0, 4.0}},
                new double[] {5.0, 6.0});
        System.out.println("solve rc=" + sol.status() + " x=" + sol.x()[0] + "," + sol.x()[1]);

        LpNonnegativeResult lp = YishapeHpc.lpNonnegative(
                new double[] {-1, -2},
                new double[][] {{1, 1}},
                new double[] {1},
                null,
                null);
        System.out.println("lp rc=" + lp.status() + " obj=" + lp.objective() + " x=" + lp.x()[0] + "," + lp.x()[1]);
    }
}

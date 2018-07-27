import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTest {
    private static final Log LOGGER = LogFactory.getLog(LoadTest.class);
    private static double memoryUnit = 1024 * 1024;

    Runtime runtime;

    @Before
    public void initContext() {

        runtime = Runtime.getRuntime();
    }

    public Invoice test(int seed) {
        Invoice i = new Invoice(seed);
        long time = System.currentTimeMillis();
        AggregatorContext context = new AggregatorContext(false);
        Processor.process(i, "i", context);
        double memory = (runtime.totalMemory() - runtime.freeMemory()) / memoryUnit;
        time = System.currentTimeMillis() - time;
        LOGGER.info(String.format("%10d,%10d,%10.1f", seed, time, memory));
        return i;
    }
    @After
    public void memory(){
        double memory = (runtime.totalMemory() - runtime.freeMemory()) / memoryUnit;
        LOGGER.info(String.format("Memory:%10.1f",memory));
    }
    @BeforeClass
    @AfterClass
    public static void gcAndReport() {
    	System.gc();
    	double memory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / memoryUnit;
        LOGGER.info(String.format("Memory after GC:%10.1f",memory));
    }
    @Test
    public void testOne() {
        Invoice i = test(1);
        Assert.assertEquals(2.92, i.averageUnitPrice, 0.01);
        Assert.assertEquals(200.0, i.totalInvoice,0.01);
        Assert.assertEquals(110, i.totalQuantity);
    }

    @Test
    public void testThousandOnes() {
        ExecutorService service = Executors.newFixedThreadPool(20);
        for (int a = 0; a < 1000; a++) {
            service.execute(() -> {
                Invoice i = test(1);
                Assert.assertEquals(2.92, i.averageUnitPrice, 0.01);
                Assert.assertEquals(200.0, i.totalInvoice,0.01);
                Assert.assertEquals(110, i.totalQuantity);
            });
        }
        service.shutdown();
        while (!service.isTerminated()) {
        }

    }

    @Test
    public void testTen() {
        Invoice i = test(10);
        Assert.assertEquals(0.51, i.averageUnitPrice, 0.01);
        Assert.assertEquals(2000, i.totalInvoice, 0.001);
        Assert.assertEquals(10100, i.totalQuantity);
    }

    @Test
    public void testHundred() {
        Invoice i = test(100);
        Assert.assertEquals(0.07, i.averageUnitPrice, 0.01);
        Assert.assertEquals(20000, i.totalInvoice, 0.001);
        Assert.assertEquals(1001000, i.totalQuantity);
    }

    @Test
    public void testThousand() {
        test(1000);
    }

    @Test
    public void testMax() {
        Invoice i = test(4160);
        Assert.assertEquals(0.0026, i.averageUnitPrice, 0.0001);
        Assert.assertEquals(832000, i.totalInvoice, 0.001);
        Assert.assertEquals(1730601600, i.totalQuantity);
    }

    @Test
    public void testTenHundred() {
        ExecutorService service = Executors.newFixedThreadPool(5);
        for (int a = 0; a < 10; a++)
            service.execute(() -> {
                Invoice i = test(100);
                Assert.assertEquals(0.07, i.averageUnitPrice, 0.01);
                Assert.assertEquals(20000, i.totalInvoice, 0.001);
                Assert.assertEquals(1001000, i.totalQuantity);
            });
        service.shutdown();
        while (!service.isTerminated()) {
        }
    }

    @Test
    public void testHundredHundred() {
        ExecutorService service = Executors.newFixedThreadPool(20);
        for (int a = 0; a < 100; a++)
            service.execute(() -> {
                        Invoice i = test(100);
                        Assert.assertEquals(0.07, i.averageUnitPrice, 0.01);
                        Assert.assertEquals(20000, i.totalInvoice, 0.001);
                        Assert.assertEquals(1001000, i.totalQuantity);
                    }
            );
        service.shutdown();
        while (!service.isTerminated()) {
        }
    }

    @Collect(value = "total", what = "this.totalPrice")
    public class Detail {
        @Collect("quantity")
        public int quantity;
        @Collect("unitPrice")
        public double unitPrice;
        @Execute("this.quantity * this.unitPrice")
        public double totalPrice;

        public Detail(int quantity, double unitPrice) {
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    public class Invoice {
        public List<Detail> details;
        @Execute("sum('quantity')")
        public long totalQuantity;
        @Execute("avg('unitPrice')")
        public double averageUnitPrice;
        @Execute("sum('total')")
        public double totalInvoice;

        public Invoice(int seed) {
            this.details = new ArrayList<>();
            for (int value = 1; value <= (seed * 10); value++)
                this.details.add(new Detail(2 * value, 10.0 / value));
        }
    }
}

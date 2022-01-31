package com.github.jpingus;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.hasItems;

public class ProcessorTest {
    private static final Log LOG = LogFactory.getLog(ProcessorTest.class);
    Business b;
    AggregatorContext myAggregatorContext;

    @Before
    public void initBusiness() {
        b = new Business();
        b.elements = new Row[]{new Row(10, "a"), null, new Row(20, "b")};
        b.elements2 = new Row2[]{new Row2(8), new Row2(null), new Row2(5)};
        b.elements3 = new ArrayList<>();
        b.elements3.add(new Row2(5));
        b.elements4 = new HashSet<>();
        b.elements4.add(new Row(7, "c"));
        b.elements4.add(new Row(124, null));
        b.elements4.add(new Row2(2));
        b.elements5 = new Hashtable<>();
        b.elements5.put("a", new Row(1, "a"));
        b.elements5.put("b", new Row2(3));
        b.elements5.put("c", new Row2(3));
        b.elements5.put("d", new Row(0, "a"));
    }

    @Before
    public void initContext() {
        myAggregatorContext = AggregatorContext.builder().debug(false).build();
        //Adding custom functions to the context
        myAggregatorContext.register("my", Functions.class);
        myAggregatorContext.setPackageStarts(Collections.singletonList("com.github.jpingus"));
        myAggregatorContext.setProcessings(
                Collections.singletonList(
                new AggregatorProcessing() {
                    @Override
                    public void preProcess(Object o, AggregatorContext context) {
                    }

                    @Override
                    public void postProcess(Object o, AggregatorContext context) {
                        if (o instanceof Business) {
                            Business b = (Business) o;
                            b.myGrandTotals = new HashMap<>();
                            Set<String> ids = context.asSet("All my ccm2 ids");
                            for (String ccm2 : ids) {
                                b.myGrandTotals.put(ccm2, context.process(new GrandTotal(0, ccm2)));
                            }
                        }
                    }
                })
        );
    }

    @After
    public void extractConfig() {
        System.out.println(ConfigurationFactory.extractConfig(myAggregatorContext));
    }

    @Test
    public void test() {
        Processor.process(b, "b", myAggregatorContext);
        Assert.assertEquals(Integer.valueOf(162), b.total);
        Assert.assertEquals(Integer.valueOf(26), b.total2);
        Assert.assertEquals(Integer.valueOf(11), b.myGrandTotals.get("a").sum);
        Assert.assertEquals(Integer.valueOf(20), b.myGrandTotals.get("b").sum);
        Assert.assertEquals(Integer.valueOf(33), b.myGrandTotals.get("c").sum);
        Assert.assertEquals(Integer.valueOf(12), b.doubleCount);
        Assert.assertEquals(4.3333, b.avg2, 0.0001);
        Assert.assertEquals(22.822, b.rate, 0.0001);
        Assert.assertEquals("[c,a,a,a,b]", b.ccm2);
        Assert.assertEquals(true, myAggregatorContext.contains("All my ccm2 ids", "a"));
        Assert.assertEquals(4.42, b.totalBig.doubleValue(), 0.001);
        Assert.assertEquals( Integer.valueOf(15), myAggregatorContext.sum("Unknown aggregator", 15));
        //Just verify the auto boxing for primitive does not break
        Integer[] o1 = myAggregatorContext.asArray("test array int");
        Assert.assertThat(Arrays.asList(o1), hasItems(8, 0, 5, 5, 2, 3, 3));
        Double[] o2 = myAggregatorContext.asArray("test array double");
        Assert.assertEquals(7, o2.length);
        myAggregatorContext.asArray("Grand total c");
        ConfigurationFactory.marshall(myAggregatorContext, System.out);
    }

    @Test
    public void testError() {
        AggregatorContext context = Processor.process(b, "b",
                AggregatorContext.builder().debug(true).build());
        LOG.info("trace of execution:" + context.getLastProcessTrace());
        context.evaluate("error");
        context.count("whatever");
    }

    @Test
    public void testApi() {
        Processor.process(b.elements5, "be5", myAggregatorContext);
        //Try combining custom functions together outside of the "box" with no "executor"
        Object result = myAggregatorContext.evaluate("my:divide(sum('total'),sum('total2'))");
        Assert.assertEquals(Double.class, result.getClass());
        Assert.assertEquals(0.166, (Double) result, 0.001);
        Assert.assertEquals( Integer.valueOf(2), myAggregatorContext.count("total2"));
        Assert.assertNull(myAggregatorContext.sum("TOTO"));
        Object[] ccm2Array = myAggregatorContext.asArray("All my ccm2 ids");
        Set<Object> ccm2Set = myAggregatorContext.asSet("All my ccm2 ids");
        LOG.debug(Arrays.toString(ccm2Array));
        LOG.debug(ccm2Set);
        Assert.assertEquals(myAggregatorContext.count("All my ccm2 ids").intValue(), ccm2Array.length);
        Assert.assertEquals(2, ccm2Array.length);
        Assert.assertEquals(1, ccm2Set.size());
        Assert.assertEquals(String.class, ccm2Array[0].getClass());
        Set<String> aggregators = myAggregatorContext.aggregators();
        LOG.debug(aggregators);
        Assert.assertNotNull(aggregators);
        Assert.assertEquals(8, aggregators.size());
        Assert.assertThat(aggregators, hasItems("Big decimal", "total", "Grand total c", "All my ccm2 ids", "Grand total a", "test array double", "total2", "test array int"));
    }

    @Test
    public void ErrorTest() {
        Error1 err = new Error1();
        err.test = 1;
        Processor.process(err);
    }

    @Test
    public void Error2Test() {
        Error2 err = new Error2();
        err.test = 1;
        Processor.process(err);
    }

    class Error1 {
        @Collect("test")
        @Execute("sum('test2')")
        int test;
    }

    class Error2 {
        @Collect("test")
        int test;
        @Execute("sum('test2')")
        int totalTest;
    }
}
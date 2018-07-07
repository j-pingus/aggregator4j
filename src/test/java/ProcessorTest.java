import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

public class ProcessorTest {
    Business b;
    AggregatorContext myAggregatorContext;
    private static final Log LOG = LogFactory.getLog(ProcessorTest.class);
    @Before
    public void initBusiness() {
        b = new Business();
        b.myGrandTotals = new HashMap<>();
        b.myGrandTotals.put("a", new GrandTotal(0, "a"));
        b.myGrandTotals.put("b", new GrandTotal(0, "b"));
        b.myGrandTotals.put("c", new GrandTotal(0, "c"));
        b.elements = new Row[]{new Row(10, "a"), null, new Row(20, "b")};
        b.elements2 = new Row2[]{new Row2(8), new Row2(null), new Row2(5)};
        b.elements3 = new ArrayList<Row2>();
        b.elements3.add(new Row2(5));
        b.elements4 = new HashSet<Rows>();
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
        myAggregatorContext = new AggregatorContext();
        //Adding custom functions to the context
        myAggregatorContext.register("my", Functions.class);
    }

    @Test
    public void test() throws Exception {
        Processor.process(b, myAggregatorContext);
        Assert.assertEquals(new Integer(162), b.total);
        Assert.assertEquals(new Integer(26), b.total2);
        Assert.assertEquals(new Integer(11), b.myGrandTotals.get("a").sum);
        Assert.assertEquals(new Integer(20), b.myGrandTotals.get("b").sum);
        Assert.assertEquals(new Integer(33), b.myGrandTotals.get("c").sum);
        Assert.assertEquals(new Integer(12), b.doubleCount);
        Assert.assertEquals(4.3333, b.avg2, 0.0001);
        Assert.assertEquals(22.822, b.rate, 0.0001);
        Assert.assertEquals("[a,b,c,a,a]", b.ccm2);
        Assert.assertEquals(new Double(4.42), b.totalBig.doubleValue(), 0.001);
    }

    @Test
    public void testApi() throws Exception {
        Processor.process(b.elements5, myAggregatorContext);
        //Try combining custom functions together outside of the "box" with no "executor"
        Object result = myAggregatorContext.evaluate("my:divide(sum('total'),sum('total2'))");
        Assert.assertEquals(Double.class, result.getClass());
        Assert.assertEquals(new Double(0.166), (Double) result, 0.001);
        Assert.assertEquals(new Integer(2), myAggregatorContext.count("total2"));
        Assert.assertNull(myAggregatorContext.sum("TOTO"));
        Object ccm2Array[] = myAggregatorContext.asArray("All my ccm2 ids");
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
        Assert.assertEquals(6, aggregators.size());
        Set<String> expectedAggregators = new HashSet<>();
        for(String eA: new String[] {"Big decimal", "total", "Grand total c", "All my ccm2 ids", "Grand total a", "total2"}) {
        	expectedAggregators.add(eA);
        }
        Assert.assertEquals(expectedAggregators, aggregators);
    }
}
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

public class ProcessorTest {
    Business b;
    AggregatorContext myAggregatorContext;

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
        Assert.assertEquals("[a,b,c,a]", b.ccm2);
        Assert.assertEquals(new Double(4.42), b.totalBig.doubleValue(), 0.001);
        System.out.println("Aggregators :" + myAggregatorContext.aggregators());
    }

    @Test
    public void testApi() throws Exception {
        Processor.process(b.elements5, myAggregatorContext);
        //Try combining custom functions together outside of the "box" with no "executor"
        Object result = myAggregatorContext.evaluate("my:divide(sum('total'),sum('total2'))");
        Assert.assertEquals(Double.class, result.getClass());
        Assert.assertEquals(new Double(0.166), (Double) result, 0.001);
        Assert.assertEquals(new Integer(2), myAggregatorContext.count("total2"));
        Object ret[] = myAggregatorContext.asArray("All my ccm2 ids");
        Assert.assertEquals(myAggregatorContext.count("All my ccm2 ids").intValue(), ret.length);
        Assert.assertEquals(String.class, ret[0].getClass());

    }
}
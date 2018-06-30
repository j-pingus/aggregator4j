import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProcessorTest {
    Business b;
    @Before
    public void initBusiness(){
        b=new Business();
        b.elements=new Row[]{new Row(10,"a"),null,new Row(20,"b")};
        b.elements2=new Row2[]{new Row2(10),new Row2(null), new Row2(5)};
        b.elements3 = new ArrayList<Row2>();
        b.elements3.add(new Row2(5));
        b.elements4 = new HashSet<Rows>();
        b.elements4.add(new Row(7,"c"));
        b.elements4.add(new Row2(2));
        b.elements5 = new Hashtable<>();
        b.elements5.put("a",new Row(1,"d"));
        b.elements5.put("b",new Row2(3));
    }
    @Test
    public void test() throws Exception {
        Processor.process(b);
        Assert.assertEquals(new Integer(38), b.total);
        Assert.assertEquals(new Integer(25), b.total2);
        Assert.assertEquals(new Integer(63), b.myGrandTotal.sum);
        Assert.assertEquals("abcd", b.ccm2);
    }
}
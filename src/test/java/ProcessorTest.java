import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProcessorTest {
    Business b;
    @Before
    public void initBusiness(){
        b=new Business();
        b.elements=new Row[]{new Row(10),null,new Row(20)};
        b.otherElements=new Row2[]{new Row2(5),new Row2(null),new Row2(10)};
    }
    @Test
    public void test() throws Exception {
        Processor.process(b);
        Assert.assertEquals(new Integer(30), b.total);
        Assert.assertEquals(new Integer(15), b.anotherTotal);
        Assert.assertEquals(new Integer(45), b.grandTotal);
    }
}
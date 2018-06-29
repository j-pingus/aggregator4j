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
    }
    @Test
    public void test() throws Exception {
        Processor.process(b);
        Assert.assertEquals(new Integer(30), b.total);
        Assert.assertEquals(new Integer(15), b.total2);
        Assert.assertEquals(new Integer(45), b.myGrandTotal.sum);
        Assert.assertEquals("ab", b.ccm2);
    }
}
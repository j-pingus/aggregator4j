package com.github.jpingus;

import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Collections;

public class ParseConfig {


    @Test
    public void parse() {
        AggregatorContext context = AggregatorContext.builder()
                .config(ConfigurationFactory.unMarshall(ParseConfig.class.getResourceAsStream("/config.xml")))
                .build();
        IgnorableClassDetector detector = AggregatorContext.packageDetector(
                Collections.singletonList("com.github.jpingus"));
        Assert.assertEquals(new Analysed(Business.class, detector), context.getAnalysed(Business.class));
        Assert.assertEquals(new Analysed(Row.class, detector), context.getAnalysed(Row.class));
        Assert.assertEquals(new Analysed(Rows.class, detector), context.getAnalysed(Rows.class));
        Assert.assertEquals(new Analysed(Row2.class, detector), context.getAnalysed(Row2.class));
        Assert.assertEquals(new Analysed(TestInvoice.Detail.class, detector), context.getAnalysed(TestInvoice.Detail.class));
        Assert.assertEquals(new Analysed(TestInvoice.Group.class, detector), context.getAnalysed(TestInvoice.Group.class));
        Assert.assertEquals(new Analysed(TestInvoice.GroupTotal.class, detector), context.getAnalysed(TestInvoice.GroupTotal.class));
        Assert.assertEquals(new Analysed(TestInvoice.Invoice.class, detector), context.getAnalysed(TestInvoice.Invoice.class));
    }

}

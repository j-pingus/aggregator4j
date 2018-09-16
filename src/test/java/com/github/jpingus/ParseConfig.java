package com.github.jpingus;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class ParseConfig {


    @Test
    public void parse() throws ParserConfigurationException, IOException, SAXException {
        AggregatorContext context = ConfigurationFactory.buildAggregatorContext(ParseConfig.class.getResourceAsStream("/config.xml"));
        Assert.assertEquals(new Analysed(Business.class, "com.github.jpingus"), context.getAnalysed(Business.class));
        Assert.assertEquals(new Analysed(Row.class, "com.github.jpingus"), context.getAnalysed(Row.class));
        Assert.assertEquals(new Analysed(Rows.class, "com.github.jpingus"), context.getAnalysed(Rows.class));
        Assert.assertEquals(new Analysed(Row2.class, "com.github.jpingus"), context.getAnalysed(Row2.class));
        Assert.assertEquals(new Analysed(TestInvoice.Detail.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Detail.class));
        Assert.assertEquals(new Analysed(TestInvoice.Group.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Group.class));
        Assert.assertEquals(new Analysed(TestInvoice.GroupTotal.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.GroupTotal.class));
        Assert.assertEquals(new Analysed(TestInvoice.Invoice.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Invoice.class));
    }

}

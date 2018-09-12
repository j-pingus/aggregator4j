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

    public static final String FIELD = "field";
    public static final String WHEN = "when";
    public static final String EXECUTE = "execute";
    public static final String JEXL = "jexl";
    public static final String COLLECT = "collect";
    public static final String TO = "to";
    public static final String WHAT = "what";
    public static final String VARIABLE = "variable";
    public static final String NAME = "name";
    public static final String EMPTY = "";
    public static final String NAMESPACE = "namespace";
    public static final String REGISTER_CLASS = "registerClass";
    public static final String FUNCTION = "function";
    public static final String PACKAGE = "package";
    public static final String CLASS = "class";
    public static final String AGGREGATOR4J = "aggregator4j";

    @Test
    public void parse() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        System.out.println(com.github.jpingus.TestInvoice.Group.class.getName());
        Document docConfig =
                builder.parse(ParseConfig.class.getResourceAsStream("/config.xml"));
        AggregatorContext context = analyse(docConfig);
        Assert.assertEquals(new Analysed(Business.class, "com.github.jpingus"), context.getAnalysed(Business.class));
        Assert.assertEquals(new Analysed(Row.class, "com.github.jpingus"), context.getAnalysed(Row.class));
        Assert.assertEquals(new Analysed(Rows.class, "com.github.jpingus"), context.getAnalysed(Rows.class));
        Assert.assertEquals(new Analysed(Row2.class, "com.github.jpingus"), context.getAnalysed(Row2.class));
        Assert.assertEquals(new Analysed(TestInvoice.Detail.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Detail.class));
        Assert.assertEquals(new Analysed(TestInvoice.Group.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Group.class));
        Assert.assertEquals(new Analysed(TestInvoice.GroupTotal.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.GroupTotal.class));
        Assert.assertEquals(new Analysed(TestInvoice.Invoice.class, "com.github.jpingus"), context.getAnalysed(TestInvoice.Invoice.class));
    }

    private AggregatorContext analyse(Document docConfig) {
        Element root = docConfig.getDocumentElement();
        if (!AGGREGATOR4J.equals(root.getTagName()))
            throw new Error("config root must be aggregator4j");
        AggregatorContext context = new AggregatorContext(true);
        NodeList level1 = root.getChildNodes();
        for (int i = 0; i < level1.getLength(); i++) {
            analyse(level1.item(i), context);
        }
        return context;
    }

    /*
        <function namespace="my" registerClass="com.github.jpingus.Functions"/>
        <package name="com.github.jpingus"/>
        <class name="com.github.jpingus.Business">
            <execute field="total" jexl="sum('total')"/>
            <collect field="ccm2" to="All my ccm2 ids" />
            <variable name="ccm2" field="ccm2"/>
        </class>
    */
    private void analyse(Node item, AggregatorContext context) {
        if (FUNCTION.equals(item.getNodeName())) {
            analyseFunction(item, context);
        } else if (PACKAGE.equals(item.getNodeName())) {
            analysePackage(item, context);
        } else if (CLASS.equals(item.getNodeName())) {
            analyseClass(item, context);
        }
    }

    /*
        <function namespace="my" registerClass="com.github.jpingus.Functions"/>
    */
    private void analyseFunction(Node item, AggregatorContext context) {
        Node namespace = item.getAttributes().getNamedItem(NAMESPACE);
        Node registerClass = item.getAttributes().getNamedItem(REGISTER_CLASS);
        if (namespace != null && registerClass != null) {
            Class clazz = null;
            try {
                clazz = this.getClass().forName(registerClass.getNodeValue());
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot register namespace function", e);
            }
            context.register(namespace.getNodeValue(), clazz);
        }
    }

    /*
        <package name="com.github.jpingus"/>
    */
    private void analysePackage(Node item, AggregatorContext context) {
        Node name = item.getAttributes().getNamedItem(NAME);
        if (name != null)
            context.setPackageStart(name.getNodeValue());
    }

    /*
        <class name="com.github.jpingus.Business">
            <execute field="total" jexl="sum('total')"/>
            <collect field="ccm2" to="All my ccm2 ids" />
            <variable name="ccm2" field="ccm2"/>
        </class>
    */
    private void analyseClass(Node item, AggregatorContext context) {
        Node className = item.getAttributes().getNamedItem(NAME);
        if (className != null) {
            Class clazz = null;
            try {
                clazz = this.getClass().forName(className.getNodeValue());
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot analyse class", e);
            }
            Analysed analysed = new Analysed();
            Node classContext = item.getAttributes().getNamedItem("context");
            if (classContext != null)
                analysed.classContext = classContext.getNodeValue();
            NodeList level1 = item.getChildNodes();
            for (int i = 0; i < level1.getLength(); i++) {
                analyseClassConfig(level1.item(i), analysed);
            }
            context.analyse(clazz, analysed);
        }
    }

    /*
        <class name="com.github.jpingus.Business">
            <execute field="total" jexl="sum('total')"/>
            <collect field="ccm2" to="All my ccm2 ids" />
            <variable name="ccm2" field="ccm2"/>
        </class>
    */
    private void analyseClassConfig(Node item, Analysed analysed) {
        if (item.getNodeType() == Node.ELEMENT_NODE) {
            Node field = item.getAttributes().getNamedItem(FIELD);
            Node when = item.getAttributes().getNamedItem(WHEN);
            if (EXECUTE.equals(item.getNodeName())) {
                Node jexl = item.getAttributes().getNamedItem(JEXL);
                if (field != null && jexl != null)
                    analysed.addExecute(field.getNodeValue(), jexl.getNodeValue(), when == null ? EMPTY : when.getNodeValue());
            } else if (COLLECT.equals(item.getNodeName())) {
                Node to = item.getAttributes().getNamedItem(TO);
                Node what = item.getAttributes().getNamedItem(WHAT);
                if (field != null && to != null)
                    analysed.addCollectField(field.getNodeValue(), to.getNodeValue(), when == null ? EMPTY : when.getNodeValue());
                if (what != null && to != null)
                    analysed.addCollectClass(what.getNodeValue(), to.getNodeValue(), when == null ? EMPTY : when.getNodeValue());
            } else if (VARIABLE.equals(item.getNodeName())) {
                Node name = item.getAttributes().getNamedItem(NAME);
                if (field != null && name != null)
                    analysed.addVariable(field.getNodeValue(), name.getNodeValue());
            }
        }
    }

}

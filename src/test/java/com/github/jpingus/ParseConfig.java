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
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document docConfig =
                builder.parse(ParseConfig.class.getResourceAsStream("/config.xml"));
        AggregatorContext context = analyse(docConfig);
        Assert.assertEquals(new Analysed(Business.class,"com.github.jpingus"),context.getAnalysed(Business.class));
    }

    private AggregatorContext analyse(Document docConfig) {
        Element root = docConfig.getDocumentElement();
        if (!"aggregator4j".equals(root.getTagName()))
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
        if ("function".equals(item.getNodeName())) {
            analyseFunction(item, context);
        } else if ("package".equals(item.getNodeName())) {
            analysePackage(item, context);
        } else if ("class".equals(item.getNodeName())) {
            analyseClass(item, context);
        }
    }

    /*
        <function namespace="my" registerClass="com.github.jpingus.Functions"/>
    */
    private void analyseFunction(Node item, AggregatorContext context) {
        Node namespace = item.getAttributes().getNamedItem("namespace");
        Node registerClass = item.getAttributes().getNamedItem("registerClass");
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
        Node name = item.getAttributes().getNamedItem("name");
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
        Node className = item.getAttributes().getNamedItem("name");
        if (className != null) {
            Class clazz = null;
            try {
                clazz = this.getClass().forName(className.getNodeValue());
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot register namespace function", e);
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
            Node field = item.getAttributes().getNamedItem("field");
            Node when = item.getAttributes().getNamedItem("when");
            if ("execute".equals(item.getNodeName())) {
                Node jexl = item.getAttributes().getNamedItem("jexl");
                if (field != null && jexl != null)
                    analysed.addExecute(field.getNodeValue(), jexl.getNodeValue(), when == null ? "" : when.getNodeValue());
            } else if ("collect".equals(item.getNodeName())) {
                Node to = item.getAttributes().getNamedItem("to");
                Node what = item.getAttributes().getNamedItem("what");
                if (field != null && to != null)
                    analysed.addCollectField(field.getNodeValue(), to.getNodeValue(), when == null ? "" : when.getNodeValue());
                if (what != null && to != null)
                    analysed.addCollectClass(what.getNodeValue(), to.getNodeValue(), when == null ? "" : when.getNodeValue());
            } else if ("variable".equals(item.getNodeName())) {
                Node name = item.getAttributes().getNamedItem("name");
                if (field != null && name != null)
                    analysed.addVariable(field.getNodeValue(), name.getNodeValue());
            }
        }
    }

}

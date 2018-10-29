package com.github.jpingus;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigurationFactory {
    private static final Log LOGGER = LogFactory.getLog(ConfigurationFactory.class);
    private static final String FIELD = "field";
    private static final String WHEN = "when";
    private static final String EXECUTE = "execute";
    private static final String JEXL = "jexl";
    private static final String COLLECT = "collect";
    private static final String TO = "to";
    private static final String WHAT = "what";
    private static final String VARIABLE = "variable";
    private static final String NAME = "name";
    private static final String NAMESPACE = "namespace";
    private static final String REGISTER_CLASS = "registerClass";
    private static final String FUNCTION = "function";
    private static final String PACKAGE = "package";
    private static final String CLASS = "class";
    private static final String AGGREGATOR4J = "aggregator4j";
    private static final String CONTEXT = "context";

    private ConfigurationFactory() {
    }

    /**
     * Parse the xml provided in the stream and transform into AggregatorContext
     *
     * @param config inputStream containing XML
     * @return built context
     */
    public static AggregatorContext buildAggregatorContext(InputStream config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document docConfig;
        try {
            builder = factory.newDocumentBuilder();

            docConfig = builder.parse(config);
        } catch (Exception e) {
            throw new Error("Could not parse aggeregator4j config", e);
        }
        return analyse(docConfig);

    }

    public static void extractConfig(AggregatorContext context, OutputStream out) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document docConfig;
        try {
            builder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new Error("Could not parse aggeregator4j config", e);
        }
        docConfig = builder.newDocument();
        Element root = docConfig.createElement(AGGREGATOR4J);
        docConfig.appendChild(root);
        root.appendChild(withAttribute(
                docConfig.createElement(PACKAGE)
                , NAME, context.getPackageStart())
        );
        context.getRegisteredNamespaces().forEach((namespace, clazz) ->
                root.appendChild(withAttribute(withAttribute(
                        docConfig.createElement(FUNCTION)
                        , NAMESPACE, namespace)
                        , REGISTER_CLASS, clazz.getName())));
        context.getAnalysedCache().forEach((clazz, analysed) -> {
            if (analysed.classType == Analysed.CLASS_TYPE.PROCESSABLE) {
                Element classElement = withAttribute(withAttribute(
                        docConfig.createElement(CLASS)
                        , NAME, clazz.getName())
                        , CONTEXT, analysed.classContext);
                root.appendChild(classElement);
                if (analysed.classCollects != null)
                    analysed.classCollects.forEach(collect ->
                            classElement.appendChild(withAttribute(withAttribute(withAttribute(
                                    docConfig.createElement(COLLECT)
                                    , WHAT, collect.what)
                                    , TO, collect.to)
                                    , WHEN, collect.when)
                            ));
                if (analysed.collects != null)
                    analysed.collects.forEach((field, collects) ->
                            collects.forEach(collect ->
                                    classElement.appendChild(withAttribute(withAttribute(withAttribute(
                                            docConfig.createElement(COLLECT)
                                            , FIELD, field)
                                            , TO, collect.to)
                                            , WHEN, collect.when)

                                    )));
                if (analysed.executes != null)
                    analysed.executes.forEach((field, executes) ->
                            executes.forEach(execute ->
                                    classElement.appendChild(withAttribute(withAttribute(withAttribute(
                                            docConfig.createElement(EXECUTE)
                                            , FIELD, field)
                                            , JEXL, execute.jexl)
                                            , WHEN, execute.when)
                                    )));
                if (analysed.variables != null)
                    analysed.variables.forEach((field, variable) ->
                            classElement.appendChild(withAttribute(withAttribute(
                                    docConfig.createElement(VARIABLE)
                                    , FIELD, field)
                                    , NAME, variable)
                            ));
            }
        });
        prettyPrint(docConfig, out);

    }

    private static Element withAttribute(Element element, String attributeName, String attributeValue) {
        if (attributeValue != null)
            element.setAttribute(attributeName, attributeValue);
        return element;
    }

    private static void prettyPrint(Document xml, OutputStream out) {
        Transformer tf = null;
        try {
            tf = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new Error("Cannot create transformer ", e);
        }
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        try {
            tf.transform(new DOMSource(xml), new StreamResult(out));
        } catch (TransformerException e) {
            throw new Error("Could not write config to stream", e);
        }
    }

    private static AggregatorContext analyse(Document docConfig) {
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

    private static void analyse(Node item, AggregatorContext context) {
        if (item.getNodeType() == Node.ELEMENT_NODE) {
            if (FUNCTION.equals(item.getNodeName())) {
                analyseFunction(item, context);
            } else if (PACKAGE.equals(item.getNodeName())) {
                analysePackage(item, context);
            } else if (CLASS.equals(item.getNodeName())) {
                analyseClass(item, context);
            } else {
                LOGGER.warn("Unexpected tag :" + item.getNodeName());
            }
        }
    }

    private static void analyseFunction(Node item, AggregatorContext context) {
        String namespace = getAttribute(item, NAMESPACE);
        String registerClass = getAttribute(item, REGISTER_CLASS);
        if (namespace != null && registerClass != null) {
            Class clazz;
            try {
                clazz = Class.forName(registerClass);
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot register namespace function", e);
            }
            context.register(namespace, clazz);
        } else {
            LOGGER.warn("Either " + REGISTER_CLASS + " '" + registerClass + "' or " + NAMESPACE + " '" + namespace + "' is missing");
        }
    }

    private static void analysePackage(Node item, AggregatorContext context) {
        String name = getAttribute(item, NAME);
        if (name != null)
            context.setPackageStart(name);
        else
            LOGGER.warn(NAME + " attribute missing for " + PACKAGE);
    }

    private static void analyseClass(Node item, AggregatorContext context) {
        String className = getAttribute(item, NAME);
        if (className != null) {
            Class clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot analyse class", e);
            }
            Analysed analysed = new Analysed();
            analysed.classContext = getAttribute(item, CONTEXT);
            NodeList level1 = item.getChildNodes();
            for (int i = 0; i < level1.getLength(); i++) {
                analyseClassConfig(level1.item(i), analysed);
            }
            context.analyse(clazz, analysed);
        } else
            LOGGER.warn(NAME + " attribute missing for " + CLASS);
    }

    private static void analyseClassConfig(Node item, Analysed analysed) {
        if (item.getNodeType() == Node.ELEMENT_NODE) {
            String field = getAttribute(item, FIELD);
            String when = getAttribute(item, WHEN);
            if (EXECUTE.equals(item.getNodeName())) {
                String jexl = getAttribute(item, JEXL);
                if (field != null && jexl != null)
                    analysed.addExecute(field, jexl, when);
                else
                    LOGGER.warn(EXECUTE + " requires " + FIELD + " '" + field + "' and " + JEXL + " '" + jexl + "' to be provided");
            } else if (COLLECT.equals(item.getNodeName())) {
                String to = getAttribute(item, TO);
                String what = getAttribute(item, WHAT);
                if (field != null && to != null)
                    analysed.addCollectField(field, to, when);
                else if (what != null && to != null)
                    analysed.addCollectClass(what, to, when);
                else
                    LOGGER.warn(COLLECT + " requires " + TO + " '" + to + "' and either " + FIELD + " '" + field + "' or " + WHAT + " '" + what + "' to be provided");

            } else if (VARIABLE.equals(item.getNodeName())) {
                String name = getAttribute(item, NAME);
                if (field != null && name != null)
                    analysed.addVariable(field, name);
                else
                    LOGGER.warn(VARIABLE + " requires " + FIELD + " '" + field + "' and " + NAME + " '" + name + "' to be provided");
            }
        }
    }


    private static String getAttribute(Node item, String attributeName) {
        Node attribute = item.getAttributes().getNamedItem(attributeName);
        if (attribute == null) return null;
        return attribute.getNodeValue();
    }

}

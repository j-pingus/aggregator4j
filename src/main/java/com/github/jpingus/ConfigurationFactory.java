package com.github.jpingus;

import com.github.jpingus.model.Class;
import com.github.jpingus.model.Collect;
import com.github.jpingus.model.Execute;
import com.github.jpingus.model.Variable;
import com.github.jpingus.model.*;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.jpingus.StringFunctions.isEmpty;

public class ConfigurationFactory {
    private static final Log LOGGER = LogFactory.getLog(ConfigurationFactory.class);
    private static final String FIELD = "field";
    private static final String WHEN = "when";
    private static final String EXECUTE = "execute";
    private static final String JEXL = "jexl";
    private static final String COLLECT = "collect";
    private static final String PROCESSING = "processing";
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
     * Unmarshall an XML configuration into Aggregator4j model
     *
     * @param config the XML input stream
     * @return parsed object
     */
    public static Aggregator4j unMarshall(InputStream config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document docConfig;
        try {
            builder = factory.newDocumentBuilder();

            docConfig = builder.parse(config);
        } catch (Exception e) {
            throw new Error("Could not parse aggeregator4j config", e);
        }
        Aggregator4j aggregator4j = new Aggregator4j();
        Element root = docConfig.getDocumentElement();
        if (!AGGREGATOR4J.equals(root.getTagName()))
            throw new Error("config root must be aggregator4j");
        if (root.hasAttribute(PROCESSING)) {
            aggregator4j.setProcessing(root.getAttribute(PROCESSING));
        }
        NodeList level1 = root.getChildNodes();
        for (int i = 0; i < level1.getLength(); i++) {
            if (level1.item(i).getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = level1.item(i).getNodeName();
                if (FUNCTION.equals(nodeName)) {
                    unMarshallFunction(aggregator4j, level1.item(i));
                } else if (PACKAGE.equals(nodeName)) {
                    unMarshallPackage(aggregator4j, level1.item(i));
                } else if (CLASS.equals(nodeName)) {
                    unMarshallClass(aggregator4j, level1.item(i));
                } else {
                    LOGGER.warn("Unexpected tag :" + nodeName);
                }
            }
        }
        return aggregator4j;
    }

    private static boolean unMarshallClass(Aggregator4j aggregator4j, Node item) {
        String className = getAttribute(item, NAME);
        if (!isEmpty(className)) {
            NodeList level1 = item.getChildNodes();
            String classContext = getAttribute(item, CONTEXT);
            Class aClass = new Class(className, classContext);
            for (int i = 0; i < level1.getLength(); i++) {
                unMarshallClassConfig(aClass, level1.item(i));
            }
            return aggregator4j.addClass(aClass);
        } else
            LOGGER.warn(NAME + " attribute missing for " + CLASS);
        return false;
    }

    private static boolean unMarshallClassConfig(Class aClass, Node item) {
        if (item.getNodeType() == Node.ELEMENT_NODE) {
            String field = getAttribute(item, FIELD);
            String when = getAttribute(item, WHEN);
            if (EXECUTE.equals(item.getNodeName())) {
                String jexl = getAttribute(item, JEXL);
                if (!isEmpty(field, jexl))
                    aClass.addExecute(new Execute(field, jexl, when));
                    //analysed.addExecute(field, jexl, when);
                else
                    LOGGER.warn(EXECUTE + " requires " + FIELD + " '" + field + "' and " + JEXL + " '" + jexl + "' to be provided");
            } else if (COLLECT.equals(item.getNodeName())) {
                String to = getAttribute(item, TO);
                String what = getAttribute(item, WHAT);
                if ((!isEmpty(field) || !isEmpty(what)) && !isEmpty(to))
                    aClass.addCollect(new Collect(field, what, to, when));
                else
                    LOGGER.warn(COLLECT + " requires " + TO + " '" + to + "' and either " + FIELD + " '" + field + "' or " + WHAT + " '" + what + "' to be provided");

            } else if (VARIABLE.equals(item.getNodeName())) {
                String name = getAttribute(item, NAME);
                if (!isEmpty(field) && !isEmpty(name))
                    aClass.addVariable(new Variable(field, name));
                    //analysed.addVariable(field, name);
                else
                    LOGGER.warn(VARIABLE + " requires " + FIELD + " '" + field + "' and " + NAME + " '" + name + "' to be provided");
            }
        }
        return false;
    }

    private static void unMarshallPackage(Aggregator4j aggregator4j, Node item) {
        String name = getAttribute(item, NAME);
        if (!isEmpty(name))
            aggregator4j.setAnalysedPackage(name);
        else
            LOGGER.warn(NAME + " attribute missing for " + PACKAGE);
    }

    private static boolean unMarshallFunction(Aggregator4j aggregator4j, Node item) {
        String namespace = getAttribute(item, NAMESPACE);
        String registerClass = getAttribute(item, REGISTER_CLASS);
        if (!isEmpty(namespace) && !isEmpty(registerClass)) {
            return aggregator4j.addFunction(new Function(namespace, registerClass));
        } else {
            LOGGER.warn("Either " + REGISTER_CLASS + " '" + registerClass + "' or " + NAMESPACE + " '" + namespace + "' is missing");
        }
        return false;
    }

    public static Aggregator4j extractConfig(AggregatorContext context) {
        Aggregator4j config = new Aggregator4j();
        config.setAnalysedPackage(context.getPackageStart());
        if (context.getProcessing() != null)
            config.setProcessing(context.getProcessing().getClass().getName());
        config.setFunctionList(extractFunctions(context));
        config.setClassList(context.getAnalysedCache().entrySet().stream()
                .filter(ConfigurationFactory::isProcessable)
                .map(ConfigurationFactory::toClass)
                .collect(Collectors.toList()));
        return config;
    }


    private static Class toClass(Map.Entry<java.lang.Class, Analysed> classAnalysedEntry) {
        Class ret = new Class(classAnalysedEntry.getKey().getName(), classAnalysedEntry.getValue().classContext);
        ret.setCollectList(
                Stream.concat(
                        Optional.ofNullable(
                                classAnalysedEntry.getValue().classCollects).orElse(Collections.emptyList())
                                .stream(),
                        Optional.ofNullable(
                                classAnalysedEntry.getValue().collects.values()).orElse(Collections.emptyList())
                                .stream()
                                .flatMap(list -> list.stream()))
                        .collect(Collectors.toList()));

        ret.setExecuteList(
                Optional.ofNullable(classAnalysedEntry.getValue().executes)
                        .orElse(Collections.emptyMap())
                        .values().stream()
                        .flatMap(list -> list.stream())
                        .collect(Collectors.toList())
        );
        ret.setVariableList(
                classAnalysedEntry.getValue().variables.entrySet().stream()
                        .map(variable -> new Variable(variable.getKey(), variable.getValue()))
                        .collect(Collectors.toList())
        );
        return ret;
    }

    private static boolean isProcessable(Map.Entry<java.lang.Class, Analysed> classAnalysedEntry) {
        return classAnalysedEntry.getValue().classType == Analysed.CLASS_TYPE.PROCESSABLE;
    }

    private static List<Function> extractFunctions(AggregatorContext context) {
        List<Function> ret = new ArrayList<>();
        if (context.getRegisteredNamespaces() != null)
            context.getRegisteredNamespaces().forEach((name, clazz) ->
                    ret.add(new Function(name, clazz.getName()))
            );
        return ret;
    }

    public static void marshall(AggregatorContext context, OutputStream out) {
        marshall(extractConfig(context), out);
    }

    private static Element withAttribute(Element element, String attributeName, String attributeValue) {
        if (!isEmpty(attributeValue))
            element.setAttribute(attributeName, attributeValue);
        return element;
    }

    /**
     * Transform a configuratin in XML output stream;
     *
     * @param config the config to marshall
     * @param out    the outputStream to marshall to.
     */
    public static void marshall(Aggregator4j config, OutputStream out) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document docConfig;
        try {
            builder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new Error("Could not build new Document", e);
        }
        docConfig = builder.newDocument();
        Element root = docConfig.createElement(AGGREGATOR4J);
        docConfig.appendChild(root);
        root.appendChild(withAttribute(
                docConfig.createElement(PACKAGE)
                , NAME, config.getAnalysedPackage())
        );
        if (config.getProcessing() != null)
            withAttribute(root, PROCESSING, config.getProcessing().getClass().getName());
        config.getFunctionList().forEach((function) ->
                root.appendChild(withAttribute(withAttribute(
                        docConfig.createElement(FUNCTION)
                        , NAMESPACE, function.getNamespace())
                        , REGISTER_CLASS, function.getRegisterClass())));
        config.getClassList().forEach(clazz -> {
            Element classElement = withAttribute(withAttribute(
                    docConfig.createElement(CLASS)
                    , NAME, clazz.getClassName())
                    , CONTEXT, clazz.getClassContext());
            root.appendChild(classElement);
            clazz.getCollectList().forEach(collect -> {
                classElement.appendChild(withAttribute(withAttribute(withAttribute(withAttribute(
                        docConfig.createElement(COLLECT)
                        , FIELD, collect.getField())
                        , WHAT, collect.getWhat())
                        , TO, collect.getTo())
                        , WHEN, collect.getWhen()));
            });
            clazz.getExecuteList().forEach(execute -> {
                classElement.appendChild(withAttribute(withAttribute(withAttribute(
                        docConfig.createElement(EXECUTE)
                        , FIELD, execute.getField())
                        , JEXL, execute.getJexl())
                        , WHEN, execute.getWhen()));
            });
            clazz.getVariableList().forEach(variable -> {
                classElement.appendChild(withAttribute(withAttribute(
                        docConfig.createElement(VARIABLE)
                        , FIELD, variable.getField())
                        , NAME, variable.getVariable()));
            });

            prettyPrint(docConfig, out);

        });
    }

    private static void prettyPrint(Document xml, OutputStream out) {
        Transformer tf;
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

    private static String getAttribute(Node item, String attributeName) {
        Node attribute = item.getAttributes().getNamedItem(attributeName);
        if (attribute == null) return null;
        return attribute.getNodeValue();
    }

}

package com.github.jpingus;

import com.github.jpingus.model.Aggregator4j;
import com.github.jpingus.model.Class;
import com.github.jpingus.model.Collect;
import com.github.jpingus.model.Execute;
import com.github.jpingus.model.Function;
import com.github.jpingus.model.Variable;
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

import static com.github.jpingus.StringFunctions.isEmpty;

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
        Aggregator4j configModel;
        try {
            builder = factory.newDocumentBuilder();

            docConfig = builder.parse(config);
        } catch (Exception e) {
            throw new Error("Could not parse aggeregator4j config", e);
        }
        configModel = unMarshall(docConfig);
        return buildAggregatorContext(configModel);

    }

    private static Aggregator4j unMarshall(Document docConfig) {
        Aggregator4j aggregator4j = new Aggregator4j();
        Element root = docConfig.getDocumentElement();
        if (!AGGREGATOR4J.equals(root.getTagName()))
            throw new Error("config root must be aggregator4j");
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
                if (!isEmpty(field,jexl) )
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
                                    , WHAT, collect.getWhat())
                                    , TO, collect.getTo())
                                    , WHEN, collect.getWhen())
                            ));
                if (analysed.collects != null)
                    analysed.collects.forEach((field, collects) ->
                            collects.forEach(collect ->
                                    classElement.appendChild(withAttribute(withAttribute(withAttribute(
                                            docConfig.createElement(COLLECT)
                                            , FIELD, field)
                                            , TO, collect.getTo())
                                            , WHEN, collect.getWhen())

                                    )));
                if (analysed.executes != null)
                    analysed.executes.forEach((field, executes) ->
                            executes.forEach(execute ->
                                    classElement.appendChild(withAttribute(withAttribute(withAttribute(
                                            docConfig.createElement(EXECUTE)
                                            , FIELD, field)
                                            , JEXL, execute.getJexl())
                                            , WHEN, execute.getWhen())
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
        if (!isEmpty(attributeValue))
            element.setAttribute(attributeName, attributeValue);
        return element;
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

    public static AggregatorContext buildAggregatorContext(Aggregator4j docConfig) {
        return buildAggregatorContext(docConfig, null);
    }

    public static AggregatorContext buildAggregatorContext(Aggregator4j docConfig, ClassLoader loader) {
        AggregatorContext context = new AggregatorContext(true);
        if (loader != null)
            context.setClassLoader(loader);
        analysePackage(context, docConfig);
        analyseFunction(context, docConfig);
        analyseClass(context, docConfig);
        return context;
    }

    private static void analyseFunction(AggregatorContext context, Aggregator4j config) {
        for (Function function : config.getFunctionList()) {
            java.lang.Class clazz;
            try {
                clazz = context.loadClass(function.getRegisterClass());
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot register namespace function" + function.getRegisterClass(), e);
            }
            context.register(function.getNamespace(), clazz);

        }
    }

    private static void analysePackage(AggregatorContext context, Aggregator4j config) {
        context.setPackageStart(config.getAnalysedPackage());
    }

    private static void analyseClass(AggregatorContext context, Aggregator4j config) {
        for (Class clazzConfig : config.getClassList()) {
            java.lang.Class clazz;

            try {
                clazz = context.loadClass(clazzConfig.getClassName());
            } catch (ClassNotFoundException e) {
                throw new Error("Cannot analyse class:" + clazzConfig.getClassName(), e);
            }
            Analysed analysed = new Analysed();
            analysed.classContext = clazzConfig.getClassContext();
            analyseClassConfig(clazzConfig, analysed);
            analysed.classType = Analysed.CLASS_TYPE.PROCESSABLE;
            analysed.addOtherFields(clazz);
            analysed.prune();

            context.cacheAndValidate(clazz, analysed);
        }
    }

    private static void analyseClassConfig(Class clazzConfig, Analysed analysed) {
        clazzConfig.getExecuteList()
                .forEach(e -> analysed.addExecute(e.getField(), e.getJexl(), e.getWhen()));
        clazzConfig.getCollectList()
                .stream()
                .filter(collect -> !isEmpty(collect.getField()))
                .forEach(c -> analysed.addCollectField(c.getField(), c.getTo(), c.getWhen()));
        clazzConfig.getCollectList()
                .stream()
                .filter(collect -> !isEmpty(collect.getWhat()))
                .forEach(c -> analysed.addCollectClass(c.getWhat(), c.getTo(), c.getWhen()));
        clazzConfig.getVariableList()
                .forEach(variable -> analysed.addVariable(variable.getField(), variable.getVariable()));
    }


    private static String getAttribute(Node item, String attributeName) {
        Node attribute = item.getAttributes().getNamedItem(attributeName);
        if (attribute == null) return null;
        return attribute.getNodeValue();
    }

}

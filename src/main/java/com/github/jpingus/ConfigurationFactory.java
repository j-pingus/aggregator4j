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
import java.util.function.Predicate;
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
     * Unmarshall an XML configuration into AggregatorConfiguration model
     *
     * @param config the XML input stream
     * @return parsed object
     */
    public static AggregatorConfiguration unMarshall(InputStream config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document docConfig;
        try {
            builder = factory.newDocumentBuilder();

            docConfig = builder.parse(config);
        } catch (Exception e) {
            throw new Error("Could not parse aggeregator4j config", e);
        }
        AggregatorConfiguration aggregatorConfig = new AggregatorConfiguration();
        Element root = docConfig.getDocumentElement();
        if (!AGGREGATOR4J.equals(root.getTagName()))
            throw new Error("config root must be aggregator4j");
        NodeList level1 = root.getChildNodes();
        for (int i = 0; i < level1.getLength(); i++) {
            if (level1.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node item = level1.item(i);
                String nodeName = item.getNodeName();
                if (PROCESSING.equals(nodeName)) {
                    unMarshallProcessing(aggregatorConfig, item);
                } else if (FUNCTION.equals(nodeName)) {
                    unMarshallFunction(aggregatorConfig, item);
                } else if (PACKAGE.equals(nodeName)) {
                    unMarshallPackage(aggregatorConfig, level1.item(i));
                } else if (CLASS.equals(nodeName)) {
                    unMarshallClass(aggregatorConfig, level1.item(i));
                } else {
                    LOGGER.warn("Unexpected tag :" + nodeName);
                }
            }
        }
        return aggregatorConfig;
    }

    private static void unMarshallProcessing(AggregatorConfiguration config, Node item) {
        String className = getAttribute(item, CLASS);
        if (!StringFunctions.isEmpty(className)) {
            if (config.getProcessings() == null) {
                config.setProcessing(className);
            } else {
                config.getProcessings().add(className);
            }
        }
    }

    private static boolean unMarshallClass(AggregatorConfiguration config, Node item) {
        String className = getAttribute(item, NAME);
        if (!StringFunctions.isEmpty(className)) {
            NodeList level1 = item.getChildNodes();
            String classContext = getAttribute(item, CONTEXT);
            Class aClass = new Class(className, classContext);
            for (int i = 0; i < level1.getLength(); i++) {
                unMarshallClassConfig(aClass, level1.item(i));
            }
            return config.addClass(aClass);
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
                if (!StringFunctions.isEmpty(field, jexl))
                    aClass.addExecute(new Execute(field, jexl, when));
                    //analysed.addExecute(field, jexl, when);
                else
                    LOGGER.warn(EXECUTE + " requires " + FIELD + " '" + field + "' and " + JEXL + " '" + jexl + "' to be provided");
            } else if (COLLECT.equals(item.getNodeName())) {
                String to = getAttribute(item, TO);
                String what = getAttribute(item, WHAT);
                if ((!StringFunctions.isEmpty(field) || !StringFunctions.isEmpty(what)) && !StringFunctions.isEmpty(to))
                    aClass.addCollect(new Collect(field, what, to, when));
                else
                    LOGGER.warn(COLLECT + " requires " + TO + " '" + to + "' and either " + FIELD + " '" + field + "' or " + WHAT + " '" + what + "' to be provided");

            } else if (VARIABLE.equals(item.getNodeName())) {
                String name = getAttribute(item, NAME);
                if (!StringFunctions.isEmpty(field) && !StringFunctions.isEmpty(name))
                    aClass.addVariable(new Variable(field, name));
                    //analysed.addVariable(field, name);
                else
                    LOGGER.warn(VARIABLE + " requires " + FIELD + " '" + field + "' and " + NAME + " '" + name + "' to be provided");
            }
        }
        return false;
    }

    private static void unMarshallPackage(AggregatorConfiguration config, Node item) {
        String name = getAttribute(item, NAME);
        if (!StringFunctions.isEmpty(name))
            if (config.getAnalysedPackages() == null) {
                config.setAnalysedPackage(name);
            } else {
                config.getAnalysedPackages().add(name);
            }
        else
            LOGGER.warn(NAME + " attribute missing for " + PACKAGE);
    }

    private static boolean unMarshallFunction(AggregatorConfiguration config, Node item) {
        String namespace = getAttribute(item, NAMESPACE);
        String registerClass = getAttribute(item, REGISTER_CLASS);
        if (!StringFunctions.isEmpty(namespace) && !StringFunctions.isEmpty(registerClass)) {
            return config.addFunction(new Function(namespace, registerClass));
        } else {
            LOGGER.warn("Either " + REGISTER_CLASS + " '" + registerClass + "' or " + NAMESPACE + " '" + namespace + "' is missing");
        }
        return false;
    }

    /**
     * This method will return a new configuration object which is the result of merge of two configurations.
     * In case of conflicts (i.e. same class defined twice) the main config will patched by the included resulting
     * possibly in data loss.  In those cases a warning will be issued in the log.
     *
     * @param main     the main config to merge
     * @param included the config to include (and overwrite) in merge
     * @return the merged configuration
     */
    public static AggregatorConfiguration merge(AggregatorConfiguration main, AggregatorConfiguration included) {
        AggregatorConfiguration merged = new AggregatorConfiguration();
        merged.setAnalysedPackages(mergeList(main.getAnalysedPackages(), included.getAnalysedPackages()));
        merged.setProcessings(mergeList(main.getProcessings(), included.getProcessings()));
        merged.setFunctionList(mergeFunctionList(main.getFunctionList(), included.getFunctionList()));
        merged.setClassList(mergeClassList(main.getClassList(), included.getClassList()));
        return merged;
    }

    private static List<Class> mergeClassList(List<Class> main, List<Class> included) {
        Map<String, Class> merged = main.stream().collect(
            Collectors.toMap(Class::getClassName, (c) -> c)
        );
        included.forEach(
            aClass -> {
                if (merged.containsKey(aClass.getClassName())) {
                    LOGGER.warn("Overwriting class '" + aClass.getClassName() + "' configuration ");
                }
                merged.put(aClass.getClassName(), aClass);
            }
        );
        return new ArrayList<>(merged.values());
    }

    private static List<Function> mergeFunctionList(List<Function> main, List<Function> included) {
        Map<String, Function> merged = main.stream().collect(
            Collectors.toMap(Function::getNamespace, (f) -> f));
        included.forEach(function -> {
            if (merged.containsKey(function.getNamespace())) {
                LOGGER.warn("Overwriting function namespace :'" + function.getNamespace() +
                    "' with class:'" + function.getRegisterClass() + "'");
            }
            merged.put(function.getNamespace(), function);
        });
        return new ArrayList<>(merged.values());
    }


    private static <C> List<C> mergeList(List<C> main, List<C> included) {
        List<C> merged = new ArrayList<>();
        if (main != null)
            merged.addAll(main);

        if (included != null) {
            merged.removeAll(included);
            merged.addAll(included);
        }
        return new ArrayList<>(merged);

    }

    public static AggregatorConfiguration extractConfig(AggregatorContext context) {
        AggregatorConfiguration config = new AggregatorConfiguration();
        config.setAnalysedPackages(context.getPackageStarts());
        if (context.getProcessings() != null)
            config.setProcessings(
                context.getProcessings().stream()
                    .map(c->c.getClass().getName())
                    .collect(Collectors.toList())
            );
        config.setFunctionList(extractFunctions(context));
        config.setClassList(context.getAnalysedCache().entrySet().stream()
            .filter(ConfigurationFactory::isProcessable)
            .map(ConfigurationFactory::toClass)
            .collect(Collectors.toList()));
        return config;
    }


    private static Class toClass(Map.Entry<java.lang.Class, Analysed> classAnalysedEntry) {
        Class ret = new Class(classAnalysedEntry.getKey().getName(), classAnalysedEntry.getValue().getClassContext());
        ret.setCollectList(
                Stream.concat(
                        Optional.ofNullable(
                                classAnalysedEntry.getValue().getClassCollects()).orElse(Collections.emptyList())
                                .stream(),
                        Optional.of(
                                classAnalysedEntry.getValue().getCollects().values()).orElse(Collections.emptyList())
                                .stream()
                                .flatMap(Collection::stream))
                        .collect(Collectors.toList()));

        ret.setExecuteList(
                Optional.ofNullable(classAnalysedEntry.getValue().getExecutes())
                        .orElse(Collections.emptyMap())
                        .values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );
        ret.setVariableList(
                classAnalysedEntry.getValue().getVariables().entrySet().stream()
                        .map(variable -> new Variable(variable.getKey(), variable.getValue()))
                        .collect(Collectors.toList())
        );
        return ret;
    }

    private static boolean isProcessable(Map.Entry<java.lang.Class, Analysed> classAnalysedEntry) {
        return classAnalysedEntry.getValue().getClassType() == Analysed.CLASS_TYPE.PROCESSABLE;
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
        if (!StringFunctions.isEmpty(attributeValue))
            element.setAttribute(attributeName, attributeValue);
        return element;
    }

    /**
     * Transform a configuratin in XML output stream;
     *
     * @param config the config to marshall
     * @param out    the outputStream to marshall to.
     */
    public static void marshall(AggregatorConfiguration config, OutputStream out) {
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
        for (String analysedPackage : config.getAnalysedPackages()) {
            root.appendChild(withAttribute(
                docConfig.createElement(PACKAGE)
                , NAME,
                analysedPackage)
            );
        }
        if (config.getProcessings() != null)
            config.getProcessings().forEach((processing) -> root.appendChild(withAttribute(
                docConfig.createElement(PROCESSING)
                , CLASS, processing)));
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
            clazz.getCollectList().forEach(collect -> classElement.appendChild(withAttribute(withAttribute(withAttribute(withAttribute(
                docConfig.createElement(COLLECT)
                , FIELD, collect.getField())
                , WHAT, collect.getWhat())
                , TO, collect.getTo())
                , WHEN, collect.getWhen())));
            clazz.getExecuteList().forEach(execute -> classElement.appendChild(withAttribute(withAttribute(withAttribute(
                docConfig.createElement(EXECUTE)
                , FIELD, execute.getField())
                , JEXL, execute.getJexl())
                , WHEN, execute.getWhen())));
            clazz.getVariableList().forEach(variable -> classElement.appendChild(withAttribute(withAttribute(
                docConfig.createElement(VARIABLE)
                , FIELD, variable.getField())
                , NAME, variable.getVariable())));

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

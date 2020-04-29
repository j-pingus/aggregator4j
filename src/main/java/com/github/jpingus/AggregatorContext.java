package com.github.jpingus;

import com.github.jpingus.model.ProcessTrace;
import org.apache.commons.jexl3.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
    private static final int SIZE_MAX = 2000;
    private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
    private final Map<Class, Analysed> analysedCache = new HashMap<>();
    private final JexlEngine jexl;
    private final Map<String, Class> registeredNamespaces;
    private final JexlContext localContext;
    private final Map<String, Aggregator> aggregators;
    private AggregatorProcessing processing;
    private ProcessTrace processTrace;
    private final boolean debug;
    private final int sizeMax = SIZE_MAX;
    private String packageStart;
    private ClassLoader classLoader = null;
    /**
     * Constructor :-)
     */
    AggregatorContext() {
        this(true);
    }
    AggregatorContext(boolean debug) {
        this.jexl = new JexlBuilder().create();
        this.localContext = new MapContext();
        this.aggregators = new HashMap<>();
        this.registeredNamespaces = new HashMap<>();
        this.processTrace = new ProcessTrace();
        this.debug = debug;
        this.packageStart = null;
    }

    public AggregatorProcessing getProcessing() {
        return processing;
    }

    public void setProcessing(AggregatorProcessing processing) {
        this.processing = processing;
    }

    public ProcessTrace getProcessTrace() {
        return processTrace;
    }

    public void setProcessTrace(ProcessTrace processTrace) {
        this.processTrace = processTrace;
    }

    /**
     * Change the default classloader for this context
     *
     * @param classLoader the classLoader to be used for analysis
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    Map<String, Class> getRegisteredNamespaces() {
        return registeredNamespaces;
    }

    Map<Class, Analysed> getAnalysedCache() {
        return analysedCache;
    }

    /**
     * Computes an identifier by concatenating prexif, "." and suffix
     * avoids null exception when computing ids
     * if null is passed the string "null" is concatenated in place of the object
     *
     * @param prefix the prefix (can be null)
     * @param suffix the suffix (can be null)
     * @return concatenation like described
     */
    public String id(Object prefix, Object suffix) {
        return prefix + "." + suffix;
    }

    /**
     * Process an object through this aggregator
     *
     * @param object the object to process
     * @param <T>    the type of object to process deduced from Object class
     * @return a processed object
     */
    public <T> T process(T object) {
        Processor.process(object, object.getClass().getSimpleName(), this);
        return object;
    }

    /**
     * Register an object (or a class) to a namespace. Future call to the evaluator
     * function will benefit from all public methods as functions in that namespace
     *
     * @param namespace the string to prefix the functions
     * @param clazz     the class containing functions to register
     */
    public void register(String namespace, Class clazz) {
        registeredNamespaces.put(namespace, clazz);
    }

    public Object execute(String field, String formula) {
        ProcessTrace current = processTrace;
        try {
            StringBuilder expression = new StringBuilder(field).append("=").append(formula);
            if (debug) {
                processTrace = processTrace.traceExecute(field, formula);
                LOGGER.debug("Execute:" + expression);
            }
            return evaluate(expression.toString());
        } finally {
            processTrace = current;
        }
    }

    /**
     * Evaluate the expression against the context, additionally to the JEXL syntax
     * you can use - sum - avg - count - join see methods with same name in this
     * class for more information
     *
     * @param expression expression to evaluate
     * @return evaluated expression
     */
    public Object evaluate(String expression) {
        try {
            this.set("$__context__", this);
            return jexl.createExpression(expression).evaluate(this);
        } catch (JexlException e) {
            error("Could not evaluate expression '" + expression + "'", e);
            return null;
        }
    }

    /**
     * Joins all objects that have been collected in an aggregator into a string
     * separated by separator
     *
     * @param separator  the String to use as separator
     * @param aggregator the aggregator to join
     * @return a String with all aggregator's element joined by sperator or else an empty string if aggregator not found
     */
    public Object join(String separator, String aggregator) {
        return aggregate(aggregator, "join", false, a -> a.join("+'" + separator + "'+"), "");
    }

    /**
     * Count how many objects have been collected in an aggregator
     *
     * @param aggregator the aggregator to count elements for
     * @return the number of elements in aggregator or else 0
     */
    public Integer count(String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            int ret = aggregators.get(aggregator).count();
            if (debug) {
                LOGGER.debug("count of " + aggregator + "=" + ret);
            }
            return ret;
        } else {
            warning("Could not find aggregator with name '" + aggregator + "'");
        }
        return 0;
    }

    /**
     * if Object was aggregated return true.
     *
     * @param aggregator the aggregator to evaluate
     * @param object     the object to search reference for
     * @return true if object exists in aggregator or else false
     */
    public Boolean contains(String aggregator, Object object) {
        return asSet(aggregator).contains(object);
    }

    /**
     * Sum all objects collected in an aggregator (may be problematic if JEXL cannot
     * sum those objects with "+" operand)
     *
     * @param aggregator the aggregator to evaluate
     * @param orElse     the default value to return in case not found or no elements in aggregator
     * @return the total of all elements in element's type if not found or no elements
     */
    public <T> T sum(String aggregator, T orElse) {
        return (T) aggregate(aggregator, "sum", true, a -> a.join("+"), orElse);
    }

    /**
     * Sum all objects collected in an aggregator (may be problematic if JEXL cannot
     * sum those objects with "+" operand)
     *
     * @param aggregator the aggregator
     * @return the sum or null if not found or empty
     */
    public Object sum(String aggregator) {
        return aggregate(aggregator, "sum", true, a -> a.join("+"), null);
    }

    /**
     * does sum/count for an aggregator, result is floating point (usually double)
     *
     * @param aggregator the aggregator
     * @return the average or 0.0d if not found or empty
     */
    public Object avg(final String aggregator) {
        int count = count(aggregator);
        if (count > 0) {
            sum(aggregator);
            return evaluate("$sum/" + count(aggregator) + ".0");
        }
        return 0.0d;
    }

    /**
     * return aggregated values As Array
     *
     * @param aggregator the aggregator
     * @return an array (may be empty)
     */
    public <T> T[] asArray(String aggregator) {
        Object ret = aggregate(aggregator, "asArray", false, a -> "[" + a.join(",") + "]", new Object[]{});
        if (ret instanceof int[]) {
            Integer[] ret2 = new Integer[((int[]) ret).length];
            int idx = 0;
            for (int i : ((int[]) ret)) ret2[idx] = ((int[]) ret)[idx++];
            return (T[])ret2;
        }
        if (ret instanceof double[]) {
            Double[] ret2 = new Double[((double[]) ret).length];
            int idx = 0;
            for (double i : ((double[]) ret)) ret2[idx] = ((double[]) ret)[idx++];
            return (T[])ret2;
        }
        return (T[]) ret;
    }

    /**
     * return aggregated values as Set
     *
     * @param aggregator the aggregator
     * @return the distinct set of elements (may be empty)
     */
    public <T> Set<T> asSet(String aggregator) {
        @SuppressWarnings("unchecked")
        Set<T> ret = (Set<T>) aggregate(aggregator, "asSet", false, a -> "{" + a.join(",") + "}", new HashSet<>());
        return ret;
    }

    /**
     * all aggregators in this context
     *
     * @return a list of aggregator names
     */
    public Set<String> aggregators() {
        return Collections.unmodifiableSet(aggregators.keySet());
    }

    protected Object aggregate(String aggregator, String name, boolean canSplit, AggregatorJEXLBuilder expressionBuilder, Object orElse) {
        Object ret = null;
        if (aggregators.containsKey(aggregator)) {
            Aggregator a = aggregators.get(aggregator);
            if (a.formulas.size() == 0)
                return orElse;
            if (canSplit && a.count() > sizeMax) {
                boolean first = true;
                for (Aggregator b : a.split(sizeMax)) {
                    String expression = "$" + name + (first ? "=" : "+=") + expressionBuilder.buildExpression(b);
                    first = false;
                    ret = evaluate(expression);
                }
            } else {
                String expression = "$" + name + "=" + expressionBuilder.buildExpression(a);
                ret = evaluate(expression);
            }
            if (debug)
                LOGGER.debug(name + " of " + aggregator + "=" + ret);
            return ret;
        } else {
            warning("Could not find aggregator with name '" + aggregator + "' use value:'" + orElse + "'");
        }
        return orElse;

    }

    /**
     * Used by processor to collect object references
     *
     * @param aggregator      aggregator's name to collect
     * @param objectReference the reference to the element to collect
     */
    protected void collect(String aggregator, String objectReference) {
        if (!aggregators.containsKey(aggregator))
            aggregators.put(aggregator, new Aggregator());
        aggregators.get(aggregator).append(objectReference);
        if (debug) {
            processTrace.traceCollect(aggregator, objectReference);
        }
    }

    @Override
    public Object resolveNamespace(String s) {
        Object ret = registeredNamespaces.get(s);
        return ret == null ? this : ret;
    }

    @Override
    public Object get(String s) {
        return localContext.get(s);
    }

    @Override
    public void set(String s, Object o) {
        localContext.set(s, o);
    }

    @Override
    public boolean has(String s) {
        return localContext.has(s);
    }

    /**
     * removes all elements from context starting
     *
     * @param prefix the prefix of all aggregators to clean
     */
    public void cleanContext(String prefix) {
        for (String key : aggregators.keySet()) {
            if (key.startsWith(prefix + ".")) {
                aggregators.get(key).clear();
            }
        }
    }


    public void addVariable(String variable, Object object) {
        // TODO Auto-generated method stub
        localContext.set("$" + variable, object);
        if (debug) {
            LOGGER.debug("got variable $" + variable);
            processTrace.traceVariable(variable, object);
        }
    }

    public String getLastProcessTrace() {
        return processTrace.toString();
    }

    public boolean isDebug() {
        return debug;
    }

    public void analyse(Class clazz, Analysed analysed) {
        analysed.classType = Analysed.CLASS_TYPE.PROCESSABLE;
        analysed.addOtherFields(clazz);
        analysed.prune();
        analysedCache.put(clazz, analysed);
    }

    public String getPackageStart() {
        return packageStart;
    }

    public void setPackageStart(String packageStart) {
        this.packageStart = packageStart;
    }

    public int size() {

        return 0;
    }

    void error(String message) {
        error(message, null);
    }

    void error(String message, Throwable e) {
        LOGGER.error(message);
        if (debug) {
            processTrace.traceError(message);
            if (e != null)
                processTrace.traceError(getRootCause(e));
        }
    }

    private String getRootCause(Throwable e) {
        if (e.getCause() != null)
            return getRootCause(e.getCause());
        return e.getLocalizedMessage();
    }

    private void warning(String message) {
        LOGGER.warn(message);
        if (debug)
            processTrace.traceWarning(message);
    }

    public void cacheAndValidate(Class objectClass, Analysed analysed) {
        if (analysed.executes != null && analysed.collects != null) {
            for (String field : analysed.executes.keySet()) {
                if (analysed.collects.containsKey(field)) {
                    List<com.github.jpingus.model.Execute> executes = analysed.executes.get(field);
                    List<com.github.jpingus.model.Collect> collects = analysed.collects.get(field);
                    if (!executes.isEmpty() && !collects.isEmpty()) {
                        Optional<com.github.jpingus.model.Execute> execute = executes.stream().filter(e -> e.getJexl().equals("null")).findFirst();
                        if (execute.isPresent()) {
                            if (collects.stream().anyMatch(c -> c.getWhen() == null)) {
                                error("Collecting nullable field '" + objectClass.getName() + "." + field
                                        + "' without when condition (suggestion add : when=\"not(" + execute.get().getWhen()
                                        + ")\"");
                            } else {
                                warning("Collecting nullable field '" + field + "' with when condition");
                            }
                        } else {
                            LOGGER.info("Collecting field '" + field + "' and execute may change its value ");
                        }
                    }
                }
            }
        }
        analysedCache.put(objectClass, analysed);
    }

    synchronized Analysed getAnalysed(Class objectClass) {
        if (!analysedCache.containsKey(objectClass)) {
            Analysed analysed = new Analysed(objectClass, this.getPackageStart());
            cacheAndValidate(objectClass, analysed);
        }
        return analysedCache.get(objectClass);
    }

    /**
     * Load class potentially using a differentiated class loader
     *
     * @param name the class to load
     * @return the loaded class
     * @throws ClassNotFoundException when the class cannot be loaded
     */
    protected Class<?> loadClass(String name) throws ClassNotFoundException {
        if (classLoader == null) {
            return this.getClass().getClassLoader().loadClass(name);
        } else {
            return classLoader.loadClass(name);
        }
    }

    void preProcess(Object o) {
        if(processing!=null)
            processing.preProcess(o,this);
    }
    void postProcess(Object o){
        if(processing!=null)
            processing.postProcess(o,this);
    }
    public interface AggregatorJEXLBuilder {
        String buildExpression(Aggregator a);
    }

    /**
     * Inner structure of the aggregator, subject to change...
     */
    private static class Aggregator {
        List<String> formulas;

        private Aggregator() {
            this.formulas = new ArrayList<>();
        }

        public List<Aggregator> split(int size) {
            List<Aggregator> ret = new ArrayList<>();
            for (int start = 0; start < formulas.size(); start += size) {
                int end = Math.min(start + size, formulas.size());
                Aggregator b = new Aggregator();
                b.formulas = formulas.subList(start, end);
                ret.add(b);
            }
            return ret;
        }

        private void append(String formula) {
            this.formulas.add(formula);
        }

        private int count() {
            return formulas.size();
        }

        private String join(String s) {
            return String.join(s, formulas);
        }

        public void clear() {
            formulas.clear();

        }
    }
}

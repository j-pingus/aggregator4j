package com.github.jpingus;

import org.apache.commons.jexl3.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
    private static final int SIZE_MAX = 2000;
    private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
    private final Map<Class, Analysed> analysedCache = new HashMap<>();
    private JexlEngine jexl;
    private Map<String, Class> registeredNamespaces;
    private JexlContext localContext;
    private Map<String, Aggregator> aggregators;
    private StringBuilder processTrace;
    private boolean debug = false;
    private int sizeMax = SIZE_MAX;
    private String packageStart;

    /**
     * Constructor :-)
     */
    public AggregatorContext() {
        this(true);
    }

    Map<String, Class> getRegisteredNamespaces() {
        return registeredNamespaces;
    }

    Map<Class, Analysed> getAnalysedCache() {
        return analysedCache;
    }


    public AggregatorContext(boolean debug) {
        this.jexl = new JexlBuilder().create();
        this.localContext = new MapContext();
        this.aggregators = new HashMap<>();
        this.registeredNamespaces = new HashMap<>();
        this.processTrace = new StringBuilder();
        this.debug = debug;
        this.packageStart = null;
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
        StringBuilder expression = new StringBuilder(field).append("=").append(formula);
        if (debug) {
            processTrace.append("\"execute\":{\"field\":\"").append(field).append("\",\"formula\":\"").append(formula)
                    .append("\"},");
            LOGGER.debug("Execute:" + expression);
        }
        return evaluate(expression.toString());
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
            return jexl.createExpression(expression).evaluate(this);
        } catch (JexlException e) {
            LOGGER.error("Could not evaluate expression '" + expression + "'", e);
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
            LOGGER.warn("Could not find aggregator with name '" + aggregator + "'");
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
    public Object sum(String aggregator, Object orElse) {
        return aggregate(aggregator, "sum", true, a -> a.join("+"), orElse);
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
        return new Double(0.0d);
    }

    /**
     * return aggregated values As Array
     *
     * @param aggregator the aggregator
     * @return an array (may be empty)
     */
    public Object[] asArray(String aggregator) {
        return (Object[]) aggregate(aggregator, "asArray", false, a -> "[" + a.join(",") + "]", new Object[]{});
    }

    /**
     * return aggregated values as Set
     *
     * @param aggregator the aggregator
     * @return the distinct set of elements (may be empty)
     */
    public Set<Object> asSet(String aggregator) {
        @SuppressWarnings("unchecked")
        Set<Object> ret = (Set<Object>) aggregate(aggregator, "asSet", false, a -> "{" + a.join(",") + "}", new HashSet<>());
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
            LOGGER.warn("Could not find aggregator with name '" + aggregator + "'");
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
            LOGGER.debug("Adding to '" + aggregator + "' formula '" + objectReference + "'");
            processTrace.append("\"collect\":{\"aggregator\":\"").append(aggregator).append("\",\"formula\":\"")
                    .append(objectReference).append("\"},");
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

    /**
     * Trace context start
     *
     * @param contextName the context name to trace
     */
    public void startContext(String contextName) {
        if (debug)
            processTrace.append("\"context\":{\"name\":\"").append(contextName).append("\",");
    }

    /**
     * stops trace of context
     *
     * @param value the context name to stop
     */
    public void endContext(String value) {
        if (debug)
            processTrace.append("},");
    }

    public void addVariable(String variable, Object object) {
        // TODO Auto-generated method stub
        localContext.set("$" + variable, object);
        if (debug) {
            LOGGER.debug("got variable $" + variable);
            processTrace.append("\"variable\":{\"name\":\"").append(variable).append("\",\"value\":\"")
                    .append(object == null ? "null" : object.toString()).append("\"},");
        }
    }

    public String getLastProcessTrace() {
        return processTrace.toString();
    }

    public void startProcess() {
        processTrace = new StringBuilder("{");
    }

    public void endProcess() {
        processTrace.append("}");
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

    synchronized Analysed getAnalysed(Class objectClass) {
        if (!analysedCache.containsKey(objectClass)) {
            Analysed analysed = new Analysed(objectClass, this.getPackageStart());
            analysedCache.put(objectClass, analysed);
        }
        return analysedCache.get(objectClass);
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

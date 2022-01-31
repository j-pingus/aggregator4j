package com.github.jpingus;

import com.github.jpingus.model.AggregatorConfiguration;
import com.github.jpingus.model.Class;
import com.github.jpingus.model.ProcessTrace;
import org.apache.commons.jexl3.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.jpingus.StringFunctions.isEmpty;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
    private static final int SIZE_MAX = 2000;
    private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
    public static final String CONTEXT_VARIABLE = "$__context__";
    private final Map<java.lang.Class, Analysed> analysedCache = new HashMap<>();
    private final JexlEngine jexl;
    private final Map<String, java.lang.Class> registeredNamespaces;
    private final JexlContext localContext;
    private final Map<String, Aggregator> aggregators;
    private final boolean debug;
    private final int sizeMax = SIZE_MAX;
    private List<AggregatorProcessing> processings;
    private ProcessTrace processTrace;
    private List<String> packageStarts;
    private ClassLoader classLoader = null;

    private AggregatorContext(boolean debug) {
        this.jexl = new JexlBuilder().create();
        this.localContext = new MapContext();
        this.localContext.set(CONTEXT_VARIABLE, this);
        this.aggregators = new HashMap<>();
        this.registeredNamespaces = new HashMap<>();
        this.processTrace = new ProcessTrace();
        this.debug = debug;
        this.packageStarts = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<AggregatorProcessing> getProcessings() {
        return processings;
    }

    public void setProcessings(List<AggregatorProcessing> processing) {
        this.processings = processing;
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

    Map<String, java.lang.Class> getRegisteredNamespaces() {
        return registeredNamespaces;
    }

    Map<java.lang.Class, Analysed> getAnalysedCache() {
        return analysedCache;
    }

    /**
     * Computes an identifier by concatenating prefix, "." and suffix
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
    public void register(String namespace, java.lang.Class clazz) {
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
            for (int i : ((int[]) ret))
                ret2[idx] = ((int[]) ret)[idx++];
            return (T[]) ret2;
        }
        if (ret instanceof double[]) {
            Double[] ret2 = new Double[((double[]) ret).length];
            int idx = 0;
            for (double i : ((double[]) ret))
                ret2[idx] = ((double[]) ret)[idx++];
            return (T[]) ret2;
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

    protected Object aggregate(String aggregator, String name, boolean canSplit,
                               AggregatorJEXLBuilder expressionBuilder, Object orElse) {
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
            if (debug) {
                String message = name + " of '" + aggregator + "' = " + ret + (ret != null ? ("[" + ret.getClass().getSimpleName() + "]") : "");
                processTrace.traceDebug(message);
                LOGGER.debug(message);
            }
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
     * removes all elements from context starting with prefix
     *
     * @param prefix the prefix of all aggregators to clean
     */
    public void cleanContext(String prefix) {
        for (Map.Entry<String, Aggregator> entry : aggregators.entrySet()) {
            if (entry.getKey().startsWith(prefix + ".")) {
                entry.getValue().clear();
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

    public void analyse(java.lang.Class clazz, Analysed analysed) {
        analysed.setClassType(Analysed.CLASS_TYPE.PROCESSABLE);
        analysed.addOtherFields(clazz);
        analysed.prune();
        analysedCache.put(clazz, analysed);
    }

    public List<String> getPackageStarts() {
        return packageStarts;
    }

    public void setPackageStarts(List<String> packageStarts) {
        this.packageStarts = packageStarts;
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
            if (e != null) {
                String cause = getRootCause(e);
                LOGGER.error(cause);
                processTrace.traceError(cause);
            }
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

    public void cacheAndValidate(java.lang.Class objectClass, Analysed analysed) {
        if (analysed.getExecutes() != null && analysed.getCollects() != null) {
            for (String field : analysed.getExecutes().keySet()) {
                if (analysed.getCollects().containsKey(field)) {
                    List<com.github.jpingus.model.Execute> executes = analysed.getExecutes().get(field);
                    List<com.github.jpingus.model.Collect> collects = analysed.getCollects().get(field);
                    if (!executes.isEmpty() && !collects.isEmpty()) {
                        Optional<com.github.jpingus.model.Execute> execute = executes.stream()
                            .filter(e -> e.getJexl().equals("null")).findFirst();
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

    static IgnorableClassDetector packageDetector(List<String> analysedPackages) {
        return (clazz) -> {
            if (clazz.getPackage() == null) return true;
            String p = clazz.getPackage().getName();
            for (String pStart : analysedPackages) {
                if (p.startsWith(pStart)) return false;
            }
            return true;
        };
    }

    synchronized Analysed getAnalysed(java.lang.Class objectClass) {
        if (!analysedCache.containsKey(objectClass)) {
            Analysed analysed = new Analysed(objectClass, packageDetector(this.getPackageStarts()));
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
    protected java.lang.Class<?> loadClass(String name) throws ClassNotFoundException {
        if (classLoader == null) {
            return this.getClass().getClassLoader().loadClass(name);
        } else {
            return classLoader.loadClass(name);
        }
    }

    void preProcess(Object o) {
        if (processings != null)
            processings.forEach(processing ->
                processing.preProcess(o, this)
            );
    }

    void postProcess(Object o) {
        if (processings != null)
            processings.forEach(processing ->
                processing.postProcess(o, this)
            );
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

    public static class Builder {
        boolean debug;
        ClassLoader classLoader;
        AggregatorConfiguration config;

        private Builder() {
        }

        private static void analyseProcessing(AggregatorContext context, List<String> processings) {
            if (processings == null)
                return;
            context.setProcessings(
                processings.stream()
                    .map(processing -> {
                        try {
                            Object o = context.loadClass(processing).getDeclaredConstructor().newInstance();
                            if (o instanceof AggregatorProcessing) {
                                return (AggregatorProcessing) o;
                            } else {
                                context.error(processing + " do not implement " + AggregatorProcessing.class.getName());
                            }
                        } catch (Throwable e) {
                            context.error("Cannot register processing class " + processing, e);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        private static void analyseFunction(AggregatorContext context, AggregatorConfiguration config) {
            config.getFunctionList().stream()
                .filter(f -> !isEmpty(f.getRegisterClass()) && !isEmpty(f.getNamespace()))
                .forEach(function -> {
                    java.lang.Class clazz;
                    try {
                        clazz = context.loadClass(function.getRegisterClass());
                        context.register(function.getNamespace(), clazz);
                    } catch (ClassNotFoundException e) {
                        context.error("Cannot register namespace function" + function.getRegisterClass(), e);
                    }
                });
        }

        private static void analysePackages(AggregatorContext context, List<String> packagesStart) {
            if (packagesStart != null)
                context.setPackageStarts(packagesStart);
        }

        private static void analyseClass(AggregatorContext context, AggregatorConfiguration config) {
            config.getClassList().stream()
                .filter(c -> !isEmpty(c.getClassName()))
                .forEach(c -> analyseAClass(context, c));
        }

        private static void analyseAClass(AggregatorContext context, Class clazzConfig) {
            java.lang.Class clazz;
            try {
                clazz = context.loadClass(clazzConfig.getClassName());
            } catch (ClassNotFoundException e) {
                context.error("Cannot analyse class:" + clazzConfig.getClassName(), e);
                return;
            }
            Analysed analysed = new Analysed();
            analysed.setClassContext(clazzConfig.getClassContext());
            analyseClassConfig(clazzConfig, analysed);
            analysed.setClassType(Analysed.CLASS_TYPE.PROCESSABLE);
            analysed.addOtherFields(clazz);
            analysed.prune();
            context.cacheAndValidate(clazz, analysed);
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

        /**
         * setup the configuration to be used;
         *
         * @param config configuration model
         * @return the builder
         */
        public Builder config(AggregatorConfiguration config) {
            this.config = config;
            return this;
        }

        /**
         * setup the debug parameter
         *
         * @param debug enables debug mode
         * @return the builder
         */
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * sets the class loader to use
         *
         * @param classLoader a class loader
         * @return the builder
         */
        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Builds a new aggregator context
         *
         * @return AggregatorContext
         */
        public AggregatorContext build() {
            AggregatorContext context = new AggregatorContext(debug);
            if (classLoader != null)
                context.setClassLoader(classLoader);
            if (config != null) {
                analyseProcessing(context, config.getProcessings());
                analysePackages(context, config.getAnalysedPackages());
                analyseFunction(context, config);
                analyseClass(context, config);
            }
            return context;
        }

    }
}

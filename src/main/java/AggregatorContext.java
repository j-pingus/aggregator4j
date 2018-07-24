import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
	private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
	private JexlEngine jexl;
	private Map<String, Object> registeredNamespaces;
	private JexlContext localContext;
	private Map<String, Aggregator> aggregators;
	private StringBuilder processTrace;
	private boolean debug = false;

	/**
	 * Constructor :-)
	 */
	public AggregatorContext() {
		this.jexl = new JexlBuilder().create();
		this.localContext = new MapContext();
		this.aggregators = new HashMap<>();
		this.registeredNamespaces = new HashMap<>();
		this.processTrace = new StringBuilder();
		this.debug = false;
	}

	public AggregatorContext(boolean debug) {
		this();
		this.debug = debug;
	}

	/**
	 * Register an object (or a class) to a namespace. Future call to the evaluator
	 * function will benefit from all public methods as functions in that namespace
	 *
	 * @param namespace
	 * @param o
	 */
	public void register(String namespace, Object o) {
		registeredNamespaces.put(namespace, o);
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
	 * @param expression
	 * @return
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
	 * @param separator
	 * @param aggregator
	 * @return
	 */
	public Object join(String separator, String aggregator) {
		return aggregate(aggregator, "join", a -> a.join("+'" + separator + "'+"));
	}

	/**
	 * Count how many objects have been collected in an aggregator
	 *
	 * @param aggregator
	 * @return
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
		return null;
	}

	/**
	 * if Object was aggregated return true.
	 *
	 * @param aggregator
	 * @param object
	 * @return
	 */
	public Boolean contains(String aggregator, Object object) {
		return asSet(aggregator).contains(object);
	}

	/**
	 * Sum all objects collected in an aggregator (may be problematic if JEXL cannot
	 * sum those objects with "+" operand)
	 *
	 * @param aggregator
	 * @return
	 */
	public Object sum(String aggregator) {
		return aggregate(aggregator, "sum", a -> a.join("+"));
	}

	/**
	 * does sum/count for an aggregator, result is floating point (usually double)
	 *
	 * @param aggregator
	 * @return
	 */
	public Object avg(String aggregator) {
		return aggregate(aggregator, "avg", a -> "(" + a.join("+") + ")/" + a.count() + ".0");
	}

	/**
	 * return aggregated values As Array
	 */
	public Object[] asArray(String aggregator) {
		return (Object[]) aggregate(aggregator, "asArray", a -> "[" + a.join(",") + "]");
	}

	/**
	 * return aggregated values as Set
	 */
	public Set<Object> asSet(String aggregator) {
		@SuppressWarnings("unchecked")
		Set<Object> ret = (Set<Object>) aggregate(aggregator, "asSet", a -> "{" + a.join(",") + "}");
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

	public Object aggregate(String aggregator, String name, AggregatorJEXLBuilder expressionBuilder) {
		if (aggregators.containsKey(aggregator)) {
			Aggregator a = aggregators.get(aggregator);
			String expression = expressionBuilder.buildExpression(a);
			Object ret = evaluate(expression);
			if (debug)
				LOGGER.debug(name + " of " + aggregator + ":" + expression + "=" + ret);
			return ret;
		} else {
			LOGGER.warn("Could not find aggregator with name '" + aggregator + "'");
		}
		return null;

	}

	/**
	 * Used by processor to collect object references
	 *
	 * @param aggregator
	 * @param objectReference
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

	public void cleanContext(String value) {
		for (String key : aggregators.keySet()) {
			if (key.startsWith(value + ".")) {
				aggregators.get(key).clear();
			}
		}
	}

	public void startContext(String contextName) {
		if (debug)
			processTrace.append("\"context\":{\"name\":\"").append(contextName).append("\",");
	}

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
					.append(object==null?"null":object.toString()).append("\"},");
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

}

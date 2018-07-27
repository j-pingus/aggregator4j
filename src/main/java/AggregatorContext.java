import org.apache.commons.jexl3.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
	private static final int SIZE_MAX=2000;
	private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
	private JexlEngine jexl;
	private Map<String, Object> registeredNamespaces;
	private JexlContext localContext;
	private Map<String, Aggregator> aggregators;
	private StringBuilder processTrace;
	private boolean debug = false;
	private int sizeMax = SIZE_MAX;
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
		return aggregate(aggregator, "join", false, a -> a.join("+'" + separator + "'+"));
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
		return aggregate(aggregator, "sum", true, a -> a.join("+"));
	}

	/**
	 * does sum/count for an aggregator, result is floating point (usually double)
	 *
	 * @param aggregator
	 * @return
	 */
	public Object avg(final String aggregator) {
		sum(aggregator);
		return evaluate("$sum/" + count(aggregator) + ".0");
	}

	/**
	 * return aggregated values As Array
	 */
	public Object[] asArray(String aggregator) {
		return (Object[]) aggregate(aggregator, "asArray", false, a -> "[" + a.join(",") + "]");
	}

	/**
	 * return aggregated values as Set
	 */
	public Set<Object> asSet(String aggregator) {
		@SuppressWarnings("unchecked")
		Set<Object> ret = (Set<Object>) aggregate(aggregator, "asSet", false, a -> "{" + a.join(",") + "}");
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
	public Object aggregate(String aggregator, String name, boolean canSplit, AggregatorJEXLBuilder expressionBuilder) {
		Object ret = null;
		if (aggregators.containsKey(aggregator)) {
			Aggregator a = aggregators.get(aggregator);
			if (canSplit && a.count() > sizeMax) {
				boolean first = true;
				for (Aggregator b : a.split(sizeMax)) {
					String expression = "$"+name + (first ? "=" : "+=") + expressionBuilder.buildExpression(b);
					first = false;
					ret = evaluate(expression);
				}
			} else {
				String expression = "$"+name+"="+expressionBuilder.buildExpression(a);
				ret = evaluate(expression);
			}
			if (debug)
				LOGGER.debug(name + " of " + aggregator + "=" + ret);
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

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Processor {
	public static void process(Object o) throws Exception {
		Map<String, Formula> aggregators = new HashMap<String, Formula>();
		JexlContext localContext = new MapContext();
		process("o", o, aggregators, localContext);
		localContext.set("o", o);
		JexlEngine jexl = new JexlBuilder().create();
		for (Formula f : aggregators.values()) {
			System.out.println(f.field + "=" + f.formula);
			jexl.createExpression(f.field + "=" + f.formula).evaluate(localContext);
		}
	}

	private static void process(String prefix, Object o, Map<String, Formula> aggregators, JexlContext localContext)
			throws Exception {
		if (o == null)
			return;
		for (Field f : o.getClass().getDeclaredFields()) {
			Aggregator a = f.getDeclaredAnnotation(Aggregator.class);
			Sum sums[] = f.getDeclaredAnnotationsByType(Sum.class);
			if (a != null) {
				if (!aggregators.containsKey(a.value())) {
					aggregators.put(a.value(), new Formula(null, null));
				}
				Formula formula = aggregators.get(a.value());
				formula.field = prefix + "." + f.getName();
			} else if (sums != null && sums.length > 0) {
				if (f.get(o) != null) {

					String add = prefix + "." + f.getName();
					for (Sum sum : sums) {
						Formula formula = null;
						if (!aggregators.containsKey(sum.value())) {
							aggregators.put(sum.value(), new Formula(null, null));
						}
						formula = aggregators.get(sum.value());
						if (formula.formula == null) {
							formula.formula = add;
						} else {
							formula.formula += "+" + add;
						}
					}
				}
			} else {
				Object fieldValue = f.get(o);
				if (fieldValue != null) {
					Class fieldClass = f.getType();
					if (fieldClass.isArray()) {
						int length = Array.getLength(fieldValue);
						for (int i = 0; i < length; i++) {
							process(prefix + "." + f.getName() + "[" + i + "]", Array.get(fieldValue, i), aggregators,
									localContext);
						}
					} else if (List.class.isAssignableFrom(fieldClass)) {
						@SuppressWarnings("rawtypes")
						List l = (List) fieldValue;
						for (int i = 0; i < l.size(); i++) {
							process(prefix + "." + f.getName() + "[" + i + "]", l.get(i), aggregators, localContext);
						}
					} else if (Iterable.class.isAssignableFrom(fieldClass)) {
						@SuppressWarnings("rawtypes")
						Iterator it = ((Iterable) fieldValue).iterator();
						int i = 0;
						String key = prefix.replaceAll("\\.", "_") + "_";
						while (it.hasNext()) {
							String setKey = key + (i++);
							Object iO = it.next();
							localContext.set(setKey, iO);
							process(setKey, iO, aggregators, localContext);
						}
					} else {
						process(prefix + "." + f.getName(), f.get(o), aggregators, localContext);
					}
				}
			}
		}
	}

	static class Formula {
		String field;
		String formula;

		public Formula(String field, String formula) {
			this.field = field;
			this.formula = formula;
		}
	}
}

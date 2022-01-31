package com.github.jpingus.model;

import java.util.ArrayList;
import java.util.List;

public class ProcessTrace {

    public List<ProcessTrace> children;
    TraceType type = TraceType.ROOT;

    @Override
    public String toString() {
        return "{" + fieldsToString() + "}";
    }

    public String fieldsToString() {
        return "\"type\":\"" + type + '\"' +
                (children != null ? (", \"children\":" + children) : "");
    }

    public ProcessTrace traceContext(String classContext) {
        return add(new ContextTrace(classContext));
    }

    public ProcessTrace traceError(String message) {
        return add(new ErrorTrace(message));
    }

    public ProcessTrace traceWarning(String message) {
        return add(new WarningTrace(message));
    }

    public ProcessTrace traceDebug(String message) {
        return add(new DebugTrace(message));
    }

    public ProcessTrace traceVariable(String name, Object value) {
        return add(new VariableTrace(name, value == null ? "null" : value.toString()));
    }

    public ProcessTrace traceCollect(String aggregator, String reference) {
        return add(new CollectTrace(aggregator, reference));
    }

    public ProcessTrace traceExecute(String field, String formula) {
        return add(new ExecuteTrace(field, formula));
    }

    private ProcessTrace add(ProcessTrace trace) {
        if (children == null)
            children = new ArrayList<>();
        children.add(trace);
        return trace;
    }

    enum TraceType {ROOT, EXECUTE, COLLECT, VARIABLE, CONTEXT, ERROR, WARNING, DEBUG}

    public static class ErrorTrace extends ProcessTrace {
        String message;

        public ErrorTrace() {
            this.type = TraceType.ERROR;
        }

        public ErrorTrace(String message) {
            this();
            this.message = message;
        }

        @Override
        public String toString() {
            return "{" +
                    fieldsToString() +
                    ", \"message\":\"" + message + '\"' +
                    '}';
        }
    }

    public static class WarningTrace extends ProcessTrace {
        String message;

        public WarningTrace() {
            this.type = TraceType.WARNING;
        }

        public WarningTrace(String message) {
            this();
            this.message = message;
        }

        @Override
        public String toString() {
            return "{" +
                fieldsToString() +
                ", \"message\":\"" + message + '\"' +
                '}';
        }
    }


    public static class DebugTrace extends ProcessTrace {
        String debug;

        public DebugTrace() {
            this.type = TraceType.DEBUG;
        }

        public DebugTrace(String debug) {
            this();
            this.debug = debug;
        }

        @Override
        public String toString() {
            return "{" +
                fieldsToString() +
                ", \"debug\":\"" + debug + '\"' +
                '}';
        }
    }

    public static class ContextTrace extends ProcessTrace {
        String name;

        public ContextTrace(String name) {
            this();
            this.name = name;
        }

        public ContextTrace() {
            this.type = TraceType.CONTEXT;
        }

        @Override
        public String toString() {
            return "{" +
                    fieldsToString() +
                    ", \"name\":\"" + name + '\"' +
                    '}';
        }
    }

    public static class CollectTrace extends ProcessTrace {
        String aggregator;
        String reference;

        public CollectTrace() {
            type = TraceType.COLLECT;
        }

        public CollectTrace(String aggregator, String reference) {
            this();
            this.aggregator = aggregator;
            this.reference = reference;
        }

        @Override
        public String toString() {
            return "{" +
                    fieldsToString() +
                    ", \"aggregator\":\"" + aggregator + '\"' +
                    ", \"reference\":\"" + reference + '\"' +
                    '}';
        }
    }

    public static class VariableTrace extends ProcessTrace {
        String variable;
        String value;

        public VariableTrace() {
            this.type = TraceType.VARIABLE;
        }

        public VariableTrace(String variable, String value) {
            this();
            this.variable = variable;
            this.value = value;
        }

        @Override
        public String toString() {
            return "{" +
                    fieldsToString() +
                    ", \"variable\":\"" + variable + '\"' +
                    ", \"value\":\"" + value + '\"' +
                    '}';
        }

    }

    public static class ExecuteTrace extends ProcessTrace {
        String field;
        String formula;

        public ExecuteTrace() {
            type = TraceType.EXECUTE;
        }

        public ExecuteTrace(String field, String formula) {
            this();
            this.field = field;
            this.formula = formula;
        }

        @Override
        public String toString() {
            return "{" +
                    fieldsToString() +
                    ", \"field\":\"" + field + '\"' +
                    ", \"formula\":\"" + formula + '\"' +
                    '}';
        }
    }
}

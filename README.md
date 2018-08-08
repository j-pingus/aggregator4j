Aggregator4j is a simple java library that helps you aggregate values in complex data structures
# Features
* **Annotation based:** you annotate the model, the engine computes all the aggregates
* **API :** you can let the engine work or use it programatically
* **JEXL ++:** Based on [JEXL](http://commons.apache.org/proper/commons-jexl/) plus additional aggregation functions 
* **Extensible :** Write your own functions and register them to be used in the expression
# Example

```java
class Detail{
    @Collect("group.total")
    public int value; 
}
@Collect(value="group.total",what="this.value + this.value2", when="this.value > this.value2")
class Detail2{
    public int value;
    public int value2;
}
@Context("group")
@Collect(value="total",what="this.total")
class Group{
    public Detail[];
    public Detail2[];
    @Execute("sum('group.total')")
    public int total;
    @Execute("avg('group.total')")
    public double average;
    @Execute("count('group.total') * 2")
    public int doubleCountOfDetails;
}
class Business{
    Map<String, Group> groups;
    @Execute("sum('total')")
    public Integer total;
    ...
    void computeTotal(){
        Processor.process(this);
    }
}
```
For more complete examples see the tests in the project.
# Annotations
* **Collect** : annotate the field you will later aggregate.  The field will be retrieved either directly or through getter method. Alternatively you can annotate the class specifying what you want to collect. Arguments:
  * value : the name of the aggregator (or eval: if you want JEXL evaluation of the expresison after eval:)
* **Execute** : preforms the aggregation by resolving the JEXL expression and assigning it to the annotated field.  On top of standard JEXL expression Aggregation methods have been added : 
  * sum : addition all collected elements
  * avg : returns the average
  * count : counts elements 
  * asArray : returns all collected elements as an ArrayList
  * asSet : returns distinct elements in a Set
* **Context** : provides a subcontext to the engine.  The subcontext will be evaluated (all @Execute using a context starting by the context name are processed). Then all aggregators which name starts with the context name are cleared.
* **Variable** : stores the annotated field as a variable in the aggregation context.  This variable can be used in any JEXL expression.  Can be handy to produce dynamic aggregator's name based on a value in another object.
# Licence : MIT
Copyright 2018 Gérald Even

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

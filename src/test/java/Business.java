public class Business {
    public Row[] getElements() {
        return elements;
    }

    public Integer getTotal() {
        return total;
    }

    Row [] elements;

    public Row2[] getOtherElements() {
        return otherElements;
    }

    Row2 [] otherElements;

    public void setElements(Row[] elements) {
        this.elements = elements;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public void setGrandTotal(Integer grandTotal) {
        this.grandTotal = grandTotal;
    }

    public void setAnotherTotal(Integer anotherTotal) {
        this.anotherTotal = anotherTotal;
    }

    @Aggregator("grand total" )
    Integer grandTotal;
    @Aggregator("total")
    Integer total;
    @Aggregator("total2")
    Integer anotherTotal;
}

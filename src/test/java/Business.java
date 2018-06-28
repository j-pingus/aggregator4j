public class Business {
    public Row[] getElements() {
        return elements;
    }

    public Integer getTotal() {
        return total;
    }

    Row [] elements;

    public void setElements(Row[] elements) {
        this.elements = elements;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    @Aggregator("total")
    Integer total;
}

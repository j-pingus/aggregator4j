import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Business {
	@Aggregator("total")
	Integer total;
    public Row[] getElements() {
        return elements;
    }
    public GrandTotal myGrandTotal=new GrandTotal();
    public Integer getTotal() {
        return total;
    }

    Row [] elements;
    public Row2 [] elements2;
    public ArrayList<Row2> elements3;
    public HashSet<Row> elements4;
    public void setElements(Row[] elements) {
        this.elements = elements;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    @Aggregator("total2")
    public Integer total2;
    @Aggregator("All my ccm2 id's")
    public String ccm2;
}
package ctia.hook;

import ctia.Hook;
import java.util.Map;

public class Dummy implements Hook {
    private String name;
    public Dummy(String name){ this.name = name; }
    public void init() { this.name += " - initialized"; }
    public void destroy() { this.name += " - destroyed"; }
    public Map<String,Object> handle(
            String resourceName
            , Map<String,Object> object
            , Map<String,Object> prevObject)
    {
        object.put(this.name, "passed");
        return object;
    }
}

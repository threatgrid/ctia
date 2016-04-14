package ctia.hook;

import ctia.Hook;
import java.util.Map;

public class DummyJar implements Hook {
    private String name;
    public DummyJar(String name){ this.name = name; }
    public void init() { this.name += " - initialized"; }
    public void destroy() { this.name += " - destroyed"; }
    public Map<String,Object> handle(String resourceName
            , Map<String,Object> object
            , Map<String,Object> prevObject)
    {
        object.put(this.name, "passed-from-jar");
        return object;
    }
}

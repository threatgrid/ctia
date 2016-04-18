package ctia.hook;

import ctia.Hook;
import java.util.Map;

public class AutoLoadedJar2 implements Hook {
    private String name;
    public AutoLoadedJar2(){ this.name = "autoloaded2"; }
    public void init() { this.name += " - initialized"; }
    public void destroy() { this.name += " - destroyed"; }
    public Map<String,Object> handle(String resourceName
            , Map<String,Object> object
            , Map<String,Object> prevObject)
    {
        object.put(this.name, "passed-from-autoloaded-jar2");
        return object;
    }
}

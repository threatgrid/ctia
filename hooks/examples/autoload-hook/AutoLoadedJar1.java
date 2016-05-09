package ctia.hook;

import ctia.Hook;
import java.util.Map;

public class AutoLoadedJar1 implements Hook {
    private String name;
    public AutoLoadedJar1() { this.name = "autoloaded1"; }
    public void init() { this.name += " - initialized"; }
    public void destroy() { this.name += " - destroyed"; }
    public Map<String,Object> handle(Map<String,Object> object,
                                     Map<String,Object> prevObject)
    {
        object.put(this.name, "passed-from-autoloaded-jar1");
        return object;
    }
}

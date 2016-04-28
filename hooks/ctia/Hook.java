package ctia;

import java.util.Map;

public interface Hook {
    void init();
    void destroy();
    Map<String,Object> handle(String resourceName,
                              Map<String,Object> object,
                              Map<String,Object> prevObject);
}

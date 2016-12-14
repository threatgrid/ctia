package client;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.sync.RedisCommands;
import org.json.JSONObject;

public class Example {

    public static int countCoaEvents(String server,
                                     int port,
                                     String key)
    {
        RedisURI uri = RedisURI.Builder.redis(server, port).build();
        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> cmds = connection.sync();

        int count = 0;
        for (String value = cmds.lpop(key); value != null; value = cmds.lpop(key)) {
            JSONObject event = new JSONObject(value);
            String type = event.getJSONObject("data")
                .getJSONObject("entity")
                .getString("type");
            if (type.equals("coa")) count++;
        }

        connection.close();
        client.shutdown();

        return count;
    }
}

package client;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.sync.RedisCommands;

public class Example {

    public static String getAnEvent(String server,
                                    int port,
                                    String key)
    {
        RedisURI uri = RedisURI.Builder.redis(server, port).build();
        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> cmds = connection.sync();

        String value = cmds.lpop(key);

        connection.close();
        client.shutdown();

        return value;
    }
}

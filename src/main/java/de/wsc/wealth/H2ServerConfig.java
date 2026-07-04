package de.wsc.wealth;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
@ConditionalOnProperty(name = "wealth.h2.server.enabled", havingValue = "true")
public class H2ServerConfig {

    @Value("${wealth.h2.server.port:9093}")
    private String port;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2TcpServer() throws SQLException {
        return Server.createTcpServer("-tcpPort", port, "-tcpAllowOthers");
    }
}

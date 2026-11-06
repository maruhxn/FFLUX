package com.maruhxn.fflux.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer = MYSQL

    companion object {
        /** 모든 테스트 컨텍스트가 컨테이너 하나를 재사용한다. */
        private val MYSQL = MySQLContainer("mysql:8.4")
            .withCommand("--default-time-zone=+09:00")
    }
}
